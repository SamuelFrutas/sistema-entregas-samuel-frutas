// Corrige o fluxo de cobrança quando o endereço da entrega não encontrou
// automaticamente um contato. A busca já abre preenchida com a chave
// prédio/bloco/apartamento da entrega selecionada, mas continua editável.
(function(){
  window.startCharge = async function(id){
    const e=currentDocs.find(x=>x.id===id);
    if(!e)return;
    if(!googleContactsDbLoaded)await loadGoogleContactsFromDb();

    if(e.semEndereco){
      const nome=String(e.enderecoReferencia||"").trim();
      if(nome){
        const matches=findContactsForName(e);
        if(matches.length>=1){
          openChargeContactPicker(e,nome);
          return;
        }
      }
      openChargeContactPicker(e,nome);
      return;
    }

    const key=deliveryAddressKey(e);
    const matches=findContactsForDelivery(e);
    if(matches.length===1){
      openWhatsappCharge(e,matches[0]);
      return;
    }
    if(matches.length>1){
      openChargeMatchesPicker(e,matches,"Encontramos "+matches.length+" contatos para "+key+".");
      return;
    }

    // Nenhum contato localizado: não abre uma pesquisa vazia.
    // Já entrega ao campo a chave exata desta cobrança.
    openChargeContactPicker(e,key);
  };
})();
