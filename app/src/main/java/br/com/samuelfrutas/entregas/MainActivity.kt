package br.com.samuelfrutas.entregas

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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

    private val green = Color.rgb(53, 199, 89)
    private val bg = Color.rgb(10, 17, 13)
    private val surface = Color.rgb(20, 30, 24)
    private val surface2 = Color.rgb(27, 39, 31)
    private val muted = Color.rgb(158, 171, 162)
    private val today get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private val money = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    private var currentEntrega: EntregaLocal? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        db = EntregaDbHelper(this)
        home()
    }

    private fun rounded(color: Int, radius: Float = 18f, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            stroke?.let { setStroke(1, it) }
        }

    private fun base(title: String): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(20, 14, 20, 24)
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            addView(root)
        }

        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 14)
        }

        val t = TextView(this).apply {
            text = title
            textSize = 26f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, 58, 1f)
            gravity = Gravity.CENTER_VERTICAL
        }

        val menu = TextView(this).apply {
            text = "☰"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(surface2, 16f)
            layoutParams = LinearLayout.LayoutParams(52, 52)
            setOnClickListener { menuScreen() }
        }

        bar.addView(t)
        bar.addView(menu)
        root.addView(bar)
        setContentView(scroll)
        return root
    }

    private fun txt(s: String, size: Float = 16f, color: Int = Color.WHITE) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            setPadding(2, 6, 2, 6)
        }

    private fun cardText(s: String, size: Float = 16f) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(Color.WHITE)
            setPadding(18, 16, 18, 16)
            background = rounded(surface, 18f)
        }

    private fun btn(s: String, action: () -> Unit) =
        TextView(this).apply {
            text = s
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 16, 18, 16)
            background = rounded(surface2, 18f)
            minHeight = 56
            setOnClickListener { action() }
        }

    private fun primaryBtn(s: String, action: () -> Unit) =
        TextView(this).apply {
            text = s
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(18, 16, 18, 16)
            background = rounded(green, 18f)
            minHeight = 56
            setOnClickListener { action() }
        }

    private fun add(v: View, top: Int = 8) {
        root.addView(v, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = top
        })
    }

    private fun field(hint: String, input: Int = InputType.TYPE_CLASS_TEXT) =
        EditText(this).apply {
            this.hint = hint
            inputType = input
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            setPadding(16, 4, 16, 4)
            background = rounded(surface, 16f, Color.rgb(47, 64, 52))
            minHeight = 54
        }

    private fun check(s: String) =
        CheckBox(this).apply {
            text = s
            textSize = 15f
            setTextColor(Color.WHITE)
            buttonTintList = android.content.res.ColorStateList.valueOf(green)
            setPadding(0, 4, 0, 4)
        }

    private fun radio(s: String) =
        RadioButton(this).apply {
            text = s
            textSize = 15f
            setTextColor(Color.WHITE)
            buttonTintList = android.content.res.ColorStateList.valueOf(green)
            setPadding(0, 3, 0, 3)
        }

    private fun moneyToCents(s: String): Long? =
        s.replace(".", "").replace(",", ".").toDoubleOrNull()?.let { (it * 100).toLong() }

    private fun centsText(v: Long?) =
        v?.let { money.format(it / 100.0) } ?: ""

    private fun location(e: EntregaLocal): String {
        if (e.semEndereco) return "📍 " + e.enderecoReferencia
        val parts = mutableListOf<String>()
        if (e.predio.isNotBlank()) parts.add("Prédio ${e.predio}")
        if (e.bloco.isNotBlank()) parts.add("Bloco ${e.bloco}")
        if (e.apartamento.isNotBlank()) parts.add("Apt ${e.apartamento}")
        return parts.joinToString(" • ")
    }

    private fun home() {
        base("Samuel Frutas")
        add(txt("Sistema de Entregas", 16f, muted), 0)
        add(cardText(
            "Hoje\n${today}\n\n" +
            "Pendentes     ${db.listarDia(today).count { !it.realizada }}\n" +
            "Realizadas    ${db.listarDia(today).count { it.realizada }}",
            17f
        ), 14)
        add(primaryBtn("＋  NOVA ENTREGA") { newDelivery() }, 14)
        add(btn("📦  ENTREGAS PENDENTES") { pending() })
        add(btn("✓  ENTREGAS REALIZADAS") { completed() })
        add(btn("↻  SINCRONIZAÇÃO") { syncScreen() })
        add(txt("Os dados são salvos primeiro no aparelho para funcionar offline.", 13f, muted), 14)
    }

    private fun newDelivery() {
        base("Nova entrega")
        add(txt("ENDEREÇO", 13f, green), 0)

        val noAddress = check("Entrega sem endereço")
        val pred = field("Prédio (somente números)", InputType.TYPE_CLASS_NUMBER)
        val bloco = field("Bloco")
        val semBloco = check("Prédio não possui bloco")
        val alphaBlock = check("Permitir letras no bloco")
        val ap = field("Apartamento (somente números)", InputType.TYPE_CLASS_NUMBER)
        val ref = field("Endereço / referência")
        val value = field(
            "Valor da compra (ex.: 35,00)",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        )

        add(pred)
        add(bloco)
        add(semBloco)
        add(alphaBlock, 2)
        add(ap)
        add(noAddress, 8)
        add(ref)

        alphaBlock.setOnCheckedChangeListener { _, checked ->
            if (!semBloco.isChecked) {
                bloco.inputType = if (checked) InputType.TYPE_CLASS_TEXT else InputType.TYPE_CLASS_NUMBER
            }
        }

        semBloco.setOnCheckedChangeListener { _, checked ->
            bloco.isEnabled = !checked
            alphaBlock.isEnabled = !checked
            if (checked) {
                bloco.setText("")
                bloco.hint = "Sem bloco"
            } else {
                bloco.hint = "Bloco"
                bloco.inputType = if (alphaBlock.isChecked) InputType.TYPE_CLASS_TEXT else InputType.TYPE_CLASS_NUMBER
            }
        }

        add(txt("PAGAMENTO INFORMADO ANTES DA ENTREGA", 13f, green), 18)

        val group = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(14, 8, 14, 8)
            background = rounded(surface, 18f)
        }
        val paid = radio("Pago adiantado")
        val unpaid = radio("Não pago")
        val unknown = radio("Não informado")
        group.addView(paid)
        group.addView(unpaid)
        group.addView(unknown)
        unknown.isChecked = true
        add(group, 8)
        add(value, 10)

        fun refresh() {
            val noAddr = noAddress.isChecked
            val withoutBlock = semBloco.isChecked
            pred.isEnabled = !noAddr
            bloco.isEnabled = !noAddr && !withoutBlock
            semBloco.isEnabled = !noAddr
            alphaBlock.isEnabled = !noAddr && !withoutBlock
            ap.isEnabled = !noAddr
            ref.isEnabled = noAddr
            value.isEnabled = !paid.isChecked
            value.visibility = if (paid.isChecked) View.GONE else View.VISIBLE
        }

        noAddress.setOnCheckedChangeListener { _, _ -> refresh() }
        group.setOnCheckedChangeListener { _, _ -> refresh() }
        refresh()

        add(primaryBtn("CONTINUAR  →") {
            if (!noAddress.isChecked) {
                if (pred.text.isBlank() || ap.text.isBlank()) {
                    toast("Preencha prédio e apartamento.")
                    return@primaryBtn
                }
                if (!semBloco.isChecked && bloco.text.isBlank()) {
                    toast("Informe o bloco ou marque 'Prédio não possui bloco'.")
                    return@primaryBtn
                }
            }
            if (noAddress.isChecked && ref.text.isBlank()) {
                toast("Informe o endereço/referência.")
                return@primaryBtn
            }

            val initial = when (group.checkedRadioButtonId) {
                paid.id -> "PAGO_ADIANTADO"
                unpaid.id -> "NAO_PAGO"
                else -> "NAO_INFORMADO"
            }
            val cents = if (initial == "PAGO_ADIANTADO") null else moneyToCents(value.text.toString())
            if (initial == "NAO_PAGO" && cents == null) {
                toast("Para 'Não pago', informe o valor da compra.")
                return@primaryBtn
            }

            currentEntrega = EntregaLocal(
                dia = today,
                predio = pred.text.toString(),
                bloco = if (semBloco.isChecked) "" else bloco.text.toString(),
                apartamento = ap.text.toString(),
                semEndereco = noAddress.isChecked,
                enderecoReferencia = ref.text.toString(),
                valorCompraCentavos = cents,
                pagamentoInicial = initial
            )
            review()
        }, 18)
    }

    private fun review() {
        val e = currentEntrega ?: return
        base("Confirmar entrega")
        add(txt("CONFIRA OS DADOS", 13f, green), 0)
        add(cardText(location(e), 17f), 10)
        val valor = centsText(e.valorCompraCentavos).ifBlank { "não informado" }
        add(cardText(
            "Pagamento inicial: " + labelInitial(e.pagamentoInicial) + "\n" +
            "Valor da compra: " + valor,
            16f
        ))
        add(primaryBtn("SALVAR ENTREGA") {
            db.inserir(e)
            pending()
        }, 16)
        add(btn("←  Voltar") { newDelivery() })
    }

    private fun pending() {
        base("Entregas pendentes")
        val list = db.listarDia(today).filter { !it.realizada }
        if (list.isEmpty()) add(cardText("Nenhuma entrega pendente.", 17f), 12)
        list.forEach { e -> add(btn(location(e)) { finish(e) }, 8) }
        add(primaryBtn("＋  NOVA ENTREGA") { newDelivery() }, 16)
    }

    private fun completed() {
        base("Entregas realizadas")
        val list = db.listarDia(today).filter { it.realizada }
        if (list.isEmpty()) add(cardText("Nenhuma entrega realizada.", 17f), 12)
        list.forEach { e ->
            add(cardText(
                location(e) + "\n" + labelResult(e.resultadoPagamento) +
                    if (e.formaPagamento.isNotBlank()) " • ${e.formaPagamento}" else "",
                16f
            ), 8)
        }
    }

    private fun finish(e: EntregaLocal) {
        base("Finalizar entrega")
        add(cardText(location(e), 17f), 0)
        val valor = centsText(e.valorCompraCentavos).ifBlank { "não informado" }
        add(txt("Valor da compra: " + valor, 15f, muted), 4)

        add(txt("RESULTADO", 13f, green), 18)
        val rg = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(14, 8, 14, 8)
            background = rounded(surface, 18f)
        }
        val paid = radio("Pago")
        val not = radio("Não pago")
        rg.addView(paid)
        rg.addView(not)
        not.isChecked = true
        add(rg, 8)

        val methods = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(14, 8, 14, 8)
            background = rounded(surface, 18f)
        }
        val cash = radio("Dinheiro")
        val card = radio("Cartão")
        methods.addView(cash)
        methods.addView(card)
        cash.isChecked = true
        add(txt("FORMA DE PAGAMENTO", 13f, green), 16)
        add(methods, 8)

        val tip = field("Caixinha (ex.: 5,00)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val obs = field("Observação")
        add(tip)
        add(obs)

        fun refresh() { methods.visibility = if (paid.isChecked) View.VISIBLE else View.GONE }
        rg.setOnCheckedChangeListener { _, _ -> refresh() }
        refresh()

        add(primaryBtn("CONCLUIR ENTREGA") {
            val result = if (paid.isChecked) "PAGO" else "NAO_PAGO"
            val form = if (result == "PAGO") if (cash.isChecked) "DINHEIRO" else "CARTAO" else ""
            db.atualizarFinal(e.id, result, form, moneyToCents(tip.text.toString()) ?: 0, obs.text.toString())
            completed()
        }, 18)
        add(btn("←  Voltar") { pending() })
    }

    private fun syncScreen() {
        base("Sincronização")
        add(cardText("Status\nDados locais aguardando sincronização central.", 17f), 0)
        add(txt("Firebase será conectado na etapa de sincronização.", 14f, muted), 8)
        add(btn("←  Voltar") { home() }, 18)
    }

    private fun menuScreen() {
        base("Menu")
        add(btn("🏠  Início") { home() }, 4)
        add(btn("📦  Pendentes") { pending() })
        add(btn("✓  Realizadas") { completed() })
        add(btn("↻  Sincronização") { syncScreen() })
    }

    private fun labelInitial(s: String) = when (s) {
        "PAGO_ADIANTADO" -> "Pago adiantado"
        "NAO_PAGO" -> "Não pago"
        else -> "Não informado"
    }

    private fun labelResult(s: String) = if (s == "PAGO") "Pago" else "Não pago"

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }
}
