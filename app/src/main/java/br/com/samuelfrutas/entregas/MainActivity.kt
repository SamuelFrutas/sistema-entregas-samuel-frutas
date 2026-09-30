package br.com.samuelfrutas.entregas

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import br.com.samuelfrutas.entregas.data.EntregaDbHelper
import br.com.samuelfrutas.entregas.data.EntregaLocal

class MainActivity : Activity() {
    private lateinit var db: EntregaDbHelper
    private lateinit var root: LinearLayout
    private val green=Color.rgb(53,199,89)
    private val bg=Color.rgb(16,24,20)
    private val today get()=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())
    private val money=NumberFormat.getCurrencyInstance(Locale("pt","BR"))
    private var currentEntrega:EntregaLocal?=null

    override fun onCreate(state:Bundle?){super.onCreate(state);db=EntregaDbHelper(this);home()}
    private fun base(title:String):LinearLayout{
        root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg);setPadding(20,18,20,20)}
        val bar=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        val t=TextView(this).apply{text=title;textSize=25f;setTextColor(Color.WHITE);setTypeface(null,Typeface.BOLD);layoutParams=LinearLayout.LayoutParams(0,60,1f)}
        bar.addView(t);bar.addView(Button(this).apply{text="☰";setOnClickListener{menuScreen()}})
        root.addView(bar);setContentView(root);return root
    }
    private fun txt(s:String,size:Float=16f)=TextView(this).apply{text=s;textSize=size;setTextColor(Color.WHITE);setPadding(4,8,4,8)}
    private fun btn(s:String,action:()->Unit)=Button(this).apply{text=s;setTextColor(Color.WHITE);setOnClickListener{action()}}
    private fun add(v:View){root.addView(v,LinearLayout.LayoutParams(-1,LinearLayout.LayoutParams.WRAP_CONTENT))}
    private fun field(hint:String,input:Int=InputType.TYPE_CLASS_TEXT)=EditText(this).apply{this.hint=hint;inputType=input;setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY);setPadding(12,10,12,10)}
    private fun moneyToCents(s:String):Long?=s.replace(".","").replace(",",".").toDoubleOrNull()?.let{(it*100).toLong()}
    private fun centsText(v:Long?)=v?.let{money.format(it/100.0)}?:""
    private fun location(e:EntregaLocal)=if(e.semEndereco)"📍 "+e.enderecoReferencia else "Prédio "+e.predio+" • Bloco "+e.bloco+" • Apt "+e.apartamento

    private fun home(){
        base("Samuel Frutas");add(txt("Sistema de Entregas",18f));add(txt("Hoje • "+today,14f))
        val list=db.listarDia(today);add(txt("Pendentes: "+list.count{!it.realizada}+"   •   Realizadas: "+list.count{it.realizada},17f))
        add(btn("➕ NOVA ENTREGA"){newDelivery()});add(btn("📦 ENTREGAS PENDENTES"){pending()});add(btn("✅ ENTREGAS REALIZADAS"){completed()});add(btn("🔄 SINCRONIZAÇÃO"){syncScreen()})
        add(txt("Os dados são salvos primeiro no aparelho para funcionar offline.",13f))
    }

    private fun newDelivery(){
        base("Nova entrega")
        val noAddress=CheckBox(this).apply{text="Entrega sem endereço";setTextColor(Color.WHITE)}
        val pred=field("Prédio (somente números)",InputType.TYPE_CLASS_NUMBER)
        val bloco=field("Bloco (somente números)",InputType.TYPE_CLASS_NUMBER)
        val alphaBlock=CheckBox(this).apply{text="ABC — permitir letras no bloco";setTextColor(Color.WHITE)}
        val ap=field("Apartamento (somente números)",InputType.TYPE_CLASS_NUMBER)
        val ref=field("Endereço / referência")
        val value=field("Valor da compra (ex.: 35,00)",InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        add(txt("Localização",17f));add(pred);add(bloco);add(alphaBlock);add(ap);add(noAddress);add(ref)
        alphaBlock.setOnCheckedChangeListener{_,checked->bloco.inputType=if(checked)InputType.TYPE_CLASS_TEXT else InputType.TYPE_CLASS_NUMBER}
        add(txt("Pagamento informado antes da entrega",17f))
        val group=RadioGroup(this)
        val paid=RadioButton(this).apply{text="Pago adiantado";setTextColor(Color.WHITE)}
        val unpaid=RadioButton(this).apply{text="Não pago";setTextColor(Color.WHITE)}
        val unknown=RadioButton(this).apply{text="Não informado";setTextColor(Color.WHITE)}
        group.addView(paid);group.addView(unpaid);group.addView(unknown);unknown.isChecked=true
        add(group);add(value)
        fun refresh(){val a=noAddress.isChecked;pred.isEnabled=!a;bloco.isEnabled=!a;ap.isEnabled=!a;ref.isEnabled=a;value.isEnabled=!paid.isChecked;value.visibility=if(paid.isChecked)View.GONE else View.VISIBLE}
        noAddress.setOnCheckedChangeListener{_,_->refresh()};group.setOnCheckedChangeListener{_,_->refresh()};refresh()
        add(btn("Continuar"){
            if(!noAddress.isChecked&&(pred.text.isBlank()||bloco.text.isBlank()||ap.text.isBlank())){toast("Preencha prédio, bloco e apartamento.");return@btn}
            if(noAddress.isChecked&&ref.text.isBlank()){toast("Informe o endereço/referência.");return@btn}
            val initial=when(group.checkedRadioButtonId){paid.id->"PAGO_ADIANTADO";unpaid.id->"NAO_PAGO";else->"NAO_INFORMADO"}
            val cents=if(initial=="PAGO_ADIANTADO")null else moneyToCents(value.text.toString())
            if(initial=="NAO_PAGO"&&cents==null){toast("Para 'Não pago', informe o valor da compra.");return@btn}
            currentEntrega=EntregaLocal(dia=today,predio=pred.text.toString(),bloco=bloco.text.toString(),apartamento=ap.text.toString(),semEndereco=noAddress.isChecked,enderecoReferencia=ref.text.toString(),valorCompraCentavos=cents,pagamentoInicial=initial)
            review()
        })
    }

    private fun review(){
        val e=currentEntrega?:return
        base("Confirmar entrega");add(txt("Confira antes de salvar",18f));add(txt(location(e)))
        add(txt("Pagamento inicial: "+labelInitial(e.pagamentoInicial)))
        add(txt("Valor da compra: "+centsText(e.valorCompraCentavos).ifBlank{"não informado"}))
        add(btn("💾 SALVAR ENTREGA"){db.inserir(e);pending()});add(btn("← Voltar"){newDelivery()})
    }

    private fun pending(){
        base("Entregas pendentes");val list=db.listarDia(today).filter{!it.realizada}
        if(list.isEmpty())add(txt("Nenhuma entrega pendente.",18f))
        list.forEach{e->add(btn(location(e)){finish(e)})};add(btn("➕ Nova entrega"){newDelivery()})
    }

    private fun completed(){
        base("Entregas realizadas");val list=db.listarDia(today).filter{it.realizada}
        if(list.isEmpty())add(txt("Nenhuma entrega realizada.",18f))
        list.forEach{e->add(txt(location(e)+"\n"+labelResult(e.resultadoPagamento)+(if(e.formaPagamento.isNotBlank())" • "+e.formaPagamento else ""),16f))}
    }

    private fun finish(e:EntregaLocal){
        base("Finalizar entrega");add(txt(location(e),18f));add(txt("Valor da compra: "+centsText(e.valorCompraCentavos).ifBlank{"não informado"}))
        val rg=RadioGroup(this);val paid=RadioButton(this).apply{text="Pago";setTextColor(Color.WHITE)};val not=RadioButton(this).apply{text="Não pago";setTextColor(Color.WHITE)}
        rg.addView(paid);rg.addView(not);not.isChecked=true;add(txt("Resultado",17f));add(rg)
        val methods=RadioGroup(this);val cash=RadioButton(this).apply{text="Dinheiro";setTextColor(Color.WHITE)};val card=RadioButton(this).apply{text="Cartão";setTextColor(Color.WHITE)}
        methods.addView(cash);methods.addView(card);cash.isChecked=true;add(methods)
        val tip=field("Caixinha (ex.: 5,00)",InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL);val obs=field("Observação");add(tip);add(obs)
        fun refresh(){methods.visibility=if(paid.isChecked)View.VISIBLE else View.GONE};rg.setOnCheckedChangeListener{_,_->refresh()};refresh()
        add(btn("✅ CONCLUIR"){val result=if(paid.isChecked)"PAGO" else "NAO_PAGO";val form=if(result=="PAGO")if(cash.isChecked)"DINHEIRO" else "CARTAO" else "";db.atualizarFinal(e.id,result,form,moneyToCents(tip.text.toString())?:0,obs.text.toString());completed()})
        add(btn("← Voltar"){pending()})
    }

    private fun syncScreen(){base("Sincronização");add(txt("Status: dados locais aguardando sincronização central.",18f));add(txt("Firebase será conectado na etapa de sincronização.",14f));add(btn("← Voltar"){home()})}
    private fun menuScreen(){base("Menu");add(btn("🏠 Início"){home()});add(btn("📦 Pendentes"){pending()});add(btn("✅ Realizadas"){completed()});add(btn("🔄 Sincronização"){syncScreen()})}
    private fun labelInitial(s:String)=when(s){"PAGO_ADIANTADO"->"Pago adiantado";"NAO_PAGO"->"Não pago";else->"Não informado"}
    private fun labelResult(s:String)=if(s=="PAGO")"Pago" else "Não pago"
    private fun toast(s:String){Toast.makeText(this,s,Toast.LENGTH_SHORT).show()}
    override fun onDestroy(){db.close();super.onDestroy()}
}