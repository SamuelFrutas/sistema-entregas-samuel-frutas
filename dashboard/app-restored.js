function esc(v){return String(v??"").replace(/[&<>"']/g,m=>({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[m]));}
function openDelivery(id){
  const e=currentDocs.find(x=>x.id===id); if(!e)return;
  const overlay=document.createElement("div"); overlay.className="modal-overlay"; overlay.id="deliveryModal";
  overlay.innerHTML='<div class="modal-card"><div class="modal-head"><div><small>ENTREGA</small><h2>'+esc(formatEndereco(e))+'</h2></div><button class="modal-close" id="closeDelivery">×</button></div>'+\
    '<div class="form-grid"><label>Prédio<input id="editPredio" type="text" inputmode="numeric" pattern="[0-9]*" oninput="this.value=this.value.replace(/[^0-9]/g,\"\")" value="'+esc(e.predio||"")+'"></label><label>Bloco<input id="editBloco" value="'+esc(e.bloco||"")+'"></label><label>Apartamento<input id="editApto" type="text" inputmode="numeric" pattern="[0-9]*" oninput="this.value=this.value.replace(/[^0-9]/g,\"\")" value="'+esc(e.apartamento||"")+'"></label><label>Valor da compra<input id="editValor" inputmode="decimal" value="'+(e.valorCompraCentavos==null?"":(Number(e.valorCompraCentavos)/100).toLocaleString("pt-BR",{minimumFractionDigits:2}))+'"></label></div>'+\
    '<label class="check-line"><input id="editSemEndereco" type="checkbox" '+(e.semEndereco?"checked":"")+'> Entrega sem endereço</label>'+\
    '<label class="full-field">Endereço / referência<textarea id="editReferencia">'+esc(e.enderecoReferencia||"")+'</textarea></label>'+\
    '<label class="full-field">Pagamento inicial<select id="editPagamento"><option value="PAGO_ADIANTADO">Pago adiantado</option><option value="NAO_PAGO">Não pago</option><option value="NAO_INFORMADO">Não informado</option></select></label>'+\
    '<div class="modal-actions"><button class="danger" id="deleteDelivery">EXCLUIR ENTREGA</button><button class="primary" id="saveDelivery">SALVAR ALTERAÇÕES</button></div><div id="editMsg" class="save-msg"></div></div>';
  document.body.appendChild(overlay);
  document.getElementById("editPagamento").value=e.pagamentoInicial||"NAO_INFORMADO";
  document.getElementById("closeDelivery").onclick=()=>overlay.remove();
  document.getElementById("saveDelivery").onclick=async()=>{
    const valor=parseMoney(document.getElementById("editValor").value);
    const updated={...e,predio:document.getElementById("editPredio").value.trim(),bloco:document.getElementById("editBloco").value.trim(),apartamento:document.getElementById("editApto").value.trim(),semEndereco:document.getElementById("editSemEndereco").checked,enderecoReferencia:document.getElementById("editReferencia").value.trim(),valorCompraCentavos:valor,pagamentoInicial:document.getElementById("editPagamento").value};
    const msg=document.getElementById("editMsg");
    try{await db.collection("entregas").doc(e.id).update(updated);msg.textContent="Alterações salvas.";msg.className="save-msg ok";setTimeout(()=>overlay.remove(),500);}catch(err){msg.textContent="Não foi possível salvar.";msg.className="save-msg error";}
  };
  document.getElementById("deleteDelivery").onclick=async()=>{
    if(!confirm("Excluir esta entrega? Essa ação não poderá ser desfeita."))return;
    try{await db.collection("entregas").doc(e.id).delete();overlay.remove();}catch(err){const msg=document.getElementById("editMsg");msg.textContent="Não foi possível excluir.";msg.className="save-msg error";}
  };
}
async function nextDashboardDeliveryId(){
  const ref=db.collection("configuracoes").doc("entregador");
  return db.runTransaction(async transaction=>{
    const snap=await transaction.get(ref);
    const current=Number(snap.exists?snap.data().proximoIdDashboard:null);
    const next=Number.isInteger(current)&&current<0 ? current-1 : -1;
    transaction.set(ref,{proximoIdDashboard:next},{merge:true});
    return next;
  });
}
function openNewDelivery(){
  const overlay=document.createElement("div"); overlay.className="modal-overlay"; overlay.id="deliveryModal";
  overlay.innerHTML='<div class="modal-card"><div class="modal-head"><div><small>NOVA ENTREGA</small><h2>Registrar entrega</h2></div><button class="modal-close" id="closeDelivery">×</button></div>'+\
    '<div class="form-grid"><label>Prédio<input id="newPredio" type="text" inputmode="numeric" pattern="[0-9]*" oninput="this.value=this.value.replace(/[^0-9]/g,\"\")"></label><label>Bloco<input id="newBloco"></label><label>Apartamento<input id="newApto" type="text" inputmode="numeric" pattern="[0-9]*" oninput="this.value=this.value.replace(/[^0-9]/g,\"\")"></label><label>Valor da compra<input id="newValor" inputmode="decimal" placeholder="0,00"></label></div>'+\
    '<label class="check-line"><input id="newSemEndereco" type="checkbox"> Entrega sem endereço</label>'+\
    '<label class="full-field">Endereço / referência<textarea id="newReferencia"></textarea></label>'+\
    '<label class="full-field">Pagamento inicial<select id="newPagamento"><option value="PAGO_ADIANTADO">Pago adiantado</option><option value="NAO_PAGO">Não pago</option><option value="NAO_INFORMADO">Não informado</option></select></label>'+\
    '<div class="modal-actions"><button class="modal-secondary" id="cancelNew">CANCELAR</button><button class="primary" id="createDelivery">ADICIONAR ENTREGA</button></div><div id="newMsg" class="save-msg"></div></div>';
  document.body.appendChild(overlay);
  document.getElementById("closeDelivery").onclick=()=>overlay.remove();
  document.getElementById("cancelNew").onclick=()=>overlay.remove();
  document.getElementById("createDelivery").onclick=async()=>{
    const valor=parseMoney(document.getElementById("newValor").value);const msg=document.getElementById("newMsg");
    const data={dia:todayKey(),predio:document.getElementById("newPredio").value.trim(),bloco:document.getElementById("newBloco").value.trim(),apartamento:document.getElementById("newApto").value.trim(),semEndereco:document.getElementById("newSemEndereco").checked,enderecoReferencia:document.getElementById("newReferencia").value.trim(),valorCompraCentavos:valor,pagamentoInicial:document.getElementById("newPagamento").value,resultadoPagamento:"",formaPagamento:"",caixinhaCentavos:0,observacao:"",realizada:false,cobrancaFeita:false,sincronizacao:"SINCRONIZADA"};
    try{const idNumber=await nextDashboardDeliveryId();const id=String(idNumber);await db.collection("entregas").doc(id).set({...data,id:idNumber,atualizadoEm:firebase.firestore.FieldValue.serverTimestamp()});overlay.remove();}catch(err){msg.textContent="Não foi possível adicionar.";msg.className="save-msg error";}
  };
}
function deliveryActions(e){return '<button class="row-action" onclick="openDelivery(\''+esc(e.id)+'\')">EDITAR</button>';}
function renderList(type){
  const today=currentDocs.filter(e=>e.dia===todayKey());
  const docs=type==="pending"?today.filter(e=>!e.realizada):currentDocs.filter(e=>!!e.realizada).sort((a,b)=>String(b.dia||"").localeCompare(String(a.dia||"")));
  if(!docs.length)return renderEmpty(type==="pending"?"Nenhuma entrega pendente hoje.":"Nenhuma entrega realizada registrada.");
  return docs.map(e=>{const payment=e.realizada&&e.formaPagamento?e.formaPagamento:(e.pagamentoInicial?paymentLabel(e):"—");const meta=[e.dia||"—",e.realizada?"REALIZADA":"PENDENTE",payment].filter(Boolean).join(" • ");return '<div class="delivery-row"><div><strong>'+esc(formatEndereco(e))+'</strong><small>'+esc(meta)+'</small></div><div class="row-end"><b>'+(e.valorCompraCentavos==null?"—":money(e.valorCompraCentavos))+'</b>'+deliveryActions(e)+'</div></div>';}).join("");
}
function showDeliveries(){
  setActive("deliveries");setTitle("Entregas");
  const pending=currentDocs.filter(e=>e.dia===todayKey()&&!e.realizada);
  document.querySelector("#content").innerHTML='<div class="page-head page-head-actions"><div><small>ACOMPANHAMENTO</small><h2>Entregas pendentes</h2><p>Entregas registradas pelo entregador que ainda não foram concluídas.</p></div><button class="primary add-btn" id="addDelivery">+ NOVA ENTREGA</button></div><article class="panel"><div class="panel-head"><div><small>HOJE</small><h3>'+pending.length+' pendente'+(pending.length===1?"":"s")+'</h3></div><span class="live-dot">AO VIVO</span></div><div class="delivery-list" id="pageList">'+renderList("pending")+'</div></article>';
  const addButton=document.querySelector("#addDelivery");if(addButton)addButton.onclick=openNewDelivery;
}
function historyDateLabel(key){const parts=String(key||"").split("-");if(parts.length!==3)return key;return parts[2]+"/"+parts[1]+"/"+parts[0];}
function renderDoneGroup(docs,key){
  const items=docs.filter(e=>e.dia===key);if(!items.length)return "";
  const charged=items.filter(e=>e.cobrancaFeita).length;
  return '<section class="history-day"><div class="history-day-head"><div><small>'+historyDateLabel(key)+'</small><h3>'+items.length+' entrega'+(items.length===1?"":"s")+'</h3></div><span>'+charged+' cobrança'+(charged===1?"":"s")+' feita'+(charged===1?"":"s")+'</span></div><div class="delivery-list">'+items.map(e=>{const payment=e.resultadoPagamento==="PAGO"?"PAGO":(e.cobrancaFeita?"COBRADO":"PENDENTE");const meta=[e.dia||"—",payment,e.formaPagamento||""].filter(Boolean).join(" • ");return '<div class="delivery-row"><div><strong>'+esc(formatEndereco(e))+'</strong><small>'+esc(meta)+'</small></div><div class="row-end"><b>'+(e.valorCompraCentavos==null?"—":money(e.valorCompraCentavos))+'</b>'+deliveryActions(e)+'</div></div>';}).join("")+'</div></section>';
}
function showDone(){
  setActive("done");setTitle("Realizadas");
  const workedDates=[...new Set(currentDocs.filter(e=>!!e.realizada&&e.dia).map(e=>e.dia))].sort((a,b)=>String(b).localeCompare(String(a))).slice(0,3);
  const recentDone=currentDocs.filter(e=>!!e.realizada&&workedDates.includes(e.dia));const groups=workedDates.map(key=>renderDoneGroup(recentDone,key)).join("");
  document.querySelector("#content").innerHTML='<div class="page-head"><div><small>ÚLTIMAS 3 DATAS DE TRABALHO</small><h2>Entregas realizadas</h2><p>As três datas mais recentes em que houve entregas, mesmo que você não tenha trabalhado em dias consecutivos. As cobranças feitas permanecem registradas junto à data.</p></div></div><article class="panel"><div class="history-groups">'+(groups||renderEmpty("Nenhuma entrega realizada registrada."))+'</div></article>';
  document.querySelectorAll(".history-day-head > span").forEach(el=>{const group=el.closest(".history-day");const key=group?.querySelector(".history-day-head small")?.textContent;const source=key?recentDone.filter(e=>historyDateLabel(e.dia)===key):[];const pending=source.filter(e=>e.realizada&&!e.cobrancaFeita&&e.resultadoPagamento!=="PAGO"&&(e.pagamentoInicial==="NAO_PAGO"||e.resultadoPagamento==="NAO_PAGO")).length;el.textContent=pending+" cobranças pendentes";el.style.color=pending===0?"var(--green)":"var(--danger)";});
}
async function showFinance(){
  setActive("finance");setTitle("Financeiro");
  const todayDone=currentDocs.filter(e=>e.dia===todayKey()&&!!e.realizada);
  const totalVendas=todayDone.reduce((sum,e)=>sum+Number(e.valorCompraCentavos||0),0);
  const pagoCartao=todayDone.filter(e=>e.resultadoPagamento==="PAGO"&&String(e.formaPagamento||"").toUpperCase().includes("CART")).reduce((sum,e)=>sum+Number(e.valorCompraCentavos||0),0);
  const pagoDinheiro=todayDone.filter(e=>e.resultadoPagamento==="PAGO"&&String(e.formaPagamento||"").toUpperCase().includes("DINHE")).reduce((sum,e)=>sum+Number(e.valorCompraCentavos||0),0);
  const cobrancas=todayDone.filter(e=>e.resultadoPagamento!=="PAGO"&&(e.pagamentoInicial==="NAO_PAGO"||e.resultadoPagamento==="NAO_PAGO")).reduce((sum,e)=>sum+Number(e.valorCompraCentavos||0),0);
  document.querySelector("#content").innerHTML='<div class="page-head"><div><small>FINANCEIRO</small><h2>Resumo financeiro</h2><p>Valores das compras das entregas realizadas hoje.</p></div></div>'+'<div class="stats finance-stats">'+'<article><small>VALOR TOTAL DAS ENTREGAS</small><strong>'+money(totalVendas)+'</strong><span>Vendas realizadas hoje</span></article>'+'<article><small>PAGO COM CARTÃO</small><strong>'+money(pagoCartao)+'</strong><span>Compras pagas com cartão</span></article>'+'<article><small>PAGO COM DINHEIRO</small><strong>'+money(pagoDinheiro)+'</strong><span>Compras pagas em dinheiro</span></article>'+'<article><small>COBRANÇAS</small><strong>'+money(cobrancas)+'</strong><span>Compras ainda não pagas</span></article>'+'</div>';
}
function loadConfig(){return {valorEntregaCentavos:0};}
async function getConfig(){try{const snap=await db.collection("configuracoes").doc("entregador").get();return snap.exists?Object.assign({valorEntregaCentavos:0,mensagemCobranca:"Total do investimento na sua saúde : {{valor}}"},snap.data()):Object.assign(loadConfig(),{mensagemCobranca:"Total do investimento na sua saúde : {{valor}}"});}catch(e){return Object.assign(loadConfig(),{mensagemCobranca:"Total do investimento na sua saúde : {{valor}}"});}}
async function saveConfig(){const cents=parseMoney(document.querySelector("#valorEntrega").value),msg=document.querySelector("#saveMsg");if(cents===null){msg.textContent="Informe um valor válido.";msg.className="save-msg error";return;}try{const mensagemCobranca=(document.querySelector("#mensagemCobranca").value||"").trim();if(!mensagemCobranca){msg.textContent="Informe a mensagem de cobrança.";msg.className="save-msg error";return;}await db.collection("configuracoes").doc("entregador").set({valorEntregaCentavos:cents,mensagemCobranca,atualizadoEm:firebase.firestore.FieldValue.serverTimestamp()},{merge:true});await backfillDeliveryPayout();document.querySelector("#currentValue").textContent=money(cents);msg.textContent="Configuração salva no Firebase.";msg.className="save-msg ok";}catch(e){msg.textContent="Não foi possível salvar no Firebase.";msg.className="save-msg error";}}
async function showSettings(){
  setActive("settings");setTitle("Configurações");const c=await getConfig();
  document.querySelector("#content").innerHTML='<div class="page-head"><div><small>CONFIGURAÇÕES</small><h2>Entregador</h2><p>Defina quanto o entregador recebe por cada entrega e controle a mensagem enviada na cobrança.</p></div></div><div class="settings-grid"><article class="panel settings-card"><div class="setting-icon">R$</div><div><small>VALOR POR ENTREGA</small><h3>Pagamento do entregador</h3><p>Este valor será usado no cálculo do total a pagar. Não altera o valor da compra do cliente.</p></div><label class="field"><span>Valor por entrega</span><input id="valorEntrega" inputmode="decimal" placeholder="0,00" value="'+(c.valorEntregaCentavos?(c.valorEntregaCentavos/100).toLocaleString("pt-BR",{minimumFractionDigits:2}):"")+'"></label><div style="height:1px;background:rgba(91,154,255,.15);margin:8px 0 2px"></div><div><small>MENSAGEM DE COBRANÇA</small><h3>WhatsApp do cliente</h3><p>Use <b>{{valor}}</b> no lugar onde o valor da compra deve aparecer.</p></div><label class="field"><span>Mensagem enviada</span><textarea id="mensagemCobranca" rows="4" placeholder="Total do investimento na sua saúde : {{valor}}">'+esc(c.mensagemCobranca||"Total do investimento na sua saúde : {{valor}}")+'</textarea></label><button class="primary" id="saveConfig">SALVAR CONFIGURAÇÃO</button><div id="saveMsg" class="save-msg"></div></article><article class="panel info-card"><small>SISTEMA</small><h3>Conta do responsável</h3><p>Esta área controla as configurações do Dashboard.</p><button class="primary" id="logoutButton">SAIR DO SISTEMA</button><div class="preview"><small>VALOR ATUAL</small><strong id="currentValue">'+money(c.valorEntregaCentavos)+'</strong></div></article></div>';
  document.querySelector("#saveConfig").onclick=saveConfig;document.querySelector("#logoutButton").onclick=logout;
}
function renderCurrentPage(){const page=document.querySelector("[data-page].active")?.dataset.page||"home";if(page==="home")renderHomeData();else if(page==="deliveries")showDeliveries();else if(page==="done")showDone();else if(page==="charges")showCharges();else if(page==="finance")showFinance();else if(page==="settings")showSettings();}
document.querySelectorAll("[data-page]").forEach(b=>{b.onclick=()=>{const page=b.dataset.page;if(page==="home")showHome();else if(page==="deliveries")showDeliveries();else if(page==="done")showDone();else if(page==="charges")showCharges();else if(page==="finance")showFinance();else if(page==="settings")showSettings();};});
initFirebase();