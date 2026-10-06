let dashboardCurrentPage = "home";

(function(){
  function go(page){
    dashboardCurrentPage = page || "home";
    document.querySelectorAll("[data-page]").forEach(b => b.classList.toggle("active", b.dataset.page === dashboardCurrentPage));
    if(dashboardCurrentPage === "home") showHome();
    else if(dashboardCurrentPage === "deliveries") showDeliveries();
    else if(dashboardCurrentPage === "done") showDone();
    else if(dashboardCurrentPage === "charges") showCharges();
    else if(dashboardCurrentPage === "finance") showFinance();
    else if(dashboardCurrentPage === "settings") showSettings();
  }

  window.renderCurrentPage = function(){
    go(dashboardCurrentPage);
  };

  // Usa um único roteador para os menus. O listener em captura impede
  // que os handlers antigos do app.js/app-restored-clean.js sobrescrevam
  // a página escolhida pelo usuário.
  document.addEventListener("click", function(event){
    const button = event.target.closest("[data-page]");
    if(!button)return;
    event.preventDefault();
    event.stopImmediatePropagation();
    go(button.dataset.page);
  }, true);

  window.dashboardGo = go;
})();
