let db=null;
let auth=null;
let currentUser=null;
let entregasUnsub=null;
let currentDocs=[];

let googleContactsCache=[];
let googleContactsToken=null;
let googleContactsTokenClient=null;
let googleContactsDbLoaded=false;
const GOOGLE_CONTACTS_CLIENT_ID = window.FIREBASE_CONFIG?.googleContactsClientId || "";
const GOOGLE_CONTACTS_SCOPE = "https://www.googleapis.com/auth/contacts.readonly";

function normalizeAddressKey(value){return String(value||"").toUpperCase().replace(/\s+/g,"").replace(/-/g,"/");}
function deliveryAddressKey(e){
  if(!e || e.semEndereco || !e.predio || !e.apartamento) return "";
  const predio=normalizeAddressKey(e.predio);
  const bloco=normalizeAddressKey(e.bloco||"");
  const apartamento=normalizeAddressKey(e.apartamento);
  return bloco ? predio+"/"+bloco+"/"+apartamento : predio+"/"+apartamento;
}
function contactPhone(person){
  return (person.phoneNumbers||[]).map(p=>p.value||"").find(Boolean)
    || String(person.telefone||person.phone||"").trim();
}
function normalizeWhatsapp(phone){
  let n=String(phone||"").replace(/\D/g,"");
  if(n.startsWith("00")) n=n.slice(2);
  if(n.length===10 || n.length===11) n="55"+n;
  return n;
}
function contactMatches(person,key){
  const name=person.names?.[0]?.displayName||"";
  return normalizeAddressKey(name).includes(normalizeAddressKey(key));
}
function googleContactName(person){
  return String(person.names?.[0]?.displayName||person.nome||person.name||"Contato sem nome").trim();
}
async function openWhatsappCharge(e,person){
  const phone=normalizeWhatsapp(contactPhone(person));
  if(!phone){alert("Este contato não possui telefone cadastrado.");return;}
  const config=await getConfig();
  const template=config.mensagemCobranca||"Total do investimento na sua saúde : {{valor}}";
  const message=template.replace(/\{\{\s*valor\s*\}\}/gi,money(e.valorCompraCentavos));
  window.open("https://wa.me/"+phone+"?text="+encodeURIComponent(message),"_blank","noopener,noreferrer");
}
function contactSearchText(person){
  const names=(person.names||[]).map(n=>n?.displayName||n?.value||"").join(" ");
  return [names,person.nome||"",person.name||"",contactPhone(person),person.telefone||"",person.phone||""].join(" ");
}
function contactNameMatches(person,term){
  const norm=v=>String(v||"").trim().toLocaleLowerCase("pt-BR").normalize("NFD").replace(/[\u0300-\u036f]/g,"").replace(/\s+/g," ");
  const q=norm(term);
  const name=norm(googleContactName(person));
  if(!q)return false;
  return name===q || name.startsWith(q+" ") || name.includes(" "+q) || name.includes(q);
}
async function openChargeContactPicker(e,initialTerm=""){
  const overlay=document.createElement("div");
  overlay.className="modal-overlay";
  overlay.innerHTML='<div class="modal-card charge-modal"><div class="modal-head"><div><small>COBRANÇA</small><h2>Selecionar cliente</h2><p>Escolha o contato desta entrega. Essa escolha será usada somente nesta cobrança.</p></div><button class="modal-close" id="closeChargeContact">×</button></div><input id="chargeContactSearch" class="charge-contact-search" type="search" autocomplete="off" autocorrect="off" autocapitalize="none" spellcheck="false" placeholder="Pesquisar nome ou telefone"><div class="contact-options" id="chargeContactOptions"></div></div>';
  document.body.appendChild(overlay);
  const search=overlay.querySelector("#chargeContactSearch");
  const options=overlay.querySelector("#chargeContactOptions");
  search.value=String(initialTerm||"");
  const normalizeSearch=v=>String(v??"").trim().toLocaleLowerCase("pt-BR").normalize("NFD").replace(/[\u0300-\u036f]/g,"").replace(/\s+/g," ");
  const render=()=>{
    const q=normalizeSearch(search.value);
    const list=googleContactsCache.filter(p=>!q||normalizeSearch(contactSearchText(p)).includes(q)).slice(0,80);
    options.innerHTML=list.length
      ?list.map((p,i)=>'<button class="contact-option" data-contact="'+i+'"><strong>'+esc(googleContactName(p))+'</strong><span>'+esc(contactPhone(p)||"Sem telefone")+'</span></button>').join("")
      :'<p style="padding:16px;color:#9aa6b2">Nenhum contato encontrado.</p>';
    options.querySelectorAll(".contact-option").forEach(btn=>btn.onclick=()=>{
      const person=list[Number(btn.dataset.contact)];
      overlay.remove();
      if(!contactPhone(person)){alert("Este contato não possui telefone cadastrado.");return;}
      openWhatsappCharge(e,person);
    });
  };
  search.addEventListener("input",render);
  search.addEventListener("keyup",render);
  search.addEventListener("change",render);
  overlay.querySelector("#closeChargeContact").onclick=()=>overlay.remove();
  try{
    const snap=await db.collection("contatosGoogle").get();
    googleContactsCache=snap.docs.map(d=>d.data());
    googleContactsDbLoaded=true;
  }catch(err){}
  render();
  setTimeout(()=>{search.focus();search.setSelectionRange(search.value.length,search.value.length);},50);
}
function googleContactsStatusText(){
  return googleContactsCache.length ? googleContactsCache.length+" contatos no banco" : "Contatos ainda não sincronizados";
}
function setGoogleContactsMessage(msg){const el=document.querySelector("#googleContactsMsg");if(el)el.textContent=msg;}
function googleContactDocId(person,index){
  const raw=String(person.resourceName||((person.names?.[0]?.displayName||"")+"|"+(person.phoneNumbers?.[0]?.value||""))||("contato-"+index));
  try{return "g_"+btoa(unescape(encodeURIComponent(raw))).replace(/\+/g,"-").replace(/\//g,"_").replace(/=+$/,"").slice(0,140);}catch(e){return "g_"+encodeURIComponent(raw).slice(0,140);}
}
async function loadGoogleContactsFromDb(){
  if(googleContactsDbLoaded)return googleContactsCache;
  try{const snap=await db.collection("contatosGoogle").get();googleContactsCache=snap.docs.map(d=>d.data());googleContactsDbLoaded=true;return googleContactsCache;}
  catch(err){googleContactsDbLoaded=true;setGoogleContactsMessage("Não foi possível carregar os contatos salvos.");return [];}
}
async function saveGoogleContactsToDb(people){
  const incomingIds=new Set();const existingSnap=await db.collection("contatosGoogle").get();const operations=[];
  people.forEach((person,index)=>{const id=googleContactDocId(person,index);incomingIds.add(id);operations.push({type:"set",id,data:{resourceName:person.resourceName||"",names:person.names||[],phoneNumbers:person.phoneNumbers||[],nome:googleContactName(person),telefone:contactPhone(person),atualizadoEm:firebase.firestore.FieldValue.serverTimestamp()}});});
  existingSnap.docs.forEach(doc=>{if(!incomingIds.has(doc.id))operations.push({type:"delete",ref:doc.ref});});
  for(let i=0;i<operations.length;i+=400){const batch=db.batch();operations.slice(i,i+400).forEach(op=>{if(op.type==="delete")batch.delete(op.ref);else batch.set(db.collection("contatosGoogle").doc(op.id),op.data,{merge:true});});await batch.commit();}
  googleContactsCache=people;googleContactsDbLoaded=true;
}
async function fetchGoogleContactsFromGoogle(token){
  const people=[];let pageToken="";
  do{const params=new URLSearchParams({personFields:"names,phoneNumbers,metadata",pageSize:"500"});if(pageToken)params.set("pageToken",pageToken);const res=await fetch("https://people.googleapis.com/v1/people/me/connections?"+params.toString(),{headers:{Authorization:"Bearer "+token}});if(res.status===401)throw new Error("AUTH_EXPIRED");if(!res.ok)throw new Error("Google Contacts: HTTP "+res.status);const data=await res.json();people.push(...(data.connections||[]));pageToken=data.nextPageToken||"";}while(pageToken);
  return people;
}
function initGoogleContactsClient(){
  if(!GOOGLE_CONTACTS_CLIENT_ID||!window.google?.accounts?.oauth2)return false;
  if(googleContactsTokenClient)return true;
  googleContactsTokenClient=google.accounts.oauth2.initTokenClient({client_id:GOOGLE_CONTACTS_CLIENT_ID,scope:GOOGLE_CONTACTS_SCOPE,callback:async response=>{
    if(response.error){setGoogleContactsMessage("Autorização cancelada.");return;}
    try{setGoogleContactsMessage("Baixando contatos do Google...");const people=await fetchGoogleContactsFromGoogle(response.access_token);setGoogleContactsMessage("Salvando "+people.length+" contatos no banco...");await saveGoogleContactsToDb(people);googleContactsToken=response.access_token;showCharges();setGoogleContactsMessage(people.length+" contatos atualizados no banco.");}
    catch(err){setGoogleContactsMessage(err.message==="AUTH_EXPIRED"?"A autorização expirou. Tente atualizar novamente.":"Não foi possível carregar os contatos do Google.");}
  }});
  return true;
}
function updateGoogleContacts(){
  if(!initGoogleContactsClient()){if(!GOOGLE_CONTACTS_CLIENT_ID)alert("Falta cadastrar o OAuth Client ID do Google Cloud.");else alert("O componente do Google ainda está carregando. Tente novamente.");return;}
  const prompt=googleContactsCache.length?"":"consent";googleContactsTokenClient.requestAccessToken({prompt});
}
function findContactsForDelivery(e){const key=deliveryAddressKey(e);if(!key)return [];return googleContactsCache.filter(p=>contactMatches(p,key)&&contactPhone(p));}
function findContactsForName(e){const nome=String(e.enderecoReferencia||"").trim();if(!nome)return [];return googleContactsCache.filter(p=>contactPhone(p)&&contactNameMatches(p,nome));}
function openChargeMatchesPicker(e,matches,titleText){
  const overlay=document.createElement("div");overlay.className="modal-overlay";
  overlay.innerHTML='<div class="modal-card charge-modal"><div class="modal-head"><div><small>COBRANÇA</small><h2>Escolha o cliente</h2><p>'+esc(titleText)+'</p></div><button class="modal-close" id="closeCharge">×</button></div><div class="contact-options">'+matches.map((p,i)=>'<button class="contact-option" data-contact="'+i+'"><strong>'+esc(googleContactName(p))+'</strong><span>'+esc(contactPhone(p))+'</span></button>').join("")+'</div></div>';
  document.body.appendChild(overlay);overlay.querySelector("#closeCharge").onclick=()=>overlay.remove();
  overlay.querySelectorAll(".contact-option").forEach(btn=>btn.onclick=()=>{const person=matches[Number(btn.dataset.contact)];overlay.remove();openWhatsappCharge(e,person);});
}
async function startCharge(id){
  const e=currentDocs.find(x=>x.id===id);if(!e)return;
  if(!googleContactsDbLoaded)await loadGoogleContactsFromDb();

  if(e.semEndereco){
    const nome=String(e.enderecoReferencia||"").trim();
    if(nome){
      const matches=findContactsForName(e);
      if(matches.length===1){openWhatsappCharge(e,matches[0]);return;}
      if(matches.length>1){openChargeMatchesPicker(e,matches,"Encontramos "+matches.length+" contatos para "+nome+".");return;}
    }
    openChargeContactPicker(e,nome);
    return;
  }

  const key=deliveryAddressKey(e);
  const matches=findContactsForDelivery(e);
  if(matches.length===1){openWhatsappCharge(e,matches[0]);return;}
  if(matches.length>1){openChargeMatchesPicker(e,matches,"Encontramos "+matches.length+" contatos para "+key+".");return;}

  // Se não encontrou pelo endereço, a pesquisa manual começa com a chave exata da entrega.
  openChargeContactPicker(e,key);
}
async function markCharged(id){
  const e=currentDocs.find(x=>x.id===id);if(!e)return;
  if(!confirm("Confirmar que este cliente já foi cobrado?"))return;
  try{await db.collection("entregas").doc(e.id).update({cobrancaFeita:true,cobradoEm:firebase.firestore.FieldValue.serverTimestamp()});}
  catch(err){alert("Não foi possível registrar a cobrança.");}
}
function chargeAction(e){
  if(!e.realizada||e.resultadoPagamento==="PAGO"||e.cobrancaFeita||(e.pagamentoInicial!=="NAO_PAGO"&&e.resultadoPagamento!=="NAO_PAGO"))return "";
  return '<div class="charge-actions"><button class="row-action charge-btn" onclick="startCharge(\''+esc(e.id)+'\')">COBRAR NO WHATSAPP</button><button class="row-action charged-btn" onclick="markCharged(\''+esc(e.id)+'\')">JÁ COBREI</button></div>';
}
function renderChargeRow(e){const key=deliveryAddressKey(e);return '<div class="delivery-row charge-row"><div><strong>'+esc(formatEndereco(e))+'</strong><small>'+esc((e.dia||"—")+" • Não pago"+(key?" • "+key:""))+'</small></div><div class="row-end"><b>'+money(e.valorCompraCentavos)+'</b>'+chargeAction(e)+'</div></div>';}
function showCharges(){
  setActive("charges");setTitle("Cobranças");
  const unpaid=currentDocs.filter(e=>!!e.realizada&&!e.cobrancaFeita&&e.resultadoPagamento!=="PAGO"&&(e.pagamentoInicial==="NAO_PAGO"||e.resultadoPagamento==="NAO_PAGO"));
  document.querySelector("#content").innerHTML='<div class="page-head page-head-actions"><div><small>CLIENTES COM PAGAMENTO PENDENTE</small><h2>Cobrar pelo WhatsApp</h2><p>O sistema tenta localizar pelo endereço. Se a entrega foi cadastrada sem endereço, ele usa o nome informado na referência e, se necessário, abre a busca.</p></div><button class="primary add-btn" id="googleContactsButton">'+(googleContactsCache.length?"ATUALIZAR CONTATOS":"CONECTAR GOOGLE CONTATOS")+'</button></div><div class="panel charge-connection"><div><small>GOOGLE CONTACTS</small><h3>'+esc(googleContactsStatusText())+'</h3><p id="googleContactsMsg">Os contatos ficam armazenados no banco. Quando cadastrar novos clientes no Google, clique em “ATUALIZAR CONTATOS”.</p></div></div><article class="panel"><div class="panel-head"><div><small>PAGAMENTOS</small><h3>'+unpaid.length+' pendente'+(unpaid.length===1?"":"s")+'</h3></div></div><div class="delivery-list">'+(unpaid.length?unpaid.map(renderChargeRow).join(""):renderEmpty("Nenhuma cobrança pendente."))+'</div></article>';
  document.getElementById("googleContactsButton").onclick=updateGoogleContacts;
  if(!googleContactsDbLoaded)loadGoogleContactsFromDb().then(()=>showCharges());
}

function money(c){return (Number(c||0)/100).toLocaleString("pt-BR",{style:"currency",currency:"BRL"});}
function parseMoney(v){const s=String(v||"").trim().replace(/R\$\s?/g,"").replace(/\./g,"").replace(",", ".");const n=Number(s);return Number.isFinite(n)&&n>=0?Math.round(n*100):null;}
function todayKey(){const d=new Date();return d.getFullYear()+"-"+String(d.getMonth()+1).padStart(2,"0")+"-"+String(d.getDate()).padStart(2,"0");}
function formatEndereco(e){if(e.semEndereco)return e.enderecoReferencia?"Sem endereço • "+e.enderecoReferencia:"Sem endereço";const parts=[e.predio&&"Prédio "+e.predio,e.bloco&&"Bloco "+e.bloco,e.apartamento&&"Apto "+e.apartamento].filter(Boolean);return parts.join(" • ")||(e.enderecoReferencia||"Endereço não informado");}
function paymentLabel(e){return e.pagamentoInicial==="PAGO_ADIANTADO"?"Pago adiantado":e.pagamentoInicial==="NAO_PAGO"?"Não pago":e.pagamentoInicial==="NAO_INFORMADO"?"Não informado":"—";}

function initFirebase(){
  try{if(!window.FIREBASE_CONFIG?.projectId)throw new Error("Configuração Firebase ausente.");firebase.initializeApp(window.FIREBASE_CONFIG);auth=firebase.auth();db=firebase.firestore();auth.onAuthStateChanged(user=>{currentUser=user||null;if(user){hideLogin();startListener();showHome();}else{stopListener();showLogin();}});}catch(e){showFatal("Falha ao iniciar o Firebase: "+e.message);}
}
function showFatal(msg){document.body.innerHTML='<div class="fatal"><h2>Firebase</h2><p>'+msg+'</p></div>';}
function showLogin(){let el=document.getElementById("loginOverlay");if(el)return;el=document.createElement("div");el.id="loginOverlay";el.className="login-overlay";el.innerHTML='<div class="login-card"><div class="logo">SF</div><small>PAINEL DO RESPONSÁVEL</small><h1>Entrar no sistema</h1><p>Use o e-mail e a senha cadastrados no Firebase Authentication.</p><label>E-mail<input id="loginEmail" type="email" autocomplete="username" placeholder="seu@email.com"></label><label>Senha<input id="loginPassword" type="password" autocomplete="current-password" placeholder="sua senha"></label><button class="primary" id="loginButton">ENTRAR</button><div id="loginMsg" class="save-msg"></div></div></div>';document.body.appendChild(el);document.getElementById("loginButton").onclick=login;["loginEmail","loginPassword"].forEach(id=>document.getElementById(id).addEventListener("keydown",e=>{if(e.key==="Enter")login();}));}
function hideLogin(){document.getElementById("loginOverlay")?.remove();}
async function login(){const email=document.getElementById("loginEmail")?.value.trim(),password=document.getElementById("loginPassword")?.value||"",msg=document.getElementById("loginMsg");if(!email||!password){msg.textContent="Informe e-mail e senha.";msg.className="save-msg error";return;}try{await auth.signInWithEmailAndPassword(email,password);}catch(e){msg.textContent="Não foi possível entrar. Confira os dados.";msg.className="save-msg error";}}
async function logout(){try{await auth.signOut();}catch(e){}}
function stopListener(){if(entregasUnsub){entregasUnsub();entregasUnsub=null;}currentDocs=[];}
function startListener(){if(entregasUnsub)return;entregasUnsub=db.collection("entregas").onSnapshot(snap=>{currentDocs=snap.docs.map(d=>Object.assign({id:d.id},d.data()));const status=document.querySelector(".status");if(status)status.innerHTML='● Firebase<br><small>Sincronizado em tempo real</small>';renderCurrentPage();backfillDeliveryPayout();},e=>{const status=document.querySelector(".status");if(status)status.innerHTML='● Firebase<br><small>Erro: '+(e.code||"leitura")+'</small>';currentDocs=[];renderCurrentPage();});}
async function backfillDeliveryPayout(){try{const config=await getConfig();const cents=Number(config.valorEntregaCentavos||0);if(cents<=0)return;const missing=currentDocs.filter(e=>e.valorEntregaCentavos==null);for(let i=0;i<missing.length;i+=450){const batch=db.batch();missing.slice(i,i+450).forEach(e=>{batch.set(db.collection("entregas").doc(String(e.id)),{valorEntregaCentavos:cents},{merge:true});});await batch.commit();}}catch(e){}}
function setActive(page){document.querySelectorAll("[data-page]").forEach(b=>b.classList.toggle("active",b.dataset.page===page));}
function setTitle(title){document.querySelector("#pageTitle").textContent=title;}
function renderEmpty(msg="Nenhuma entrega encontrada."){return '<div class="empty"><b>▤</b><strong>'+msg+'</strong><p>Os registros aparecem aqui automaticamente quando o APK sincroniza com o Firebase.</p></div>';}
function renderRecentes(docs){const box=document.querySelector("#recentes");if(!box)return;if(!docs.length){box.innerHTML=renderEmpty("Nenhuma entrega sincronizada");return;}const sorted=[...docs].sort((a,b)=>String(b.dia||"").localeCompare(String(a.dia||""))).slice(0,20);box.innerHTML=sorted.map(e=>'<div class="delivery-row"><div><strong>'+esc(formatEndereco(e))+'</strong><small>'+esc(String(e.dia||"—")+" • "+(e.realizada?"REALIZADA":"PENDENTE"))+'</small></div><div class="row-end"><b>'+(e.valorCompraCentavos==null?"Valor não informado":money(e.valorCompraCentavos))+'</b>'+deliveryActions(e)+'</div></div>').join("");}
function renderHomeData(){const today=currentDocs.filter(e=>e.dia===todayKey()),pending=today.filter(e=>!e.realizada),done=today.filter(e=>!!e.realizada),tips=done.reduce((s,e)=>s+Number(e.caixinhaCentavos||0),0);const set=(sel,val)=>{const el=document.querySelector(sel);if(el)el.textContent=val;};set(".stats article:nth-child(1) strong",today.length);set(".stats article:nth-child(2) strong",pending.length);set(".stats article:nth-child(3) strong",done.length);set("#todayHint",today.length===1?"1 entrega registrada":today.length+" entregas registradas");getConfig().then(c=>{const ganhoEntregas=Number(c.valorEntregaCentavos||0)*done.length;const totalPagar=ganhoEntregas+tips;const stats=document.querySelector(".stats");if(stats){stats.innerHTML='<article><small>ENTREGAS HOJE</small><strong>'+today.length+'</strong><span>'+(today.length===1?"1 entrega registrada":"Entregas registradas")+'</span></article><article><small>PENDENTES</small><strong>'+pending.length+'</strong><span>Aguardando conclusão</span></article><article><small>REALIZADAS</small><strong>'+done.length+'</strong><span>Concluídas hoje</span></article>';}const rows=document.querySelector(".rows");if(rows)rows.innerHTML='<div>Entregas realizadas <b>'+done.length+'</b></div><div>Ganhos entregas <b>'+money(ganhoEntregas)+'</b></div><div>Caixinhas <b>'+money(tips)+'</b></div><div>Total a pagar <b>'+money(totalPagar)+'</b></div>';});renderRecentes(today);}
function renderCurrentPage(){const active=document.querySelector("[data-page].active")?.dataset.page||"home";if(active==="charges")showCharges();else if(active==="done")showDone();else if(active==="finance")showFinance();else if(active==="deliveries")showDeliveries();else if(active==="settings")showSettings();else showHome();}
function showHome(){setActive("home");setTitle("Visão geral");renderHomeData();}
