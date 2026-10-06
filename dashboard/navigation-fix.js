(function(){
  function go(page){
    document.querySelectorAll('[data-page]').forEach(b=>b.classList.toggle('active',b.dataset.page===page));
    if(page==='home')showHome();
    else if(page==='deliveries')showDeliveries();
    else if(page==='done')showDone();
    else if(page==='charges')showCharges();
    else if(page==='finance')showFinance();
    else if(page==='settings')showSettings();
  }
  window.renderCurrentPage=function(){
    const active=document.querySelector('[data-page].active')?.dataset.page || 'home';
    go(active);
  };
  document.querySelectorAll('[data-page]').forEach(button=>{
    button.addEventListener('click',function(){go(this.dataset.page);});
  });
})();
