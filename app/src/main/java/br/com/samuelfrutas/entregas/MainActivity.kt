package br.com.samuelfrutas.entregas

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import br.com.samuelfrutas.entregas.data.EntregaDbHelper

class MainActivity : Activity() {
    private val bg = Color.rgb(16, 20, 24)
    private val card = Color.rgb(27, 33, 38)
    private val green = Color.rgb(70, 210, 120)
    private val white = Color.WHITE
    private val muted = Color.rgb(180, 188, 194)
    private lateinit var root: LinearLayout
    data class Delivery(val id:String, val day:String, val location:String, val initialPayment:String, val purchaseValue:String, var finalPayment:String?=null, var paymentMethod:String?=null, var tip:String="", var observation:String="", var completed:Boolean=false)
    private val deliveries=mutableListOf<Delivery>()
    private lateinit var db: EntregaDbHelper
    private var selectedDelivery: Delivery?=null
    private var current: Delivery?=null
    private val today:String get()=java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db=EntregaDbHelper(this)
        deliveries.addAll(db.loadDeliveries(today))
        showHome()
    }

    private fun base(title: String, subtitle: String? = null): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(label(title, 24f, white))
        subtitle?.let { titleBox.addView(label(it, 14f, muted)) }
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label(if (isOnline()) "● ONLINE" else "● OFFLINE", 12f, green))
        root.addView(header)
        return root
    }

    private fun label(text: String, size: Float, color: Int) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); setPadding(0, 4, 0, 8)
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; setTextColor(white); setBackgroundColor(Color.rgb(42, 52, 58))
        setOnClickListener { action() }; minimumHeight = 52
    }

    private fun primary(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; setTextColor(Color.BLACK); setBackgroundColor(green)
        setOnClickListener { action() }; minimumHeight = 56
    }

    private fun card(title: String, body: String): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18,18,18,18); setBackgroundColor(card) }
        box.addView(label(title,18f,white)); box.addView(label(body,14f,muted)); return box
    }

    private fun addGap(p: LinearLayout, h: Int = 12) { p.addView(Space(this), LinearLayout.LayoutParams(1,h)) }

    private fun edit(hint: String, numeric: Boolean = false) = EditText(this).apply {
        this.hint = hint; setHintTextColor(Color.rgb(125,135,142)); setTextColor(white); setSingleLine(true); setPadding(14,4,14,4)
        if (numeric) inputType = InputType.TYPE_CLASS_NUMBER
    }

    private fun finish(view: LinearLayout) { setContentView(view) }

    private fun showHome() {
        val v=base("Samuel Frutas","Entregas de hoje"); addGap(v)
        v.addView(card("ENTREGAS PENDENTES","0 entregas aguardando realização.")); addGap(v)
        v.addView(button("VER PENDENTES"){showPending()}); addGap(v)
        v.addView(card("ENTREGAS REALIZADAS","0 entregas concluídas hoje.")); addGap(v)
        v.addView(button("VER REALIZADAS"){showCompleted()}); addGap(v)
        v.addView(card("STATUS","Aplicativo preparado para operação offline.")); addGap(v)
        v.addView(button("SINCRONIZAÇÃO"){showSync()}); addGap(v,20)
        v.addView(primary("+ NOVA ENTREGA"){showNewDelivery()}); addGap(v); v.addView(button("☰ MENU"){showMenu()}); finish(v)
    }

    private fun showNewDelivery() {
        val v=base("Nova entrega","Cadastro da entrega"); addGap(v)
        v.addView(label("Prédio",14f,muted)); val predio=edit("Somente números",true); v.addView(predio)
        v.addView(label("Bloco",14f,muted)); val bloco=edit("Número inicialmente",true); v.addView(bloco)
        var blocoLetras=false
        v.addView(button("ABC — permitir letras no bloco"){ blocoLetras=!blocoLetras; bloco.inputType=if(blocoLetras) InputType.TYPE_CLASS_TEXT else InputType.TYPE_CLASS_NUMBER; bloco.hint=if(blocoLetras) "Ex.: E ou E13" else "Somente números" })
        v.addView(label("Apartamento",14f,muted)); val apto=edit("Somente números",true); v.addView(apto)
        addGap(v); val semEndereco=CheckBox(this).apply{text="Entrega sem endereço";setTextColor(white)}; v.addView(semEndereco)
        val endereco=edit("Endereço / referência")
        v.addView(endereco)
        semEndereco.setOnCheckedChangeListener { _, checked ->
            endereco.isEnabled=checked
            endereco.hint=if(checked) "Obrigatório: endereço / referência" else "Endereço / referência (opcional)"
        }
        endereco.isEnabled=false
        addGap(v); v.addView(label("Pagamento inicial",14f,muted))
        val payment=RadioGroup(this)
        val rbAd=RadioButton(this).apply{text="Pago adiantado";setTextColor(white);id=1001}
        val rbNp=RadioButton(this).apply{text="Não pago";setTextColor(white);id=1002}
        val rbNi=RadioButton(this).apply{text="Não informado";setTextColor(white);id=1003}
        payment.addView(rbAd);payment.addView(rbNp);payment.addView(rbNi);payment.check(rbNi.id);v.addView(payment)
        addGap(v)
        v.addView(label("Valor da compra",14f,muted))
        val valor=edit("R$ 0,00")
        v.addView(valor)
        val valorInfo=label("Opcional para Não informado.",12f,muted); v.addView(valorInfo)
        fun updateValueRule() {
            when (payment.checkedRadioButtonId) {
                rbAd.id -> { valor.isEnabled=false; valor.setText(""); valorInfo.text="Pago adiantado: valor não é necessário." }
                rbNp.id -> { valor.isEnabled=true; valorInfo.text="Não pago: valor da compra é obrigatório." }
                else -> { valor.isEnabled=true; valorInfo.text="Não informado: valor da compra é opcional." }
            }
        }
        payment.setOnCheckedChangeListener { _, _ -> updateValueRule() }; updateValueRule()
        addGap(v)
        v.addView(primary("CONTINUAR"){ 
            val addressOk = if (semEndereco.isChecked) {
                endereco.text.toString().trim().isNotEmpty()
            } else {
                predio.text.toString().trim().isNotEmpty() &&
                bloco.text.toString().trim().isNotEmpty() &&
                apto.text.toString().trim().isNotEmpty()
            }
            val valueOk = !rbNp.isChecked || valor.text.toString().trim().isNotEmpty()
            when {
                !addressOk -> Toast.makeText(this,"Preencha prédio, bloco e apartamento ou marque 'Entrega sem endereço'.",Toast.LENGTH_SHORT).show()
                !valueOk -> Toast.makeText(this,"Para 'Não pago', informe o valor da compra.",Toast.LENGTH_SHORT).show()
                else -> {\n                    val local=if(semEndereco.isChecked) endereco.text.toString().trim() else "Prédio "+predio.text+" • Bloco "+bloco.text+" • Apto "+apto.text\n                    current=Delivery(java.util.UUID.randomUUID().toString(),today,local,when(payment.checkedRadioButtonId){rbAd.id->"Pago adiantado";rbNp.id->"Não pago";else->"Não informado"},valor.text.toString().trim())\n                    showReview()\n                }
            }
        })
        addGap(v); v.addView(button("← VOLTAR"){showHome()}); finish(v)
    }

    private fun showReview(){
        val d=current ?: return showHome()
        val v=base("Revisar entrega","Confira antes de salvar");addGap(v)
        v.addView(card("LOCAL",d.location));addGap(v)
        v.addView(card("PAGAMENTO INICIAL",d.initialPayment+(if(d.purchaseValue.isNotBlank())"\\nValor: "+d.purchaseValue else "")));addGap(v)
        v.addView(primary("SALVAR COMO PENDENTE"){
            deliveries.add(d)
            db.saveDelivery(d)
            current=null
            showPending()
        })
        addGap(v);v.addView(button("← VOLTAR"){showNewDelivery()});finish(v)
    }

    private fun showFinalPayment() {
        val selected=selectedDelivery ?: return showPending()
        val v=base("Registrar entrega","Resultado final");addGap(v)
        val result=RadioGroup(this)
        val pago=RadioButton(this).apply{text="Pago";setTextColor(white);id=2001}
        val nao=RadioButton(this).apply{text="Não pago";setTextColor(white);id=2002}
        result.addView(pago);result.addView(nao);result.check(pago.id);v.addView(result)
        v.addView(label("Forma de pagamento",14f,muted))
        val forma=RadioGroup(this)
        val dinheiro=RadioButton(this).apply{text="Dinheiro";setTextColor(white);id=3001}
        val cartao=RadioButton(this).apply{text="Cartão";setTextColor(white);id=3002}
        forma.addView(dinheiro);forma.addView(cartao);forma.check(dinheiro.id);v.addView(forma)
        val caixinha=edit("Caixinha (R$ 0,00)")
        val obs=edit("Observação")
        v.addView(label("Caixinha",14f,muted));v.addView(caixinha)
        v.addView(label("Observação",14f,muted));v.addView(obs)
        result.setOnCheckedChangeListener{_,id->
            val paid=id==pago.id
            forma.isEnabled=paid;dinheiro.isEnabled=paid;cartao.isEnabled=paid
            if(!paid) forma.clearCheck()
        }
        addGap(v);v.addView(primary("CONTINUAR"){
            selected.finalPayment=if(pago.isChecked)"Pago" else "Não pago"
            selected.paymentMethod=if(pago.isChecked){if(dinheiro.isChecked)"Dinheiro" else "Cartão"}else null
            selected.tip=caixinha.text.toString().trim()
            selected.observation=obs.text.toString().trim()
            showConfirmation()
        })
        addGap(v);v.addView(button("← VOLTAR"){showPending()});finish(v)
    }

    private fun showConfirmation(){
        val d=selectedDelivery ?: return showPending()
        val v=base("Confirmar entrega","Confira o resultado antes de concluir");addGap(v)
        v.addView(card("LOCAL",d.location));addGap(v)
        v.addView(card("RESULTADO",(d.finalPayment ?: "")+(if(d.paymentMethod!=null)"\\nForma: "+d.paymentMethod else "")));addGap(v)
        v.addView(card("CAIXINHA / OBSERVAÇÃO",(if(d.tip.isBlank())"R$ 0,00" else d.tip)+"\\n"+(if(d.observation.isBlank())"Sem observação" else d.observation)));addGap(v)
        v.addView(primary("CONFIRMAR COMO REALIZADA"){
            d.completed=true
            db.saveDelivery(d)
            selectedDelivery=null
            showCompleted()
        })
        addGap(v);v.addView(button("← VOLTAR"){showFinalPayment()});finish(v)
    }

    private fun showPending(){
        val list=deliveries.filter{it.day==today&&!it.completed}
        val v=base("Entregas pendentes","Hoje");addGap(v)
        if(list.isEmpty()) v.addView(card("Nenhuma entrega pendente","Todas as entregas de hoje foram realizadas."))
        else list.forEach{d->
            v.addView(card("ENTREGA PENDENTE",d.location+"\\nPagamento inicial: "+d.initialPayment))
            addGap(v,8)
            v.addView(primary("REALIZAR ENTREGA"){selectedDelivery=d;showFinalPayment()})
            addGap(v)
        }
        addGap(v);v.addView(primary("+ NOVA ENTREGA"){showNewDelivery()});addGap(v);v.addView(button("← INÍCIO"){showHome()});finish(v)
    }

    private fun showCompleted(){
        val list=deliveries.filter{it.day==today&&it.completed}
        val v=base("Entregas realizadas","Hoje");addGap(v)
        if(list.isEmpty()) v.addView(card("Nenhuma entrega realizada","As entregas concluídas aparecerão aqui."))
        else list.forEach{d->
            v.addView(card("REALIZADA",d.location+"\\nResultado: "+(d.finalPayment ?: "")+(if(d.paymentMethod!=null)" • "+d.paymentMethod else "")))
            addGap(v,8)
        }
        addGap(v);v.addView(button("← INÍCIO"){showHome()});finish(v)
    }

    private fun showSync(){
        val v=base("Sincronização","Operação offline-first");addGap(v)
        val pendingCount=deliveries.size
        v.addView(card("MODO",if(isOnline())"Online — dados continuam sendo gravados primeiro no SQLite." else "Offline — as entregas ficam armazenadas no aparelho."))
        addGap(v)
        v.addView(card("REGISTROS LOCAIS",pendingCount.toString()+" entrega(s) carregada(s) para hoje."))
        addGap(v)
        v.addView(card("SEGURANÇA","Nenhum registro local é apagado neste bloco. O status permanece PENDENTE até a futura sincronização com Firebase."))
        addGap(v);v.addView(button("← INÍCIO"){showHome()});finish(v)
    }

    private fun isOnline():Boolean{
        val cm=getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n=cm.activeNetwork ?: return false
        val caps=cm.getNetworkCapabilities(n) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    private fun showMenu(){val v=base("Menu","Sistema de Entregas");addGap(v);v.addView(button("ENTREGAS PENDENTES"){showPending()});addGap(v);v.addView(button("ENTREGAS REALIZADAS"){showCompleted()});addGap(v);v.addView(button("SINCRONIZAÇÃO"){showSync()});addGap(v);v.addView(button("← INÍCIO"){showHome()});finish(v)}
}
