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