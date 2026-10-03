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
  return [
    names,
    person.nome||"",
    person.name||"",
    contactPhone(person),
    person.telefone||"",
    person.phone||""
  ].join(" ");
}
function contactNameMatches(person,term){
  const norm=v=>String(v||"").trim().toLocaleLowerCase("pt-BR").normalize("NFD").replace(/[\\u0300-\\u036f]/g,"").replace(/\\s+/g," ");
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

  // O nome da referência é apenas o valor inicial. Depois que o modal abre,
  // o campo é uma pesquisa completamente independente e pode ser apagado/trocado.
  search.value=String(initialTerm||"");

  const normalizeSearch=v=>String(v??"").trim().toLocaleLowerCase("pt-BR").normalize("NFD").replace(/[\\u0300-\\u036f]/g,"").replace(/\\s+/g," ");
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

  // Escuta input + teclado para garantir que apagar/trocar o texto sempre atualize a lista.
  search.addEventListener("input",render);
  search.addEventListener("keyup",render);
  search.addEventListener("change",render);
  overlay.querySelector("#closeChargeContact").onclick=()=>overlay.remove();

  // Recarrega do Firestore para a pesquisa manual não depender de uma lista antiga.
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
  const key=deliveryAddressKey(e);
  if(!key)return [];
  return googleContactsCache.filter(p=>contactMatches(p,key)&&contactPhone(p));
}
function findContactsForName(e){
  const nome=String(e.enderecoReferencia||"").trim();
  if(!nome)return [];
  return googleContactsCache.filter(p=>contactPhone(p)&&contactNameMatches(p,nome));
}
function openChargeMatchesPicker(e,matches,titleText){
  const overlay=document.createElement("div");
  overlay.className="modal-overlay";
  overlay.innerHTML='<div class="modal-card charge-modal"><div class="modal-head"><div><small>COBRANÇA</small><h2>Escolha o cliente</h2><p>'+esc(titleText)+'</p></div><button class="modal-close" id="closeCharge">×</button></div><div class="contact-options">'+matches.map((p,i)=>'<button class="contact-option" data-contact="'+i+'"><strong>'+esc(googleContactName(p))+'</strong><span>'+esc(contactPhone(p))+'</span></button>').join("")+'</div></div>';
  document.body.appendChild(overlay);
  overlay.querySelector("#closeCharge").onclick=()=>overlay.remove();
  overlay.querySelectorAll(".contact-option").forEach(btn=>btn.onclick=()=>{
    const person=matches[Number(btn.dataset.contact)];
    overlay.remove();
    openWhatsappCharge(e,person);
  });
}
async function startCharge(id){
  const e=currentDocs.find(x=>x.id===id);if(!e)return;
  if(!googleContactsDbLoaded)await loadGoogleContactsFromDb();

  // SEM ENDEREÇO: a referência é o nome do cliente. Não procura pela chave de endereço.
  if(e.semEndereco){
    const nome=String(e.enderecoReferencia||"").trim();
    if(nome){
      const matches=findContactsForName(e);
      if(matches.length>=1){
        // Para referência/nome, sempre usamos o seletor com campo de pesquisa.
        // O nome informado pelo entregador entra apenas como busca inicial;
        // o usuário pode apagar e pesquisar qualquer outro contato.
        openChargeContactPicker(e,nome);
        return;
      }
    }
    openChargeContactPicker(e,nome);
    return;
  }

  // COM ENDEREÇO: procura exclusivamente pela chave formada por prédio/bloco/apartamento.
  const matches=findContactsForDelivery(e);
  if(matches.length===1){openWhatsappCharge(e,matches[0]);return;}
  if(matches.length>1){
    openChargeMatchesPicker(e,matches,"Encontramos "+matches.length+" contatos para "+deliveryAddressKey(e)+".");
    return;
  }

  // Endereço informado, mas nenhum contato localizado.
  openChargeContactPicker(e,"");
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
  document.querySelector("#content").innerHTML='<div class="page-head page-head-actions"><div><small>CLIENTES COM PAGAMENTO PENDENTE</small><h2>Cobrar pelo WhatsApp</h2><p>O sistema tenta localizar pelo endereço. Se a entrega foi cadastrada sem endereço, ele tenta encontrar automaticamente o nome informado nos contatos e, se necessário, abre a busca.</p></div><button class="primary add-btn" id="googleContactsButton">'+(googleContactsCache.length?"ATUALIZAR CONTATOS":"CONECTAR GOOGLE CONTATOS")+'</button></div><div class="panel charge-connection"><div><small>GOOGLE CONTACTS</small><h3>'+esc(googleContactsStatusText())+'</h3><p id="googleContactsMsg">Os contatos ficam armazenados no banco. Quando cadastrar novos clientes no Google, clique em “ATUALIZAR CONTATOS”.</p></div></div><article class="panel"><div class="panel-head"><div><small>PAGAMENTOS</small><h3>'+unpaid.length+' pendente'+(unpaid.length===1?"":"s")+'</h3></div></div><div class="delivery-list">'+(unpaid.length?unpaid.map(renderChargeRow).join(""):renderEmpty("Nenhuma cobrança pendente."))+'</div></article>';
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
  set(".stats article:nth-child(1) strong",today.length);
  set(".stats article:nth-child(2) strong",pending.length);
  set(".stats article:nth-child(3) strong",done.length);
  set("#todayHint",today.length===1?"1 entrega registrada":today.length+" entregas registradas");

  getConfig().then(c=>{
    const ganhoEntregas=Number(c.valorEntregaCentavos||0)*done.length;
    const totalPagar=ganhoEntregas+tips;
    const rows=document.querySelector(".home-summary-rows");
    if(rows)rows.innerHTML=
      '<div>Entregas realizadas <b>'+done.length+'</b></div>'+
      '<div>Ganhos entregas <b>'+money(ganhoEntregas)+'</b></div>'+
      '<div>Caixinhas <b>'+money(tips)+'</b></div>'+
      '<div>Total a pagar <b>'+money(totalPagar)+'</b></div>';
  });
}

function showHome(){
  setActive("home");setTitle("Visão geral");
  document.querySelector("#content").innerHTML=
    '<div class="hero"><div><small>HOJE</small><h2>Controle das entregas</h2><p>Acompanhe em tempo real o que foi registrado pelo entregador no APK.</p></div><strong class="hero-icon">▣</strong></div>'+
    '<div class="stats home-stats"><article><small>ENTREGAS HOJE</small><strong>0</strong><span id="todayHint">Nenhuma registrada</span></article>'+
    '<article><small>PENDENTES</small><strong>0</strong><span>Aguardando conclusão</span></article>'+
    '<article><small>REALIZADAS</small><strong>0</strong><span>Concluídas hoje</span></article></div>'+
    '<article class="panel home-summary"><small>ENTREGADOR</small><h3>Resumo do dia</h3><div class="rows home-summary-rows">'+
    '<div>Entregas realizadas <b>0</b></div><div>Ganhos entregas <b>R$ 0,00</b></div><div>Caixinhas <b>R$ 0,00</b></div><div>Total a pagar <b>R$ 0,00</b></div>'+
    '</div></article>';
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
  const groups=workedDates.map(key=>renderDoneGroup(recentDone,key)).join("");
  document.querySelector("#content").innerHTML='<div class="page-head"><div><small>ÚLTIMAS 3 DATAS DE TRABALHO</small><h2>Entregas realizadas</h2><p>As três datas mais recentes em que houve entregas, mesmo que você não tenha trabalhado em dias consecutivos. As cobranças feitas permanecem registradas junto à data.</p></div></div><article class="panel"><div class="history-groups">'+(groups||renderEmpty("Nenhuma entrega realizada registrada."))+'</div></article>';
  document.querySelectorAll(".history-day-head > span").forEach(el=>{
    const group=el.closest(".history-day");
    const key=group?.querySelector(".history-day-head small")?.textContent;
    const source=key?recentDone.filter(e=>historyDateLabel(e.dia)===key):[];
    const pending=source.filter(e=>
      e.realizada &&
      !e.cobrancaFeita &&
      e.resultadoPagamento!=="PAGO" &&
      (e.pagamentoInicial==="NAO_PAGO" || e.resultadoPagamento==="NAO_PAGO")
    ).length;
    el.textContent=pending+" cobranças pendentes";
    el.style.color=pending===0?"var(--green)":"var(--danger)";
  });
}

async function showFinance(){
  setActive("finance");setTitle("Financeiro");
  const todayDone=currentDocs.filter(e=>e.dia===todayKey()&&!!e.realizada);
  const totalVendas=todayDone.reduce((sum,e)=>sum+Number(e.valorCompraCentavos||0),0);
  const pagoCartao=todayDone.filter(e=>e.resultadoPagamento==="PAGO"&&String(e.formaPagamento||"").toUpperCase().includes("CART")).reduce((sum,e)=>sum+Number(e.valorCompraCentavos||0),0);
  const pagoDinheiro=todayDone.filter(e=>e.resultadoPagamento==="PAGO"&&String(e.formaPagamento||"").toUpperCase().includes("DINHE")).reduce((sum,e)=>sum+Number(e.valorCompraCentavos||0),0);
  const cobrancas=todayDone.filter(e=>e.resultadoPagamento!=="PAGO"&&(
    e.pagamentoInicial==="NAO_PAGO" || e.resultadoPagamento==="NAO_PAGO"
  )).reduce((sum,e)=>sum+Number(e.valorCompraCentavos||0),0);

  document.querySelector("#content").innerHTML=
    '<div class="page-head"><div><small>FINANCEIRO</small><h2>Resumo financeiro</h2><p>Valores das compras das entregas realizadas hoje.</p></div></div>'+
    '<div class="stats finance-stats">'+
      '<article><small>VALOR TOTAL DAS ENTREGAS</small><strong>'+money(totalVendas)+'</strong><span>Vendas realizadas hoje</span></article>'+
      '<article><small>PAGO COM CARTÃO</small><strong>'+money(pagoCartao)+'</strong><span>Compras pagas com cartão</span></article>'+
      '<article><small>PAGO COM DINHEIRO</small><strong>'+money(pagoDinheiro)+'</strong><span>Compras pagas em dinheiro</span></article>'+
      '<article><small>COBRANÇAS</small><strong>'+money(cobrancas)+'</strong><span>Compras ainda não pagas</span></article>'+
    '</div>';
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