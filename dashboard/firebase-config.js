// Configuração pública do Firebase Web App. Nunca coloque senha ou chave privada aqui.
window.FIREBASE_CONFIG={apiKey:"",authDomain:"",projectId:"",storageBucket:"",messagingSenderId:"",appId:""};
(function(){
  const h=document.querySelector("header"),t=h&&h.querySelector("h1"),s=document.querySelector("main section");
  if(t)t.id="pageTitle";
  if(s)s.id="content";
  const pages=["home","deliveries","completed","finance","settings"];
  document.querySelectorAll("nav button").forEach((b,i)=>{if(pages[i])b.dataset.page=pages[i];});
})();