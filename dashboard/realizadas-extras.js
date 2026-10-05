(function(){
  function formatDate(key){
    const p=String(key||'').split('-');
    return p.length===3 ? p[2]+'/'+p[1]+'/'+p[0] : String(key||'');
  }
  function normalize(v){
    return String(v||'').trim().toLocaleLowerCase('pt-BR').normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/\s+/g,' ');
  }
  function sameDelivery(a,b){
    if(!a||!b) return false;
    return normalize(formatEndereco(a))===normalize(formatEndereco(b));
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
        const candidates=source.filter(e=>normalize(formatEndereco(e))===rowAddress);
        const matched=candidates.length===1 ? candidates[0] : (candidates[0] || source[i]);
        const obs=String(matched?.observacao||'').trim();
        if(!obs) return;
        const el=document.createElement('div');
        el.className='web-observation';
        el.innerHTML='<span class="web-observation-icon">⚠️</span><div><strong>Observação</strong><span class="web-observation-text"></span></div>';
        el.querySelector('.web-observation-text').textContent=obs;
        row.appendChild(el);
      });
    });
  }
  const oldShowDone=window.showDone;
  if(typeof oldShowDone==='function'){
    window.showDone=function(){
      oldShowDone.apply(this,arguments);
      setTimeout(decorate,0);
      setTimeout(decorate,150);
      setTimeout(decorate,500);
    };
  }
  const style=document.createElement('style');
  style.textContent='.history-day .delivery-row{flex-wrap:wrap;align-items:center}.history-day .web-observation{flex:1 1 100%;width:100%;min-width:0;display:flex;align-items:flex-start;gap:10px;margin-top:2px;padding:10px 12px;border-radius:10px;background:rgba(255,196,0,.08);border:1px solid rgba(255,213,74,.22);border-left:4px solid #ffd54a;color:#ffd54a;font-size:13px;font-weight:700;line-height:1.35;box-sizing:border-box}.web-observation-icon{flex:0 0 auto;font-size:16px;line-height:1.35}.web-observation>div{min-width:0}.web-observation strong{display:block;color:#ffd54a;font-size:12px;margin:0 0 2px;letter-spacing:.02em}.web-observation-text{display:block;color:#ffe68a;font-weight:600;white-space:normal;overflow-wrap:anywhere;word-break:normal}@media(max-width:800px){.history-day .web-observation{margin-top:3px;padding:10px 11px;font-size:13px}.web-observation strong{font-size:12px}.web-observation-icon{font-size:15px}}';
  document.head.appendChild(style);
  setTimeout(decorate,100);
  setTimeout(decorate,500);
})();
