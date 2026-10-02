let db=null;
let auth=null;
let currentUser=null;
let entregasUnsub=null;
let currentDocs=[];

function money(c){return (Number(c||0)/100).toLocaleString("pt-BR",{style:"currency",currency:"BRL"});}
function parseMoney(v){const s=String(v||"").trim().replace(/R\$\s?/g,"").replace(/\./g,"").replace(",", ".");const n=Number(s);return Number.isFinite(n)&&n>=0?Math.round(n*100):null;}
function todayKey(){const d=new Date();return d.getFullYear()+"-"+String(d.getMonth()+1).padStart(2,"0")+"-"+String(d.getDate()).padStart(2,"0");}
function formatEndereco(e){
  if(e.semEndereco) return e.enderecoReferencia?"Sem endereço • "+e.enderecoReferencia:"Sem endereço";
  const parts=[e.predio&&"Prédio "+e.predio,e.bloco&&"Bloco "+e.bloco,e.apartamento&&"Apto "+e.apartamento].filter(Boolean);
  return parts.join(" • ")||(e.enderecoReferencia||"Endereço não informado");
}
function paymentLabel(e){return e.pagamentoInicial==="PAGO_ADIANTADO"?"Pago adiantado":e.pagamentoInicial==="NAO_PAGO"?"Não pago":e.pagamentoInicial==="NAO_INFORMADO"?"Não informado":"—";}

function initFirebase(){
  try{
    if(!window.FIREBASE_CONFIG?.projectId) throw new Error("Configuração Firebase ausente.");
    firebase.initializeApp(window.FIREBASE_CONFIG);
    auth=firebase.auth(); db=firebase.firestore();
    auth.onAuthStateChanged(user=>{
      currentUser=user||null;
      if(user){hideLogin();startListener();showHome();}
      else {stopListener();showLogin();}
    });
  }catch(e){showFatal("Falha ao iniciar o Firebase: "+e.message);}
}
function showFatal(msg){document.body.innerHTML='<div class="fatal"><h2>Firebase</h2><p>'+msg+'</p></div>';}
function showLogin(){
  let el=document.getElementById("loginOverlay"); if(el)return;
  el=document.createElement("div");el.id="loginOverlay";el.className="login-overlay";
  el.innerHTML='<div class="login-card"><div class="logo">SF</div><small>PAINEL DO RESPONSÁVEL</small><h1>Entrar no sistema</h1><p>Use o e-mail e a senha cadastrados no Firebase Authentication.</p><label>E-mail<input id="loginEmail" type="email" autocomplete="username" placeholder="seu@email.com"></label><label>Senha<input id="loginPassword" type="password" autocomplete="current-password" placeholder="Senha"></label><button class="primary" id="loginButton">ENTRAR</button><div id="loginMsg" class="save-msg"></div></div>';
  document.body.appendChild(el);
  document.getElementById("loginButton").onclick=login;
  ["loginEmail","loginPassword"].forEach(id=>document.getElementById(id).addEventListener("keydown",e=>{if(e.key==="Enter")login();}));
}
function hideLogin(){document.getElementById("loginOverlay")?.remove();}
async function login(){
  const email=document.getElementById("loginEmail")?.value.trim(),password=document.getElementById("loginPassword")?.value||"",msg=document.getElementById("loginMsg");
  if(!email||!password){msg.textContent="Informe e-mail e senha.";msg.className="save-msg error";return;}
  try{await auth.signInWithEmailAndPassword(email,password);}
  catch(e){msg.textContent="Não foi possível entrar. Confira os dados.";msg.className="save-msg error";}
}
async function logout(){try{await auth.signOut();}catch(e){}}

function stopListener(){if(entregasUnsub){entregasUnsub();entregasUnsub=null;}currentDocs=[];}
function startListener(){
  if(entregasUnsub)return;
  entregasUnsub=db.collection("entregas").onSnapshot(snap=>{
    currentDocs=snap.docs.map(d=>Object.assign({id:d.id},d.data()));
    const status=document.querySelector(".status");
    if(status)status.innerHTML='● Firebase<br><small>Sincronizado em tempo real</small>';
    renderCurrentPage();
  },e=>{
    const status=document.querySelector(".status");
    if(status)status.innerHTML='● Firebase<br><small>Erro: '+(e.code||"leitura")+'</small>';
    currentDocs=[];
    renderCurrentPage();
  });
}
function setActive(page){document.querySelectorAll("[data-page]").forEach(b=>b.classList.toggle("active",b.dataset.page===page));}
function setTitle(title){document.querySelector("#pageTitle").textContent=title;}
function renderEmpty(msg="Nenhuma entrega encontrada."){return '<div class="empty"><b>▤</b><strong>'+msg+'</strong><p>Os registros aparecem aqui automaticamente quando o APK sincroniza com o Firebase.</p></div>';}

function renderRecentes(docs){
  const box=document.querySelector("#recentes");if(!box)return;
  if(!docs.length){box.innerHTML=renderEmpty("Nenhuma entrega sincronizada");return;}
  const sorted=[...docs].sort((a,b)=>String(b.dia||"").localeCompare(String(a.dia||""))).slice(0,20);
  box.innerHTML=sorted.map(e=>'<div class="delivery-row"><div><strong>'+esc(formatEndereco(e))+'</strong><small>'+esc(String(e.dia||"—")+" • "+(e.realizada?"REALIZADA":"PENDENTE"))+'</small></div><div class="row-end"><b>'+(e.valorCompraCentavos==null?"Valor não informado":money(e.valorCompraCentavos))+'</b>'+deliveryActions(e)+'</div></div>').join("");
}

function renderHomeData(){
  const today=currentDocs.filter(e=>e.dia===todayKey()),pending=today.filter(e=>!e.realizada),done=today.filter(e=>!!e.realizada),tips=done.reduce((s,e)=>s+Number(e.caixinhaCentavos||0),0);
  const set=(sel,val)=>{const el=document.querySelector(sel);if(el)el.textContent=val;};
  set(".stats article:nth-child(1) strong",today.length);set(".stats article:nth-child(2) strong",pending.length);set(".stats article:nth-child(3) strong",done.length);set(".stats article:nth-child(4) strong",money(tips));
  set("#todayHint",today.length===1?"1 entrega registrada":today.length+" entregas registradas");
  renderRecentes(today);
  getConfig().then(c=>{
    const rows=document.querySelector(".rows");
    if(rows)rows.innerHTML='<div>Valor por entrega <b>'+money(c.valorEntregaCentavos)+'</b></div><div>Entregas realizadas <b>'+done.length+'</b></div><div>Total a pagar <b>'+money(Number(c.valorEntregaCentavos||0)*done.length)+'</b></div>';
  });
}

function showHome(){
  setActive("home");setTitle("Visão geral");
  document.querySelector("#content").innerHTML='<div class="hero"><div><small>HOJE</small><h2>Controle das entregas</h2><p>Acompanhe em tempo real o que foi registrado pelo entregador no APK.</p></div><strong class="hero-icon">▣</strong></div><div class="stats"><article><small>ENTREGAS HOJE</small><strong>0</strong><span id="todayHint">Nenhuma registrada</span></article><article><small>PENDENTES</small><strong>0</strong><span>Aguardando conclusão</span></article><article><small>REALIZADAS</small><strong>0</strong><span>Concluídas hoje</span></article><article><small>CAIXINHA</small><strong>R$ 0,00</strong><span>Hoje</span></article></div><div class="columns"><article class="panel"><div class="panel-head"><div><small>ACOMPANHAMENTO</small><h3>Entregas recentes</h3></div><span class="live-dot">AO VIVO</span></div><div id="recentes" class="delivery-list"></div></article><article class="panel"><small>ENTREGADOR</small><h3>Resumo do dia</h3><div class="rows"><div>Valor por entrega <b>R$ 0,00</b></div><div>Entregas realizadas <b>0</b></div><div>Total a pagar <b>R$ 0,00</b></div></div></article></div>';
  renderHomeData();
}

function esc(v){return String(v??"").replace(/[&<>"']/g,m=>({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[m]));}
function openDelivery(id){
  const e=currentDocs.find(x=>x.id===id); if(!e)return;
  const overlay=document.createElement("div"); overlay.className="modal-overlay"; overlay.id="deliveryModal";
  overlay.innerHTML='<div class="modal-card"><div class="modal-head"><div><small>ENTREGA</small><h2>'+esc(formatEndereco(e))+'</h2></div><button class="modal-close" id="closeDelivery">×</button></div>'+
    '<div class="form-grid"><label>Prédio<input id="editPredio" value="'+esc(e.predio||"")+'"></label><label>Bloco<input id="editBloco" value="'+esc(e.bloco||"")+'"></label><label>Apartamento<input id="editApto" value="'+esc(e.apartamento||"")+'"></label><label>Valor da compra<input id="editValor" inputmode="decimal" value="'+(e.valorCompraCentavos==null?"":(Number(e.valorCompraCentavos)/100).toLocaleString("pt-BR",{minimumFractionDigits:2}))+'"></label></div>'+
    '<label class="check-line"><input id="editSemEndereco" type="checkbox" '+(e.semEndereco?"checked":"")+'> Entrega sem endereço</label>'+
    '<label class="full-field">Endereço / referência<textarea id="editReferencia">'+esc(e.enderecoReferencia||"")+'</textarea></label>'+
    '<label class="full-field">Pagamento inicial<select id="editPagamento"><option value="PAGO_ADIANTADO">Pago adiantado</option><option value="NAO_PAGO">Não pago</option><option value="NAO_INFORMADO">Não informado</option></select></label>'+
    '<div class="modal-actions"><button class="danger" id="deleteDelivery">EXCLUIR ENTREGA</button><button class="primary" id="saveDelivery">SALVAR ALTERAÇÕES</button></div><div id="editMsg" class="save-msg"></div></div>';
  document.body.appendChild(overlay);
  document.getElementById("editPagamento").value=e.pagamentoInicial||"NAO_INFORMADO";
  document.getElementById("closeDelivery").onclick=()=>overlay.remove();
  document.getElementById("saveDelivery").onclick=async()=>{
    const valor=parseMoney(document.getElementById("editValor").value);
    const updated={...e,predio:document.getElementById("editPredio").value.trim(),bloco:document.getElementById("editBloco").value.trim(),apartamento:document.getElementById("editApto").value.trim(),semEndereco:document.getElementById("editSemEndereco").checked,enderecoReferencia:document.getElementById("editReferencia").value.trim(),valorCompraCentavos:valor,pagamentoInicial:document.getElementById("editPagamento").value};
    const msg=document.getElementById("editMsg");
    try{await db.collection("entregas").doc(e.id).update(updated);msg.textContent="Alterações salvas.";msg.className="save-msg ok";setTimeout(()=>overlay.remove(),500);}
    catch(err){msg.textContent="Não foi possível salvar.";msg.className="save-msg error";}
  };
  document.getElementById("deleteDelivery").onclick=async()=>{
    if(!confirm("Excluir esta entrega? Essa ação não poderá ser desfeita."))return;
    try{await db.collection("entregas").doc(e.id).delete();overlay.remove();}
    catch(err){const msg=document.getElementById("editMsg");msg.textContent="Não foi possível excluir.";msg.className="save-msg error";}
  };
}
function openNewDelivery(){
  const overlay=document.createElement("div"); overlay.className="modal-overlay"; overlay.id="deliveryModal";
  overlay.innerHTML='<div class="modal-card"><div class="modal-head"><div><small>NOVA ENTREGA</small><h2>Registrar entrega</h2></div><button class="modal-close" id="closeDelivery">×</button></div>'+
    '<div class="form-grid"><label>Prédio<input id="newPredio"></label><label>Bloco<input id="newBloco"></label><label>Apartamento<input id="newApto"></label><label>Valor da compra<input id="newValor" inputmode="decimal" placeholder="0,00"></label></div>'+
    '<label class="check-line"><input id="newSemEndereco" type="checkbox"> Entrega sem endereço</label>'+
    '<label class="full-field">Endereço / referência<textarea id="newReferencia"></textarea></label>'+
    '<label class="full-field">Pagamento inicial<select id="newPagamento"><option value="PAGO_ADIANTADO">Pago adiantado</option><option value="NAO_PAGO">Não pago</option><option value="NAO_INFORMADO">Não informado</option></select></label>'+
    '<div class="modal-actions"><button class="modal-secondary" id="cancelNew">CANCELAR</button><button class="primary" id="createDelivery">ADICIONAR ENTREGA</button></div><div id="newMsg" class="save-msg"></div></div>';
  document.body.appendChild(overlay);
  document.getElementById("closeDelivery").onclick=()=>overlay.remove();
  document.getElementById("cancelNew").onclick=()=>overlay.remove();
  document.getElementById("createDelivery").onclick=async()=>{
    const valor=parseMoney(document.getElementById("newValor").value);
    const msg=document.getElementById("newMsg");
    const data={dia:todayKey(),predio:document.getElementById("newPredio").value.trim(),bloco:document.getElementById("newBloco").value.trim(),apartamento:document.getElementById("newApto").value.trim(),semEndereco:document.getElementById("newSemEndereco").checked,enderecoReferencia:document.getElementById("newReferencia").value.trim(),valorCompraCentavos:valor,pagamentoInicial:document.getElementById("newPagamento").value,resultadoPagamento:"",formaPagamento:"",caixinhaCentavos:0,observacao:"",realizada:false,sincronizacao:"SINCRONIZADA"};
    try{await db.collection("entregas").add({...data,atualizadoEm:firebase.firestore.FieldValue.serverTimestamp()});overlay.remove();}
    catch(err){msg.textContent="Não foi possível adicionar.";msg.className="save-msg error";}
  };
}
function deliveryActions(e){return '<button class="row-action" onclick="openDelivery(\''+esc(e.id)+'\')">EDITAR</button>';}
function renderList(type){
  const today=currentDocs.filter(e=>e.dia===todayKey());
  const docs=type==="pending"?today.filter(e=>!e.realizada):currentDocs.filter(e=>!!e.realizada).sort((a,b)=>String(b.dia||"").localeCompare(String(a.dia||"")));
  if(!docs.length)return renderEmpty(type==="pending"?"Nenhuma entrega pendente hoje.":"Nenhuma entrega realizada registrada.");
  return docs.map(e=>{
    const payment=e.realizada&&e.formaPagamento?e.formaPagamento:(e.pagamentoInicial?paymentLabel(e):"—");
    const meta=[e.dia||"—",e.realizada?"REALIZADA":"PENDENTE",payment].filter(Boolean).join(" • ");
    return '<div class="delivery-row"><div><strong>'+esc(formatEndereco(e))+'</strong><small>'+esc(meta)+'</small></div><div class="row-end"><b>'+ (e.valorCompraCentavos==null?"—":money(e.valorCompraCentavos))+'</b>'+deliveryActions(e)+'</div></div>';
  }).join("");
}

function showDeliveries(){
  setActive("deliveries");setTitle("Entregas");
  const pending=currentDocs.filter(e=>e.dia===todayKey()&&!e.realizada);
  document.querySelector("#content").innerHTML='<div class="page-head page-head-actions"><div><small>ACOMPANHAMENTO</small><h2>Entregas pendentes</h2><p>Entregas registradas pelo entregador que ainda não foram concluídas.</p></div><button class="primary add-btn" id="addDelivery">+ NOVA ENTREGA</button></div><article class="panel"><div class="panel-head"><div><small>HOJE</small><h3>'+pending.length+' pendente'+(pending.length===1?"":"s")+'</h3></div><span class="live-dot">AO VIVO</span></div><div class="delivery-list" id="pageList">'+renderList("pending")+'</div></article>';
  const addButton=document.querySelector("#addDelivery");
  if(addButton) addButton.onclick=openNewDelivery;
}

function showDone(){
  setActive("done");setTitle("Realizadas");
  const done=currentDocs.filter(e=>!!e.realizada);
  document.querySelector("#content").innerHTML='<div class="page-head"><div><small>HISTÓRICO</small><h2>Entregas realizadas</h2><p>Registro das entregas concluídas pelo entregador.</p></div></div><article class="panel"><div class="panel-head"><div><small>TOTAL</small><h3>'+done.length+' realizada'+(done.length===1?"":"s")+'</h3></div></div><div class="delivery-list" id="pageList">'+renderList("done")+'</div></article>';
}

async function showFinance(){
  setActive("finance");setTitle("Financeiro");
  const done=currentDocs.filter(e=>!!e.realizada),todayDone=done.filter(e=>e.dia===todayKey()),tips=todayDone.reduce((s,e)=>s+Number(e.caixinhaCentavos||0),0),purchase=todayDone.reduce((s,e)=>s+Number(e.valorCompraCentavos||0),0),c=await getConfig(),payout=Number(c.valorEntregaCentavos||0)*todayDone.length;
  document.querySelector("#content").innerHTML='<div class="page-head"><div><small>FINANCEIRO</small><h2>Resumo financeiro</h2><p>Separação entre o valor das compras dos clientes e o pagamento do entregador.</p></div></div><div class="stats"><article><small>COMPRAS HOJE</small><strong>'+money(purchase)+'</strong><span>Valor informado nas entregas</span></article><article><small>ENTREGAS REALIZADAS</small><strong>'+todayDone.length+'</strong><span>Hoje</span></article><article><small>A PAGAR AO ENTREGADOR</small><strong>'+money(payout)+'</strong><span>'+money(c.valorEntregaCentavos)+' por entrega</span></article><article><small>CAIXINHA</small><strong>'+money(tips)+'</strong><span>Hoje</span></article></div><div class="columns"><article class="panel"><small>PAGAMENTO DO ENTREGADOR</small><h3>Valor configurado</h3><div class="rows"><div>Valor por entrega <b>'+money(c.valorEntregaCentavos)+'</b></div><div>Entregas realizadas hoje <b>'+todayDone.length+'</b></div><div>Total a pagar hoje <b>'+money(payout)+'</b></div></div></article><article class="panel"><small>CLIENTES</small><h3>Valor das compras</h3><div class="rows"><div>Compras registradas hoje <b>'+money(purchase)+'</b></div><div>Caixinha registrada hoje <b>'+money(tips)+'</b></div></div></article></div>';
}

function loadConfig(){return {valorEntregaCentavos:0};}
async function getConfig(){
  try{const snap=await db.collection("configuracoes").doc("entregador").get();return snap.exists?Object.assign({valorEntregaCentavos:0},snap.data()):loadConfig();}
  catch(e){return loadConfig();}
}
async function saveConfig(){
  const cents=parseMoney(document.querySelector("#valorEntrega").value),msg=document.querySelector("#saveMsg");
  if(cents===null){msg.textContent="Informe um valor válido.";msg.className="save-msg error";return;}
  try{await db.collection("configuracoes").doc("entregador").set({valorEntregaCentavos:cents,atualizadoEm:firebase.firestore.FieldValue.serverTimestamp()},{merge:true});document.querySelector("#currentValue").textContent=money(cents);msg.textContent="Configuração salva no Firebase.";msg.className="save-msg ok";}
  catch(e){msg.textContent="Não foi possível salvar no Firebase.";msg.className="save-msg error";}
}
async function showSettings(){
  setActive("settings");setTitle("Configurações");const c=await getConfig();
  document.querySelector("#content").innerHTML='<div class="page-head"><div><small>CONFIGURAÇÕES</small><h2>Entregador</h2><p>Defina quanto o entregador recebe por cada entrega realizada.</p></div></div><div class="settings-grid"><article class="panel settings-card"><div class="setting-icon">R$</div><div><small>VALOR POR ENTREGA</small><h3>Pagamento do entregador</h3><p>Este valor será usado no cálculo do total a pagar. Não altera o valor da compra do cliente.</p></div><label class="field"><span>Valor por entrega</span><input id="valorEntrega" inputmode="decimal" placeholder="0,00" value="'+(c.valorEntregaCentavos?(c.valorEntregaCentavos/100).toLocaleString("pt-BR",{minimumFractionDigits:2}):"")+'"></label><button class="primary" id="saveConfig">SALVAR CONFIGURAÇÃO</button><div id="saveMsg" class="save-msg"></div></article><article class="panel info-card"><small>SISTEMA</small><h3>Conta do responsável</h3><p>Esta área controla as configurações do Dashboard.</p><button class="primary" id="logoutButton">SAIR DO SISTEMA</button><div class="preview"><small>VALOR ATUAL</small><strong id="currentValue">'+money(c.valorEntregaCentavos)+'</strong></div></article></div>';
  document.querySelector("#saveConfig").onclick=saveConfig;document.querySelector("#logoutButton").onclick=logout;
}

function renderCurrentPage(){
  const page=document.querySelector("[data-page].active")?.dataset.page||"home";
  if(page==="home")renderHomeData();
  else if(page==="deliveries")showDeliveries();
  else if(page==="done")showDone();
  else if(page==="finance")showFinance();
  else if(page==="settings")showSettings();
}

document.querySelectorAll("[data-page]").forEach(b=>{
  b.onclick=()=>{
    const page=b.dataset.page;
    if(page==="home")showHome();
    else if(page==="deliveries")showDeliveries();
    else if(page==="done")showDone();
    else if(page==="finance")showFinance();
    else if(page==="settings")showSettings();
  };
});

initFirebase();