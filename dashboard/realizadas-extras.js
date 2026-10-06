(function(){
  function formatDate(key){
    const p=String(key||'').split('-');
    return p.length===3 ? p[2]+'/'+p[1]+'/'+p[0] : String(key||'');
  }
  function normalize(v){
    return String(v||'').trim().toLocaleLowerCase('pt-BR').normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/\s+/g,' ');
  }
  function decorate(){
    const title=document.getElementById('pageTitle');
    if(!title || normalize(title.textContent)!=='realizadas') return;
    const docs=typeof currentDocs!=='undefined' ? currentDocs : [];
    document.querySelectorAll('.history-day').forEach(group=>{
      const dateLabel=group.querySelector('.history-day-head small')?.textContent?.trim()||'';
      const source=docs.filter(e=>e && e.realizada && e.dia && formatDate(e.dia)===dateLabel);
      const rows=group.querySelectorAll('.delivery-row');
      rows.forEach((row,i)=>{
        row.querySelectorAll('.web-observation').forEach(el=>el.remove());
        const strong=row.querySelector('strong');
        const rowAddress=normalize(strong?.textContent||'');
        const matched=source.find(e=>normalize(formatEndereco(e))===rowAddress) || source[i];
        const obs=String(matched?.observacao||'').trim();
        if(!obs) return;

        // A observação pertence às informações da entrega.
        // Colocamos diretamente na coluna esquerda, logo abaixo do status/forma de pagamento.
        const info=row.querySelector(':scope > div:first-child');
        if(!info) return;
        const el=document.createElement('div');
        el.className='web-observation';
        el.textContent='⚠️ Observação: '+obs;
        info.appendChild(el);
      });
    });
  }
  const oldShowDone=window.showDone;
  if(typeof oldShowDone==='function'){
    window.showDone=function(){
      oldShowDone.apply(this,arguments);
      setTimeout(decorate,0);
      setTimeout(decorate,150);
    };
  }
  const style=document.createElement('style');
  style.textContent='.history-day .delivery-row .web-observation{display:block;width:100%;box-sizing:border-box;margin-top:7px;padding:7px 0 0;border-top:1px solid rgba(255,213,74,.18);color:#ffd54a;font-size:13px;font-weight:700;line-height:1.35;word-break:break-word}.history-day .delivery-row .web-observation::first-letter{font-size:15px}';
  document.head.appendChild(style);
  setTimeout(decorate,100);
  setTimeout(decorate,500);
})();
