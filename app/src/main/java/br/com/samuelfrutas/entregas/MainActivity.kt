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

    private val green = Color.rgb(24, 245, 126)
    private val bg = Color.rgb(3, 14, 24)
    private val surface = Color.rgb(7, 28, 43)
    private val surface2 = Color.rgb(11, 38, 56)
    private val muted = Color.rgb(167, 191, 207)
    private val panel = surface
    private val panel2 = surface2
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

    private fun base(title: String, back: (() -> Unit)? = null) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(18, 10, 18, 22)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            addView(root)
        }
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 10)
        }
        if (back != null) {
            bar.addView(TextView(this).apply {
                text = "‹"
                textSize = 34f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(46, 54)
                setOnClickListener { back() }
            })
        }
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, 58, 1f)
            gravity = Gravity.CENTER_VERTICAL
        }
        titleBox.addView(TextView(this).apply {
            text = title
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        titleBox.addView(TextView(this).apply {
            text = if (back == null) "SAMUEL FRUTAS • ENTREGADOR" else "MODO ENTREGADOR • OFFLINE"
            textSize = 10f
            setTextColor(muted)
        })
        bar.addView(titleBox)
        bar.addView(TextView(this).apply {
            text = "☰"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(surface2, 14f, Color.rgb(35, 76, 102))
            layoutParams = LinearLayout.LayoutParams(50, 50)
            setOnClickListener { menuScreen() }
        })
        root.addView(bar)
        setContentView(scroll)
    }

    private fun txt(s: String, size: Float = 15f, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(2, 5, 2, 5)
        }

    private fun section(s: String) = txt(s.uppercase(Locale.getDefault()), 11f, green, true)

    private fun card(s: String, size: Float = 15f, bgColor: Int = panel) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(Color.WHITE)
            setPadding(16, 14, 16, 14)
            background = rounded(bgColor, 14f, Color.rgb(35, 76, 102))
        }

    private fun btn(s: String, action: () -> Unit) =
        TextView(this).apply {
            text = s
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 13, 16, 13)
            background = rounded(panel2, 14f, Color.rgb(35, 76, 102))
            minHeight = 54
            setOnClickListener { action() }
        }

    private fun primary(s: String, action: () -> Unit) =
        TextView(this).apply {
            text = s
            textSize = 15f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(16, 13, 16, 13)
            background = rounded(green, 14f)
            minHeight = 54
            setOnClickListener { action() }
        }

    private fun danger(s: String, action: () -> Unit) =
        TextView(this).apply {
            text = s
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(16, 13, 16, 13)
            background = rounded(Color.rgb(175, 35, 42), 14f)
            minHeight = 50
            setOnClickListener { action() }
        }

    private fun stepBar(active: Int) {
        val box = LinearLayout(this).apply {
            background = rounded(panel, 13f, Color.rgb(35, 76, 102))
            setPadding(8, 8, 8, 8)
            gravity = Gravity.CENTER
        }
        val labels = listOf("Local", "Pagamento", "Revisão")
        labels.forEachIndexed { i, label ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, 54, 1f)
            }
            item.addView(TextView(this).apply {
                text = (i + 1).toString()
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(if (i + 1 <= active) Color.BLACK else Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                background = rounded(if (i + 1 <= active) green else panel2, 50f, if (i + 1 <= active) green else Color.rgb(35, 76, 102))
                layoutParams = LinearLayout.LayoutParams(28, 28)
            })
            item.addView(TextView(this).apply {
                text = label
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(if (i + 1 == active) green else muted)
            })
            box.addView(item)
        }
        add(box, 0)
    }

    private fun statusChip(s: String, color: Int) =
        TextView(this).apply {
            text = s
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(10, 5, 10, 5)
            background = rounded(color, 10f)
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
            setPadding(14, 4, 14, 4)
            background = rounded(panel2, 12f, Color.rgb(35, 76, 102))
            minHeight = 54
        }

    private fun check(s: String) =
        CheckBox(this).apply {
            text = s
            textSize = 14f
            setTextColor(Color.WHITE)
            buttonTintList = android.content.res.ColorStateList.valueOf(green)
            setPadding(0, 3, 0, 3)
        }

    private fun radio(s: String) =
        RadioButton(this).apply {
            text = s
            textSize = 14f
            setTextColor(Color.WHITE)
            buttonTintList = android.content.res.ColorStateList.valueOf(green)
            setPadding(8, 5, 8, 5)
            minHeight = 58
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
        base("Tela Inicial")
        val list = db.listarDia(today)
        val pendingCount = list.count { !it.realizada }
        val doneCount = list.count { it.realizada }

        val head = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(panel, 16f, Color.rgb(35, 76, 102))
            setPadding(12, 8, 12, 8)
        }
        head.addView(TextView(this).apply {
            text = "🍎"
            textSize = 32f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(50, 56)
        })
        val logo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        logo.addView(txt("SAMUEL", 20f, Color.WHITE, true))
        logo.addView(txt("FRUTAS", 11f, green, true))
        head.addView(logo)
        head.addView(txt("🚚  MODO\nENTREGADOR", 10f, green, true))
        add(head, 0)

        val mode = card("🚚  MODO ENTREGADOR\nFuncionando offline", 15f, Color.rgb(4, 75, 48))
        mode.background = rounded(Color.rgb(4, 75, 48), 14f, green)
        add(mode, 10)

        val grid = LinearLayout(this)
        val pending = card("☷\n\nENTREGAS\nPENDENTES\n" + pendingCount, 14f)
        pending.gravity = Gravity.CENTER
        pending.setOnClickListener { pending() }
        val done = card("✓\n\nENTREGAS\nREALIZADAS\n" + doneCount, 14f)
        done.gravity = Gravity.CENTER
        done.setOnClickListener { completed() }
        grid.addView(pending, LinearLayout.LayoutParams(0, 142, 1f).apply { rightMargin = 5 })
        grid.addView(done, LinearLayout.LayoutParams(0, 142, 1f).apply { leftMargin = 5 })
        add(grid, 10)

        val newBox = primary("＋\nNOVA ENTREGA") { newDelivery() }
        newBox.textSize = 17f
        newBox.minHeight = 102
        add(newBox, 10)

        val day = card("▥  MEU DIA\n" + list.size + " entregas registradas hoje", 14f, panel2)
        day.setOnClickListener { myDay() }
        add(day, 10)

        val sync = card("☁  SINCRONIZAÇÃO\nDados locais prontos para sincronizar quando a internet voltar.", 13f)
        sync.setOnClickListener { syncScreen() }
        add(sync, 10)

        add(card("⌁  MODO OFFLINE\nO aplicativo continua funcionando sem internet.", 13f, panel2), 10)
    }

    private fun newDelivery() {
        base("Nova Entrega", { home() })
        stepBar(1)
        add(section("Local da entrega"), 12)

        val pred = field("Prédio *", InputType.TYPE_CLASS_NUMBER)
        val bloco = field("Bloco *")
        val semBloco = check("Prédio não possui bloco")
        val alphaBlock = check("Permitir letras no bloco")
        val ap = field("Apartamento *", InputType.TYPE_CLASS_NUMBER)
        val noAddress = check("Entrega sem endereço")
        val ref = field("Endereço / referência *")

        add(pred); add(bloco); add(semBloco, 2); add(alphaBlock, 0); add(ap); add(noAddress, 8); add(ref)

        alphaBlock.setOnCheckedChangeListener { _, checked ->
            if (!semBloco.isChecked) bloco.inputType = if (checked) InputType.TYPE_CLASS_TEXT else InputType.TYPE_CLASS_NUMBER
        }
        semBloco.setOnCheckedChangeListener { _, checked ->
            bloco.isEnabled = !checked && !noAddress.isChecked
            alphaBlock.isEnabled = !checked && !noAddress.isChecked
            if (checked) { bloco.setText(""); bloco.hint = "Sem bloco" }
            else {
                bloco.hint = "Bloco *"
                bloco.inputType = if (alphaBlock.isChecked) InputType.TYPE_CLASS_TEXT else InputType.TYPE_CLASS_NUMBER
            }
        }
        fun refresh() {
            val no = noAddress.isChecked
            pred.isEnabled = !no
            bloco.isEnabled = !no && !semBloco.isChecked
            semBloco.isEnabled = !no
            alphaBlock.isEnabled = !no && !semBloco.isChecked
            ap.isEnabled = !no
            ref.isEnabled = no
        }
        noAddress.setOnCheckedChangeListener { _, _ -> refresh() }
        refresh()

        add(card("ⓘ  Sem bloco? Marque 'Prédio não possui bloco' e continue.\nSem endereço? Marque a opção e informe uma referência.", 12f, panel2), 10)
        add(primary("CONTINUAR   ›") {
            if (!noAddress.isChecked) {
                if (pred.text.isBlank() || ap.text.isBlank()) { toast("Preencha prédio e apartamento."); return@primary }
                if (!semBloco.isChecked && bloco.text.isBlank()) { toast("Informe o bloco ou marque 'Prédio não possui bloco'."); return@primary }
            }
            if (noAddress.isChecked && ref.text.isBlank()) { toast("Informe o endereço/referência."); return@primary }
            currentEntrega = EntregaLocal(
                dia = today,
                predio = pred.text.toString(),
                bloco = if (semBloco.isChecked) "" else bloco.text.toString(),
                apartamento = ap.text.toString(),
                semEndereco = noAddress.isChecked,
                enderecoReferencia = ref.text.toString(),
                valorCompraCentavos = null,
                pagamentoInicial = "NAO_INFORMADO"
            )
            paymentStep()
        }, 14)
    }

    private fun paymentStep() {
        val baseEntrega = currentEntrega ?: return
        base("Nova Entrega", { newDelivery() })
        stepBar(2)
        add(section("Situação do pagamento"), 12)

        val group = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            background = rounded(panel, 14f, Color.rgb(35, 76, 102))
            setPadding(8, 4, 8, 4)
        }
        val paid = radio("✓  Pago adiantado\n     Cliente já pagou. Não precisa informar o valor.")
        val unpaid = radio("●  Não pago\n     Cliente vai pagar na entrega. É obrigatório informar o valor.")
        val unknown = radio("○  Não informado\n     Não tenho o valor da entrega.")
        group.addView(paid); group.addView(unpaid); group.addView(unknown); unknown.isChecked = true
        add(group)

        add(section("Valor da compra"), 14)
        val value = field("R$ 0,00", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        add(value)
        add(card("ⓘ  'Não pago' exige o valor. 'Não informado' permite deixar em branco.", 12f, panel2), 8)

        fun refresh() {
            value.isEnabled = !paid.isChecked
            value.hint = if (paid.isChecked) "Não necessário" else if (unpaid.isChecked) "Valor da compra *" else "Valor da compra (opcional)"
        }
        group.setOnCheckedChangeListener { _, _ -> refresh() }
        refresh()

        add(primary("CONTINUAR   ›") {
            val initial = when (group.checkedRadioButtonId) {
                paid.id -> "PAGO_ADIANTADO"
                unpaid.id -> "NAO_PAGO"
                else -> "NAO_INFORMADO"
            }
            val cents = if (initial == "PAGO_ADIANTADO") null else moneyToCents(value.text.toString())
            if (initial == "NAO_PAGO" && cents == null) { toast("Para 'Não pago', informe o valor da compra."); return@primary }
            currentEntrega = baseEntrega.copy(valorCompraCentavos = cents, pagamentoInicial = initial)
            review()
        }, 14)
    }

    private fun review() {
        val e = currentEntrega ?: return
        base("Nova Entrega", { paymentStep() })
        stepBar(3)
        add(section("Resumo da entrega"), 12)
        val address = if (e.semEndereco) e.enderecoReferencia else
            "Prédio: " + e.predio + "\n" +
            (if (e.bloco.isNotBlank()) "Bloco: " + e.bloco + "\n" else "Bloco: sem bloco\n") +
            "Apartamento: " + e.apartamento
        add(card("⌖  " + address, 15f), 6)
        add(card("◉  " + labelInitial(e.pagamentoInicial) + "\nValor da compra: " +
            centsText(e.valorCompraCentavos).ifBlank { "Não informado" }, 15f), 8)
        add(card("✓  A entrega será salva no aparelho e poderá ser feita sem internet.", 12f, panel2), 8)
        val row = LinearLayout(this)
        row.addView(btn("EDITAR") { paymentStep() }, LinearLayout.LayoutParams(0, 54, 1f).apply { rightMargin = 5 })
        row.addView(primary("✓  SALVAR ENTREGA") { db.inserir(e); confirmation() },
            LinearLayout.LayoutParams(0, 54, 1f).apply { leftMargin = 5 })
        add(row, 14)
    }

    private fun confirmation() {
        base("Entrega registrada!", { pending() })
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(18, 35, 18, 35)
            background = rounded(panel, 18f, green)
        }
        box.addView(TextView(this).apply { text = "✓"; textSize = 64f; gravity = Gravity.CENTER; setTextColor(green) })
        box.addView(txt("Entrega registrada!", 23f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        box.addView(txt("A entrega foi salva no aparelho e será sincronizada quando tiver internet.", 14f, muted).apply {
            gravity = Gravity.CENTER; setPadding(20, 12, 20, 20)
        })
        add(box, 25)
        add(btn("VER PRÓXIMAS ENTREGAS") { pending() }, 12)
        add(primary("VOLTAR PARA PENDENTES") { pending() }, 10)
    }

    private fun pending() {
        base("Entregas Pendentes", { home() })
        val list = db.listarDia(today).filter { !it.realizada }
        add(card("●  ENTREGAS PENDENTES    " + list.size, 14f, Color.rgb(5, 53, 78)), 0)
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val search = field("⌕  Buscar por prédio, bloco, apto...")
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                holder.removeAllViews()
                val q = s?.toString().orEmpty()
                list.filter { location(it).contains(q, true) }.forEach { holder.addView(makePendingCard(it)) }
                if (holder.childCount == 0) holder.addView(card("Nenhuma entrega encontrada.", 14f))
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        add(search, 8); add(holder)
        list.forEach { holder.addView(makePendingCard(it)) }
        if (list.isEmpty()) holder.addView(card("Nenhuma entrega pendente.", 15f))
        add(primary("＋  NOVA ENTREGA") { newDelivery() }, 14)
    }

    private fun makePendingCard(e: EntregaLocal): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(panel, 14f, Color.rgb(35, 76, 102))
            setPadding(13, 9, 13, 9)
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(txt("⌖", 25f, Color.rgb(204, 151, 255)).apply {
            gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(38, 50)
        })
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        info.addView(txt(location(e), 16f, Color.WHITE, true))
        info.addView(txt(centsText(e.valorCompraCentavos).ifBlank { "Valor não informado" }, 13f))
        top.addView(info)
        top.addView(statusChip(labelInitial(e.pagamentoInicial),
            if (e.pagamentoInicial == "NAO_PAGO") Color.rgb(190, 38, 50) else Color.rgb(35, 64, 85)))
        box.addView(top)
        box.addView(primary("ABRIR ENTREGA") { finish(e) }, LinearLayout.LayoutParams(-1, 42).apply { topMargin = 5 })
        return box
    }

    private fun completed() {
        base("Entregas Realizadas", { home() })
        val list = db.listarDia(today).filter { it.realizada }
        add(card("✓  ENTREGAS REALIZADAS    " + list.size, 14f, Color.rgb(10, 67, 78)), 0)
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val search = field("⌕  Buscar por prédio, bloco, apto...")
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                holder.removeAllViews()
                val q = s?.toString().orEmpty()
                list.filter { location(it).contains(q, true) }.forEach { holder.addView(makeCompletedCard(it)) }
                if (holder.childCount == 0) holder.addView(card("Nenhuma entrega encontrada.", 14f))
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        add(search, 8); add(holder)
        list.forEach { holder.addView(makeCompletedCard(it)) }
        if (list.isEmpty()) holder.addView(card("Nenhuma entrega realizada.", 15f))
    }

    private fun makeCompletedCard(e: EntregaLocal): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(panel, 14f, Color.rgb(35, 76, 102))
            setPadding(13, 10, 13, 10)
        }
        box.addView(txt(location(e) + "\n" + centsText(e.valorCompraCentavos).ifBlank { "Valor não informado" }, 15f, Color.WHITE, true))
        val result = if (e.resultadoPagamento == "PAGO") "Pago" else "Não pago"
        val detail = if (e.formaPagamento.isNotBlank()) result + " • " + e.formaPagamento else result
        box.addView(txt(detail, 12f, if (result == "Pago") green else Color.rgb(240, 70, 75), true))
        return box
    }

    private fun finish(e: EntregaLocal) {
        base("Registrar Entrega", { pending() })
        add(card("⌖  " + location(e) + "\nValor da compra: " +
            centsText(e.valorCompraCentavos).ifBlank { "Não informado" }, 16f), 0)
        add(section("Resultado da entrega"), 16)

        val rg = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            background = rounded(panel, 14f, Color.rgb(35, 76, 102))
            setPadding(8, 4, 8, 4)
        }
        val paid = radio("✓  Pago")
        val not = radio("○  Não pago")
        rg.addView(paid); rg.addView(not); not.isChecked = true
        add(rg)

        add(section("Forma de pagamento"), 14)
        val methods = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            background = rounded(panel, 14f, Color.rgb(35, 76, 102))
            setPadding(6, 2, 6, 2)
        }
        val cash = radio("Dinheiro")
        val card = radio("Cartão")
        methods.addView(cash, RadioGroup.LayoutParams(0, 58, 1f))
        methods.addView(card, RadioGroup.LayoutParams(0, 58, 1f))
        cash.isChecked = true
        add(methods)

        add(section("Detalhes"), 14)
        val tip = field("Caixinha (opcional)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val obs = field("Observação (opcional)")
        add(tip); add(obs)

        fun refresh() { methods.visibility = if (paid.isChecked) View.VISIBLE else View.GONE }
        rg.setOnCheckedChangeListener { _, _ -> refresh() }
        refresh()

        add(primary("✓  CONFIRMAR ENTREGA") {
            val result = if (paid.isChecked) "PAGO" else "NAO_PAGO"
            val form = if (result == "PAGO") if (cash.isChecked) "DINHEIRO" else "CARTAO" else ""
            db.atualizarFinal(e.id, result, form, moneyToCents(tip.text.toString()) ?: 0, obs.text.toString())
            confirmation()
        }, 16)
    }

private fun myDay() {
        base("Meu Dia", { home() })
        val list = db.listarDia(today)
        val done = list.count { it.realizada }
        val pendingCount = list.count { !it.realizada }
        val tips = list.filter { it.realizada }.sumOf { it.caixinhaCentavos }
        val values = list.filter { it.realizada && it.valorCompraCentavos != null }.sumOf { it.valorCompraCentavos ?: 0 }
        add(card("✓   " + done + "\nEntregas realizadas", 18f, Color.rgb(6, 60, 95)), 8)
        add(card("◉   " + centsText(values) + "\nValor das compras registradas", 17f, Color.rgb(66, 22, 105)), 8)
        add(card("🎁   " + centsText(tips) + "\nCaixinhas recebidas", 17f, Color.rgb(73, 57, 3)), 8)
        add(card("🚚   " + pendingCount + "\nEntregas ainda pendentes", 17f, Color.rgb(5, 72, 48)), 8)
        add(btn("☷  VER ENTREGAS REALIZADAS") { completed() }, 14)
    }

        private fun syncScreen() {
        base("Sincronização")
        add(card("Status\nDados locais aguardando sincronização central.", 17f), 0)
        add(txt("Firebase será conectado na etapa de sincronização.", 14f, muted), 8)
        add(btn("←  Voltar") { home() }, 18)
    }

    private fun menuScreen() {
        base("Menu", { home() })
        add(card("🍎  SAMUEL\nFRUTAS", 20f), 8)
        add(btn("⌂  Início") { home() }, 8)
        add(btn("☷  Entregas Pendentes") { pending() })
        add(btn("✓  Entregas Realizadas") { completed() })
        add(btn("▥  Meu Dia") { myDay() })
        add(btn("☁  Sincronização") { syncScreen() })
        add(danger("↪  Sair do Aplicativo") { finishAndRemoveTask() }, 22)
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
