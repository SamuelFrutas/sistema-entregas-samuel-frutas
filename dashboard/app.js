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
function contactPhone(person){return (person.phoneNumbers||[]).map(p=>p.value||"").find(Boolean)||"";}
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
function googleContactName(person){return person.names?.[0]?.displayName||"Contato sem nome";}
async function openWhatsappCharge(e,person){
  const phone=normalizeWhatsapp(contactPhone(person));
  if(!phone){alert("Este contato não possui telefone cadastrado.");return;}
  const config=await getConfig();
  const template=config.mensagemCobranca||"Total do investimento na sua saúde : {{valor}}";
  const message=template.replace(/\{\{\s*valor\s*\}\}/gi,money(e.valorCompraCentavos));
  window.open("https://wa.me/"+phone+"?text="+encodeURIComponent(message),"_blank","noopener,noreferrer");
}
function googleContactsStatusText(){
  return googleContactsCache.length ? googleContactsCache.length+" contatos no banco" : "Contatos ainda não sincronizados";
}
function setGoogleContactsMessage(msg){
  const el=document.querySelector("#googleContactsMsg");
  if(el)el.textContent=msg;
}
function googleContactDocId(person,index){
  const raw=String(person.resourceName||((person.names?.[0]?.displayName||"")+"|"+(person.phoneNumbers?.[0]?.value||""))||("contato-"+index));
  try{
    return "g_"+btoa(unescape(encodeURIComponent(raw))).replace(/\+/g,"-").replace(/\//g,"_").replace(/=+$/,"").slice(0,140);
  }catch(e){
    return "g_"+encodeURIComponent(raw).slice(0,140);
  }
}
async function loadGoogleContactsFromDb(){
  if(googleContactsDbLoaded) return googleContactsCache;
  try{
    const snap=await db.collection("contatosGoogle").get();
    googleContactsCache=snap.docs.map(d=>d.data());
    googleContactsDbLoaded=true;
    return googleContactsCache;
  }catch(err){
    googleContactsDbLoaded=true;
    setGoogleContactsMessage("Não foi possível carregar os contatos salvos.");
    return [];
  }
}
async function saveGoogleContactsToDb(people){
  const incomingIds=new Set();
  const existingSnap=await db.collection("contatosGoogle").get();
  const operations=[];
  people.forEach((person,index)=>{
    const id=googleContactDocId(person,index);
    incomingIds.add(id);
    operations.push({
      type:"set",
      id,
      data:{
        resourceName:person.resourceName||"",
        names:person.names||[],
        phoneNumbers:person.phoneNumbers||[],
        nome:googleContactName(person),
        telefone:contactPhone(person),
        atualizadoEm:firebase.firestore.FieldValue.serverTimestamp()
      }
    });
  });
  existingSnap.docs.forEach(doc=>{
    if(!incomingIds.has(doc.id)) operations.push({type:"delete",ref:doc.ref});
  });
  for(let i=0;i<operations.length;i+=400){
    const batch=db.batch();
    operations.slice(i,i+400).forEach(op=>{
      if(op.type==="delete") batch.delete(op.ref);
      else batch.set(db.collection("contatosGoogle").doc(op.id),op.data,{merge:true});
    });
    await batch.commit();
  }
  googleContactsCache=people;
  googleContactsDbLoaded=true;
}
async function fetchGoogleContactsFromGoogle(token){
  const people=[];
  let pageToken="";
  do{
    const params=new URLSearchParams({personFields:"names,phoneNumbers,metadata",pageSize:"500"});
    if(pageToken)params.set("pageToken",pageToken);
    const res=await fetch("https://people.googleapis.com/v1/people/me/connections?"+params.toString(),{
      headers:{Authorization:"Bearer "+token}
    });
    if(res.status===401)throw new Error("AUTH_EXPIRED");
    if(!res.ok)throw new Error("Google Contacts: HTTP "+res.status);
    const data=await res.json();
    people.push(...(data.connections||[]));
    pageToken=data.nextPageToken||"";
  }while(pageToken);
  return people;
}
function initGoogleContactsClient(){
  if(!GOOGLE_CONTACTS_CLIENT_ID || !window.google?.accounts?.oauth2)return false;
  if(googleContactsTokenClient)return true;
  googleContactsTokenClient=google.accounts.oauth2.initTokenClient({
    client_id:GOOGLE_CONTACTS_CLIENT_ID,
    scope:GOOGLE_CONTACTS_SCOPE,
    callback:async response=>{
      if(response.error){
        setGoogleContactsMessage("Autorização cancelada.");
        return;
      }
      try{
        setGoogleContactsMessage("Baixando contatos do Google...");
        const people=await fetchGoogleContactsFromGoogle(response.access_token);
        setGoogleContactsMessage("Salvando "+people.length+" contatos no banco...");
        await saveGoogleContactsToDb(people);
        googleContactsToken=response.access_token;
        showCharges();
        setGoogleContactsMessage(people.length+" contatos atualizados no banco.");
      }catch(err){
        setGoogleContactsMessage(err.message==="AUTH_EXPIRED"?"A autorização expirou. Tente atualizar novamente.":"Não foi possível carregar os contatos do Google.");
      }
    }
  });
  return true;
}
function updateGoogleContacts(){
  if(!initGoogleContactsClient()){
    if(!GOOGLE_CONTACTS_CLIENT_ID)alert("Falta cadastrar o OAuth Client ID do Google Cloud.");
    else alert("O componente do Google ainda está carregando. Tente novamente.");
    return;
  }
  const prompt=googleContactsCache.length?"":"consent";
  googleContactsTokenClient.requestAccessToken({prompt});
}
function findContactsForDelivery(e){
  const key=deliveryAddressKey(e);if(!key)return [];
  return googleContactsCache.filter(p=>contactMatches(p,key)&&contactPhone(p));
}
async function startCharge(id){
  const e=currentDocs.find(x=>x.id===id);if(!e)return;
  if(!googleContactsDbLoaded)await loadGoogleContactsFromDb();
  const matches=findContactsForDelivery(e);
  if(!matches.length){alert("Nenhum contato salvo foi encontrado com o endereço "+deliveryAddressKey(e)+". Clique em ATUALIZAR CONTATOS se o cliente foi cadastrado recentemente.");return;}
  if(matches.length===1){openWhatsappCharge(e,matches[0]);return;}
  const overlay=document.createElement("div");overlay.className="modal-overlay";
  overlay.innerHTML='<div class="modal-card charge-modal"><div class="modal-head"><div><small>COBRANÇA</small><h2>Escolha o cliente</h2><p>Encontramos '+matches.length+' contatos para <b>'+esc(deliveryAddressKey(e))+'</b>.</p></div><button class="modal-close" id="closeCharge">×</button></div><div class="contact-options">'+matches.map((p,i)=>'<button class="contact-option" data-contact="'+i+'"><strong>'+esc(googleContactName(p))+'</strong><span>'+esc(contactPhone(p))+'</span></button>').join("")+'</div></div>';
  document.body.appendChild(overlay);
  document.getElementById("closeCharge").onclick=()=>overlay.remove();
  overlay.querySelectorAll(".contact-option").forEach(btn=>btn.onclick=()=>{openWhatsappCharge(e,matches[Number(btn.dataset.contact)]);overlay.remove();});
}
async function markCharged(id){
  const e=currentDocs.find(x=>x.id===id);if(!e)return;
  if(!confirm("Confirmar que este cliente já foi cobrado?"))return;
  try{
    await db.collection("entregas").doc(e.id).update({
      cobrancaFeita:true,
      cobradoEm:firebase.firestore.FieldValue.serverTimestamp()
    });
  }catch(err){
    alert("Não foi possível registrar a cobrança.");
  }
}
function chargeAction(e){
  if(!e.realizada || e.resultadoPagamento==="PAGO" || e.cobrancaFeita || (e.pagamentoInicial!=="NAO_PAGO" && e.resultadoPagamento!=="NAO_PAGO"))return "";
  return '<div class="charge-actions"><button class="row-action charge-btn" onclick="startCharge(\''+esc(e.id)+'\')">COBRAR NO WHATSAPP</button><button class="row-action charged-btn" onclick="markCharged(\''+esc(e.id)+'\')">JÁ COBREI</button></div>';
}
function renderChargeRow(e){
  const key=deliveryAddressKey(e);
  return '<div class="delivery-row charge-row"><div><strong>'+esc(formatEndereco(e))+'</strong><small>'+esc((e.dia||"—")+" • Não pago"+(key?" • "+key:""))+'</small></div><div class="row-end"><b>'+money(e.valorCompraCentavos)+'</b>'+chargeAction(e)+'</div></div>';
}
function showCharges(){
  setActive("charges");setTitle("Cobranças");
  const unpaid=currentDocs.filter(e=>!!e.realizada&&!e.cobrancaFeita&&e.resultadoPagamento!=="PAGO"&&(e.pagamentoInicial==="NAO_PAGO"||e.resultadoPagamento==="NAO_PAGO"));
  document.querySelector("#content").innerHTML='<div class="page-head page-head-actions"><div><small>CLIENTES COM PAGAMENTO PENDENTE</small><h2>Cobrar pelo WhatsApp</h2><p>O sistema procura o endereço nos contatos salvos no banco e abre a conversa com a mensagem pronta.</p></div><button class="primary add-btn" id="googleContactsButton">'+(googleContactsCache.length?"ATUALIZAR CONTATOS":"CONECTAR GOOGLE CONTATOS")+'</button></div><div class="panel charge-connection"><div><small>GOOGLE CONTACTS</small><h3>'+esc(googleContactsStatusText())+'</h3><p id="googleContactsMsg">Os contatos ficam armazenados no banco. Quando cadastrar novos clientes no Google, clique em “ATUALIZAR CONTATOS”.</p></div></div><article class="panel"><div class="panel-head"><div><small>PAGAMENTOS</small><h3>'+unpaid.length+' pendente'+(unpaid.length===1?"":"s")+'</h3></div></div><div class="delivery-list">'+(unpaid.length?unpaid.map(renderChargeRow).join(""):renderEmpty("Nenhuma cobrança pendente."))+'</div></article>';
  document.getElementById("googleContactsButton").onclick=updateGoogleContacts;
  if(!googleContactsDbLoaded){
    loadGoogleContactsFromDb().then(()=>showCharges());
  }
}

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
    backfillDeliveryPayout();
  },e=>{
    const status=document.querySelector(".status");
    if(status)status.innerHTML='● Firebase<br><small>Erro: '+(e.code||"leitura")+'</small>';
    currentDocs=[];
    renderCurrentPage();
  });
}
async function backfillDeliveryPayout(){
  try{
    const config=await getConfig();
    const cents=Number(config.valorEntregaCentavos||0);
    if(cents<=0)return;
    const missing=currentDocs.filter(e=>e.valorEntregaCentavos==null);
    for(let i=0;i<missing.length;i+=450){
      const batch=db.batch();
      missing.slice(i,i+450).forEach(e=>{
        batch.set(db.collection("entregas").doc(String(e.id)),{valorEntregaCentavos:cents},{merge:true});
      });
      await batch.commit();
    }
  }catch(e){}
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
    '<div class="form-grid"><label>Prédio<input id="editPredio" type="text" inputmode="numeric" pattern="[0-9]*" oninput="this.value=this.value.replace(/[^0-9]/g,\"\")" value="'+esc(e.predio||"")+'"></label><label>Bloco<input id="editBloco" value="'+esc(e.bloco||"")+'"></label><label>Apartamento<input id="editApto" type="text" inputmode="numeric" pattern="[0-9]*" oninput="this.value=this.value.replace(/[^0-9]/g,\"\")" value="'+esc(e.apartamento||"")+'"></label><label>Valor da compra<input id="editValor" inputmode="decimal" value="'+(e.valorCompraCentavos==null?"":(Number(e.valorCompraCentavos)/100).toLocaleString("pt-BR",{minimumFractionDigits:2}))+'"></label></div>'+
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
    '<div class="form-grid"><label>Prédio<input id="newPredio" type="text" inputmode="numeric" pattern="[0-9]*" oninput="this.value=this.value.replace(/[^0-9]/g,\"\")"></label><label>Bloco<input id="newBloco"></label><label>Apartamento<input id="newApto" type="text" inputmode="numeric" pattern="[0-9]*" oninput="this.value=this.value.replace(/[^0-9]/g,\"\")"></label><label>Valor da compra<input id="newValor" inputmode="decimal" placeholder="0,00"></label></div>'+
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
    const data={dia:todayKey(),predio:document.getElementById("newPredio").value.trim(),bloco:document.getElementById("newBloco").value.trim(),apartamento:document.getElementById("newApto").value.trim(),semEndereco:document.getElementById("newSemEndereco").checked,enderecoReferencia:document.getElementById("newReferencia").value.trim(),valorCompraCentavos:valor,pagamentoInicial:document.getElementById("newPagamento").value,resultadoPagamento:"",formaPagamento:"",caixinhaCentavos:0,observacao:"",realizada:false,cobrancaFeita:false,sincronizacao:"SINCRONIZADA"};
    try{const snap=await db.collection("entregas").get();const ids=snap.docs.map(d=>Number(d.id)).filter(n=>Number.isInteger(n)&&n>0&&n<1000000);const id=String((ids.length?Math.max(...ids):0)+1);await db.collection("entregas").doc(id).set({...data,id:Number(id),atualizadoEm:firebase.firestore.FieldValue.serverTimestamp()});overlay.remove();}
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

function historyDateLabel(key){
  const parts=String(key||"").split("-");
  if(parts.length!==3)return key;
  return parts[2]+"/"+parts[1]+"/"+parts[0];
}
function renderDoneGroup(docs,key){
  const items=docs.filter(e=>e.dia===key);
  if(!items.length)return "";
  const charged=items.filter(e=>e.cobrancaFeita).length;
  return '<section class="history-day"><div class="history-day-head"><div><small>'+historyDateLabel(key)+'</small><h3>'+items.length+' entrega'+(items.length===1?"":"s")+'</h3></div><span>'+charged+' cobrança'+(charged===1?"":"s")+' feita'+(charged===1?"":"s")+'</span></div><div class="delivery-list">'+items.map(e=>{
    const payment=e.resultadoPagamento==="PAGO"?"PAGO":(e.cobrancaFeita?"COBRADO":"PENDENTE");
    const meta=[e.dia||"—",payment,e.formaPagamento||""].filter(Boolean).join(" • ");
    return '<div class="delivery-row"><div><strong>'+esc(formatEndereco(e))+'</strong><small>'+esc(meta)+'</small></div><div class="row-end"><b>'+(e.valorCompraCentavos==null?"—":money(e.valorCompraCentavos))+'</b>'+deliveryActions(e)+'</div></div>';
  }).join("")+'</div></section>';
}
function showDone(){
  setActive("done");setTitle("Realizadas");
  const workedDates=[...new Set(currentDocs.filter(e=>!!e.realizada&&e.dia).map(e=>e.dia))]
    .sort((a,b)=>String(b).localeCompare(String(a)))
    .slice(0,3);
  const recentDone=currentDocs.filter(e=>!!e.realizada&&workedDates.includes(e.dia));
  const charged=recentDone.filter(e=>e.cobrancaFeita).length;
  const groups=workedDates.map(key=>renderDoneGroup(recentDone,key)).join("");
  document.querySelector("#content").innerHTML='<div class="page-head"><div><small>ÚLTIMAS 3 DATAS DE TRABALHO</small><h2>Entregas realizadas</h2><p>As três datas mais recentes em que houve entregas, mesmo que você não tenha trabalhado em dias consecutivos. As cobranças feitas permanecem registradas junto à data.</p></div></div><div class="stats history-stats"><article><small>ENTREGAS</small><strong>'+recentDone.length+'</strong><span>Nas últimas 3 datas de trabalho</span></article><article><small>COBRANÇAS FEITAS</small><strong>'+charged+'</strong><span>Registradas</span></article></div><article class="panel"><div class="history-groups">'+(groups||renderEmpty("Nenhuma entrega realizada registrada."))+'</div></article>';
}

async function showFinance(){
  setActive("finance");setTitle("Financeiro");
  const done=currentDocs.filter(e=>!!e.realizada),todayDone=done.filter(e=>e.dia===todayKey()),tips=todayDone.reduce((s,e)=>s+Number(e.caixinhaCentavos||0),0),purchase=todayDone.reduce((s,e)=>s+Number(e.valorCompraCentavos||0),0),c=await getConfig(),payout=Number(c.valorEntregaCentavos||0)*todayDone.length;
  document.querySelector("#content").innerHTML='<div class="page-head"><div><small>FINANCEIRO</small><h2>Resumo financeiro</h2><p>Separação entre o valor das compras dos clientes e o pagamento do entregador.</p></div></div><div class="stats"><article><small>COMPRAS HOJE</small><strong>'+money(purchase)+'</strong><span>Valor informado nas entregas</span></article><article><small>ENTREGAS REALIZADAS</small><strong>'+todayDone.length+'</strong><span>Hoje</span></article><article><small>A PAGAR AO ENTREGADOR</small><strong>'+money(payout)+'</strong><span>'+money(c.valorEntregaCentavos)+' por entrega</span></article><article><small>CAIXINHA</small><strong>'+money(tips)+'</strong><span>Hoje</span></article></div><div class="columns"><article class="panel"><small>PAGAMENTO DO ENTREGADOR</small><h3>Valor configurado</h3><div class="rows"><div>Valor por entrega <b>'+money(c.valorEntregaCentavos)+'</b></div><div>Entregas realizadas hoje <b>'+todayDone.length+'</b></div><div>Total a pagar hoje <b>'+money(payout)+'</b></div></div></article><article class="panel"><small>CLIENTES</small><h3>Valor das compras</h3><div class="rows"><div>Compras registradas hoje <b>'+money(purchase)+'</b></div><div>Caixinha registrada hoje <b>'+money(tips)+'</b></div></div></article></div>';
}

function loadConfig(){return {valorEntregaCentavos:0};}
async function getConfig(){
  try{const snap=await db.collection("configuracoes").doc("entregador").get();return snap.exists?Object.assign({valorEntregaCentavos:0,mensagemCobranca:"Total do investimento na sua saúde : {{valor}}"},snap.data()):Object.assign(loadConfig(),{mensagemCobranca:"Total do investimento na sua saúde : {{valor}}"});}
  catch(e){return Object.assign(loadConfig(),{mensagemCobranca:"Total do investimento na sua saúde : {{valor}}"});}
}
async function saveConfig(){
  const cents=parseMoney(document.querySelector("#valorEntrega").value),msg=document.querySelector("#saveMsg");
  if(cents===null){msg.textContent="Informe um valor válido.";msg.className="save-msg error";return;}
  try{const mensagemCobranca=(document.querySelector("#mensagemCobranca").value||"").trim();if(!mensagemCobranca){msg.textContent="Informe a mensagem de cobrança.";msg.className="save-msg error";return;}await db.collection("configuracoes").doc("entregador").set({valorEntregaCentavos:cents,mensagemCobranca,atualizadoEm:firebase.firestore.FieldValue.serverTimestamp()},{merge:true});await backfillDeliveryPayout();document.querySelector("#currentValue").textContent=money(cents);msg.textContent="Configuração salva no Firebase.";msg.className="save-msg ok";}
  catch(e){msg.textContent="Não foi possível salvar no Firebase.";msg.className="save-msg error";}
}
async function showSettings(){
  setActive("settings");setTitle("Configurações");const c=await getConfig();
  document.querySelector("#content").innerHTML='<div class="page-head"><div><small>CONFIGURAÇÕES</small><h2>Entregador</h2><p>Defina quanto o entregador recebe por cada entrega e controle a mensagem enviada na cobrança.</p></div></div><div class="settings-grid"><article class="panel settings-card"><div class="setting-icon">R$</div><div><small>VALOR POR ENTREGA</small><h3>Pagamento do entregador</h3><p>Este valor será usado no cálculo do total a pagar. Não altera o valor da compra do cliente.</p></div><label class="field"><span>Valor por entrega</span><input id="valorEntrega" inputmode="decimal" placeholder="0,00" value="'+(c.valorEntregaCentavos?(c.valorEntregaCentavos/100).toLocaleString("pt-BR",{minimumFractionDigits:2}):"")+'"></label><div style="height:1px;background:rgba(91,154,255,.15);margin:8px 0 2px"></div><div><small>MENSAGEM DE COBRANÇA</small><h3>WhatsApp do cliente</h3><p>Use <b>{{valor}}</b> no lugar onde o valor da compra deve aparecer.</p></div><label class="field"><span>Mensagem enviada</span><textarea id="mensagemCobranca" rows="4" placeholder="Total do investimento na sua saúde : {{valor}}">'+esc(c.mensagemCobranca||"Total do investimento na sua saúde : {{valor}}")+'</textarea></label><button class="primary" id="saveConfig">SALVAR CONFIGURAÇÃO</button><div id="saveMsg" class="save-msg"></div></article><article class="panel info-card"><small>SISTEMA</small><h3>Conta do responsável</h3><p>Esta área controla as configurações do Dashboard.</p><button class="primary" id="logoutButton">SAIR DO SISTEMA</button><div class="preview"><small>VALOR ATUAL</small><strong id="currentValue">'+money(c.valorEntregaCentavos)+'</strong></div></article></div>';
  document.querySelector("#saveConfig").onclick=saveConfig;document.querySelector("#logoutButton").onclick=logout;
}

function renderCurrentPage(){
  const page=document.querySelector("[data-page].active")?.dataset.page||"home";
  if(page==="home")renderHomeData();
  else if(page==="deliveries")showDeliveries();
  else if(page==="done")showDone();
  else if(page==="charges")showCharges();
  else if(page==="finance")showFinance();
  else if(page==="settings")showSettings();
}

document.querySelectorAll("[data-page]").forEach(b=>{
  b.onclick=()=>{
    const page=b.dataset.page;
    if(page==="home")showHome();
    else if(page==="deliveries")showDeliveries();
    else if(page==="done")showDone();
    else if(page==="charges")showCharges();
    else if(page==="finance")showFinance();
    else if(page==="settings")showSettings();
  };
});

initFirebase();