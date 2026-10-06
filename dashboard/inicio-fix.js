function showHome(){
  setActive("home");
  setTitle("Visão geral");

  const today=todayKey();
  const todayDocs=currentDocs.filter(e=>e.dia===today);
  const done=todayDocs.filter(e=>!!e.realizada);
  const pending=todayDocs.filter(e=>!e.realizada);
  const tips=done.reduce((sum,e)=>sum+Number(e.caixinhaCentavos||0),0);

  getConfig().then(config=>{
    const valorEntrega=Number(config.valorEntregaCentavos||0);
    const ganhos=valorEntrega*done.length;
    const total=ganhos+tips;
    const content=document.querySelector("#content");
    if(!content)return;

    content.innerHTML='<div class="page-head"><div><small>RESUMO DO DIA</small><h2>Visão geral</h2><p>Acompanhe as entregas e os valores do entregador de hoje.</p></div></div>'+ 
      '<div class="stats home-stats">'+
      '<article><small>ENTREGAS HOJE</small><strong>'+todayDocs.length+'</strong><span>Entregas registradas</span></article>'+ 
      '<article><small>PENDENTES</small><strong>'+pending.length+'</strong><span>Ainda não realizadas</span></article>'+ 
      '<article><small>REALIZADAS</small><strong>'+done.length+'</strong><span>Entregas concluídas</span></article>'+ 
      '</div>'+ 
      '<article class="panel home-summary"><div class="panel-head"><div><small>VALORES DO ENTREGADOR</small><h3>Resumo do entregador</h3></div></div>'+ 
      '<div class="home-summary-rows">'+
      '<div class="home-summary-item"><span>Entregas realizadas</span><b>'+done.length+'</b></div>'+ 
      '<div class="home-summary-item"><span>Ganhos das entregas</span><b>'+money(ganhos)+'</b></div>'+ 
      '<div class="home-summary-item"><span>Caixinhas</span><b>'+money(tips)+'</b></div>'+ 
      '<div class="home-summary-item total"><span>Total a pagar</span><b>'+money(total)+'</b></div>'+ 
      '</div></article>';
  }).catch(()=>{});
}
