const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

initializeApp();

const googleClientSecret = defineSecret("GOOGLE_OAUTH_CLIENT_SECRET");

const CLIENT_ID = "475005081261-al41vuvobjr2d3foo16oai9cpmjmora2.apps.googleusercontent.com";
const FRONTEND_ORIGIN = "https://samuelfrutas.github.io";
const REDIRECT_ORIGIN = FRONTEND_ORIGIN;
const GOOGLE_SCOPE = "https://www.googleapis.com/auth/contacts.readonly";

function sendJson(res, status, body) {
  res.status(status).set("Cache-Control", "no-store").json(body);
}

function checkOrigin(req, res) {
  const origin = req.get("Origin") || "";
  if (origin !== FRONTEND_ORIGIN) {
    sendJson(res, 403, { error: "ORIGIN_NOT_ALLOWED" });
    return false;
  }
  return true;
}

async function verifyFirebaseUser(req) {
  const header = req.get("Authorization") || "";
  if (!header.startsWith("Bearer ")) throw new Error("AUTH_REQUIRED");
  const idToken = header.slice(7).trim();
  if (!idToken) throw new Error("AUTH_REQUIRED");
  return getAuth().verifyIdToken(idToken);
}

async function exchangeCode(code) {
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      code,
      client_id: CLIENT_ID,
      client_secret: googleClientSecret.value(),
      redirect_uri: REDIRECT_ORIGIN,
      grant_type: "authorization_code"
    })
  });
  const data = await response.json();
  if (!response.ok) {
    const err = new Error(data.error_description || data.error || "GOOGLE_TOKEN_ERROR");
    err.status = response.status;
    throw err;
  }
  return data;
}

async function refreshAccessToken(refreshToken) {
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: CLIENT_ID,
      client_secret: googleClientSecret.value(),
      refresh_token: refreshToken,
      grant_type: "refresh_token"
    })
  });
  const data = await response.json();
  if (!response.ok) {
    const err = new Error(data.error_description || data.error || "GOOGLE_REFRESH_ERROR");
    err.status = response.status;
    throw err;
  }
  return data;
}

async function listContacts(accessToken) {
  const people = [];
  let pageToken = "";
  do {
    const params = new URLSearchParams({
      personFields: "names,phoneNumbers",
      pageSize: "1000"
    });
    if (pageToken) params.set("pageToken", pageToken);

    const response = await fetch(
      "https://people.googleapis.com/v1/people/me/connections?" + params.toString(),
      { headers: { Authorization: "Bearer " + accessToken } }
    );
    const data = await response.json();

    if (!response.ok) {
      const err = new Error(data?.error?.message || "PEOPLE_API_ERROR");
      err.status = response.status;
      throw err;
    }

    people.push(...(data.connections || []));
    pageToken = data.nextPageToken || "";
  } while (pageToken);

  return people;
}

exports.googleContacts = onRequest(
  {
    region: "us-central1",
    maxInstances: 1,
    secrets: [googleClientSecret],
    invoker: "public"
  },
  async (req, res) => {
    res.set("Access-Control-Allow-Origin", FRONTEND_ORIGIN);
    res.set("Vary", "Origin");
    res.set("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Requested-With");
    res.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    res.set("Cache-Control", "no-store");

    if (req.method === "OPTIONS") {
      res.status(204).send("");
      return;
    }

    if (!checkOrigin(req, res)) return;

    try {
      const user = await verifyFirebaseUser(req);
      const db = getFirestore();
      const connectionRef = db.collection("googleConnections").doc(user.uid);
      const path = String(req.path || "");

      if (req.method === "POST" && path.endsWith("/authorize")) {
        if (req.get("X-Requested-With") !== "XmlHttpRequest") {
          sendJson(res, 403, { error: "CSRF_HEADER_REQUIRED" });
          return;
        }

        const code = String(req.body?.code || "").trim();
        if (!code) {
          sendJson(res, 400, { error: "CODE_REQUIRED" });
          return;
        }

        const tokens = await exchangeCode(code);
        if (!tokens.refresh_token) {
          sendJson(res, 409, {
            error: "NO_REFRESH_TOKEN",
            message: "O Google não devolveu um refresh token. É necessária uma nova autorização."
          });
          return;
        }

        await connectionRef.set({
          refreshToken: tokens.refresh_token,
          scope: tokens.scope || GOOGLE_SCOPE,
          connectedAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp()
        }, { merge: true });

        sendJson(res, 200, { ok: true });
        return;
      }

      if (req.method === "GET" && path.endsWith("/contacts")) {
        const stored = await connectionRef.get();
        if (!stored.exists || !stored.data()?.refreshToken) {
          sendJson(res, 404, { error: "GOOGLE_NOT_CONNECTED" });
          return;
        }

        const refreshToken = stored.data().refreshToken;
        let tokens;
        try {
          tokens = await refreshAccessToken(refreshToken);
        } catch (err) {
          if (err.status === 400 || err.message === "invalid_grant") {
            await connectionRef.delete();
            sendJson(res, 401, { error: "GOOGLE_RECONNECT_REQUIRED" });
            return;
          }
          throw err;
        }

        const people = await listContacts(tokens.access_token);
        const safeContacts = people.map(person => ({
          resourceName: person.resourceName || "",
          names: (person.names || []).map(n => ({ displayName: n.displayName || "" })),
          phoneNumbers: (person.phoneNumbers || []).map(p => ({ value: p.value || "" }))
        })).filter(person => person.names.length > 0 || person.phoneNumbers.length > 0);

        const contactsRef = connectionRef.collection("contacts");
        const existing = await contactsRef.get();
        const incomingIds = new Set();
        const batch = db.batch();

        for (const person of safeContacts) {
          const key = person.resourceName || Buffer.from(
            (person.names[0]?.displayName || "") + "|" + (person.phoneNumbers[0]?.value || "")
          ).toString("base64url").slice(0, 80);
          incomingIds.add(key);
          batch.set(contactsRef.doc(key), {
            resourceName: person.resourceName || "",
            names: person.names,
            phoneNumbers: person.phoneNumbers,
            updatedAt: FieldValue.serverTimestamp()
          }, { merge: true });
        }

        for (const doc of existing.docs) {
          if (!incomingIds.has(doc.id)) batch.delete(doc.ref);
        }

        batch.set(connectionRef, {
          contactCount: safeContacts.length,
          contactsUpdatedAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp()
        }, { merge: true });

        await batch.commit();

        sendJson(res, 200, { contacts: safeContacts, count: safeContacts.length });
        return;
      }

      sendJson(res, 404, { error: "NOT_FOUND" });
    } catch (err) {
      console.error("googleContacts error", err);
      if (err.message === "AUTH_REQUIRED" || err.code === "auth/id-token-expired" || err.code === "auth/argument-error") {
        sendJson(res, 401, { error: "AUTH_REQUIRED" });
        return;
      }
      sendJson(res, 500, { error: "INTERNAL_ERROR" });
    }
  }
);
