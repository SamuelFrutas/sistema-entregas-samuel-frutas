/*
 * Correção definitiva do carregamento das entregas:
 * o APK pode gravar um campo "id" dentro do documento, mas o Dashboard
 * deve usar sempre o ID real do documento Firestore.
 *
 * O app.js inicializa o listener antes deste arquivo ser carregado, então
 * depois de substituir startListener precisamos reiniciar o listener uma vez.
 */
(function(){
  const originalStartListener=window.startListener;
  if(typeof originalStartListener!=="function") return;

  window.startListener=function(){
    if(entregasUnsub)return;
    entregasUnsub=db.collection("entregas").onSnapshot(snap=>{
      currentDocs=snap.docs.map(d=>Object.assign({},d.data(),{id:d.id}));
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
  };

  // app.js já chamou initFirebase(); por isso o primeiro listener pode ter
  // sido criado usando a versão antiga. Substituímos por uma única versão correta.
  if(typeof currentUser!=="undefined" && currentUser){
    if(typeof stopListener==="function")stopListener();
    window.startListener();
  }
})();
