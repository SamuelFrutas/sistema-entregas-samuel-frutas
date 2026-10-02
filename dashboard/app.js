const KEY="sf_entregador_config";
let db=null;
let auth=null;
let currentUser=null;
let entregasUnsub=null;

function money(c){return (Number(c||0)/100).toLocaleString("pt-BR",{style:"currency",currency:"BRL"});}
function parseMoney(v){const s=String(v||"").trim().replace(/R\$\s?/g,"").replace(/\./g,"").replace(",", ".");const n=Number(s);return Number.isFinite(n)&&n>=0?Math.round(n*100):null;}

function initFirebase(){
  try{
    if(!window.FIREBASE_CONFIG?.projectId) throw new Error("Configuração Firebase ausente.");
    firebase.initializeApp(window.FIREBASE_CONFIG);
    auth=firebase.auth();
    db=firebase.firestore();
    auth.onAuthStateChanged(user=>{
      currentUser=user||null;
      if(user){hideLogin();loadHome();}
      else showLogin();
    });
  }catch(e){showFatal("Falha ao iniciar o Firebase: "+e.message);}
}

function showFatal(msg){
  document.body.innerHTML='<div class="fatal"><h2>Firebase</h2><p>'+msg+'</p></div>';
}

function showLogin(){
  let el=document.getElementById("loginOverlay");
  if(el) return;
  el=document.createElement("div");
  el.id="loginOverlay";
  el.className="login-overlay";
  el.innerHTML='<div class="login-card"><div class="logo">SF</div><small>PAINEL DO RESPONSÁVEL</small><h1>Entrar no sistema</h1><p>Use o e-mail e a senha cadastrados no Firebase Authentication.</p><label>E-mail<input id="loginEmail" type="email" autocomplete="username" placeholder="seu@email.com"></label><label>Senha<input id="loginPassword" type="password" autocomplete="current-password" placeholder="Senha"></label><button class="primary" id="loginButton">ENTRAR</button><div id="loginMsg" class="save-msg"></div></div>';
  document.body.appendChild(el);
  document.getElementById("loginButton").onclick=login;
  ["loginEmail","loginPassword"].forEach(id=>document.getElementById(id).addEventListener("keydown",e=>{if(e.key==="Enter")login();}));
}

function hideLogin(){document.getElementById("loginOverlay")?.remove();}

async function login(){
  const email=document.getElementById("loginEmail")?.value.trim();
  const password=document.getElementById("loginPassword")?.value||"";
  const msg=document.getElementById("loginMsg");
  if(!email||!password){msg.textContent="Informe e-mail e senha.";msg.className="save-msg error";return;}
  try{
    await auth.signInWithEmailAndPassword(email,password);
  }catch(e){
    msg.textContent="Não foi possível entrar. Confira os dados.";
    msg.className="save-msg error";
  }
}

async function logout(){try{await auth.signOut();}catch(e){}}

function loadConfig(){
  return {valorEntregaCentavos:0};
}

async function getConfig(){
  try{
    const snap=await db.collection("configuracoes").doc("entregador").get();
    return snap.exists?Object.assign({valorEntregaCentavos:0},snap.data()):loadConfig();
  }catch(e){return loadConfig();}
}

async function saveConfig(){
  const cents=parseMoney(document.querySelector("#valorEntrega").value);
  const msg=document.querySelector("#saveMsg");
  if(cents===null){msg.textContent="Informe um valor válido.";msg.className="save-msg error";return;}
  try{
    await db.collection("configuracoes").doc("entregador").set({
      valorEntregaCentavos:cents,
      atualizadoEm:firebase.firestore.FieldValue.serverTimestamp()
    },{merge:true});
    document.querySelector("#currentValue").textContent=money(cents);
    msg.textContent="Configuração salva no Firebase.";
    msg.className="save-msg ok";
  }catch(e){
    msg.textContent="Não foi possível salvar. As regras do Firestore ainda precisam permitir o acesso.";
    msg.className="save-msg error";
  }
}

async function showSettings(){
  const c=await getConfig();
  document.querySelector("#pageTitle").textContent="Configurações";
  document.querySelector("#content").innerHTML='<div class="page-head"><div><small>CONFIGURAÇÕES</small><h2>Entregador</h2><p>Defina quanto o entregador recebe por cada entrega realizada.</p></div></div><div class="settings-grid"><article class="panel settings-card"><div class="setting-icon">R$</div><div><small>VALOR POR ENTREGA</small><h3>Pagamento do entregador</h3><p>Este valor será usado no cálculo do total a pagar no resumo financeiro. Não altera o valor da compra do cliente.</p></div><label class="field"><span>Valor por entrega</span><input id="valorEntrega" inputmode="decimal" placeholder="0,00" value="'+(c.valorEntregaCentavos?(c.valorEntregaCentavos/100).toLocaleString("pt-BR",{minimumFractionDigits:2}):"")+'"></label><button class="primary" id="saveConfig">SALVAR CONFIGURAÇÃO</button><div id="saveMsg" class="save-msg"></div></article><article class="panel info-card"><small>COMO FUNCIONA</small><h3>Regra do sistema</h3><div class="rule"><span>1</span><p>O entregador registra a entrega no APK.</p></div><div class="rule"><span>2</span><p>O Dashboard contabiliza as entregas realizadas.</p></div><div class="rule"><span>3</span><p>O valor configurado aqui é multiplicado pela quantidade de entregas realizadas.</p></div><div class="preview"><small>VALOR ATUAL</small><strong id="currentValue">'+money(c.valorEntregaCentavos)+'</strong></div></article></div>';
  document.querySelector("#saveConfig").onclick=saveConfig;
}

function todayKey(){
  const d=new Date();
  const y=d.getFullYear();
  const m=String(d.getMonth()+1).padStart(2,"0");
  const day=String(d.getDate()).padStart(2,"0");
  return `${y}-${m}-${day}`;
}

async function loadHome(){
  if(!document.querySelector("#content")) return;
  document.querySelector("#pageTitle").textContent="Visão geral";
  try{
    if(entregasUnsub) entregasUnsub();
    entregasUnsub=db.collection("entregas").onSnapshot(async snap=>{
      const docs=snap.docs.map(d=>Object.assign({id:d.id},d.data()));
      const today=docs.filter(e=>e.dia===todayKey());
      const pending=today.filter(e=>!e.realizada);
      const done=today.filter(e=>!!e.realizada);
      const tips=done.reduce((s,e)=>s+Number(e.caixinhaCentavos||0),0);
      const c=await getConfig();
      document.querySelector(".stats article:nth-child(1) strong").textContent=today.length;
      document.querySelector(".stats article:nth-child(2) strong").textContent=pending.length;
      document.querySelector(".stats article:nth-child(3) strong").textContent=done.length;
      document.querySelector(".stats article:nth-child(4) strong").textContent=money(tips);
      const rows=document.querySelector(".rows");
      if(rows) rows.innerHTML='<div>Valor por entrega <b>'+money(c.valorEntregaCentavos)+'</b></div><div>Entregas realizadas <b>'+done.length+'</b></div><div>Total a pagar <b>'+money(Number(c.valorEntregaCentavos||0)*done.length)+'</b></div>';
    }, e=>{
      const status=document.querySelector(".status");
      if(status) status.innerHTML='● Firebase<br><small>Erro de leitura</small>';
    });
  }catch(e){
    const status=document.querySelector(".status");
    if(status) status.innerHTML='● Firebase<br><small>Conectado, aguardando regras</small>';
  }
}

function showHome(){loadHome();}
document.querySelectorAll("nav button").forEach((b,i)=>{
  if(i===0)b.onclick=showHome;
  if(i===4)b.onclick=showSettings;
});
initFirebase();
