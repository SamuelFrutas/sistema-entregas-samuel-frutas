(function(){
  const originalStartListener=window.startListener;
  window.startListener=function(){
    if(typeof entregasUnsub!=='undefined' && entregasUnsub)return;
    entregasUnsub=db.collection('entregas').onSnapshot(snap=>{
      // IMPORTANTE: o ID real do documento Firestore deve prevalecer sobre
      // qualquer campo "id" gravado dentro do documento pelo APK.
      currentDocs=snap.docs.map(d=>Object.assign({},d.data(),{id:d.id}));
      const status=document.querySelector('.status');
      if(status)status.innerHTML='● Firebase<br><small>Sincronizado em tempo real</small>';
      renderCurrentPage();
      if(typeof backfillDeliveryPayout==='function')backfillDeliveryPayout();
    },e=>{
      const status=document.querySelector('.status');
      if(status)status.innerHTML='● Firebase<br><small>Erro: '+(e.code||'leitura')+'</small>';
      currentDocs=[];
      renderCurrentPage();
    });
  };
})();
