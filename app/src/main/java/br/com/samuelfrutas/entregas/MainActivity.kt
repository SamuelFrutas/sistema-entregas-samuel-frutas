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
import br.com.samuelfrutas.entregas.data.FirebaseSync

class MainActivity : Activity() {
    private lateinit var db: EntregaDbHelper
    private lateinit var firebaseSync: FirebaseSync
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
    private var systemBackAction: (() -> Unit)? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun sp(v: Float): Float = v

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        db = EntregaDbHelper(this)
        firebaseSync = FirebaseSync(db)
        firebaseSync.startRealtimeSync()
        home()
    }

    private fun rounded(color: Int, radius: Float = 18f, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            stroke?.let { setStroke(1, it) }
        }

    private fun base(title: String, back: (() -> Unit)? = null) {
        systemBackAction = if (title.isBlank()) null else (back ?: { home() })
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16), dp(8), dp(16), dp(20))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            addView(root)
        }
        if (title.isBlank()) {
            setContentView(scroll)
            return
        }

        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(10))
        }
        if (back != null) {
            bar.addView(TextView(this).apply {
                text = "‹"
                textSize = 34f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(48))
                setOnClickListener { back() }
            })
        }
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
            gravity = Gravity.CENTER_VERTICAL
        }
        titleBox.addView(TextView(this).apply {
            text = title
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        titleBox.addView(TextView(this).apply {
            text = if (title.isBlank()) "" else if (back == null) "SAMUEL FRUTAS • ENTREGADOR" else "MODO ENTREGADOR • OFFLINE"
            textSize = 10f
            setTextColor(muted)
        })
        bar.addView(titleBox)
        bar.addView(ImageView(this).apply {
            setImageResource(br.com.samuelfrutas.entregas.R.drawable.ic_menu)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = rounded(surface2, 14f, Color.rgb(35, 76, 102))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            setOnClickListener { menuScreen() }
        })
        root.addView(bar)
        setContentView(scroll)
    }

    private fun icon(resId: Int, size: Int = 24): ImageView = ImageView(this).apply {
        setImageResource(resId)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        scaleType = ImageView.ScaleType.CENTER_INSIDE
    }

    private fun iconTitle(resId: Int, title: String, subtitle: String = ""): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            addView(icon(resId, 28))
            val texts = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                setPadding(dp(10), 0, 0, 0)
                addView(txt(title, 15f, Color.WHITE, true))
                if (subtitle.isNotBlank()) addView(txt(subtitle, 11f, muted))
            }
            addView(texts)
        }

    private fun txt(s: String, size: Float = 15f, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(dp(2), dp(4), dp(2), dp(4))
        }

    private fun section(s: String) = txt(s.uppercase(Locale.getDefault()), 11f, green, true)

    private fun card(s: String, size: Float = 15f, bgColor: Int = panel) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(bgColor, 14f, Color.rgb(35, 76, 102))
        }

    private fun btn(s: String, action: () -> Unit) =
        TextView(this).apply {
            text = s
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = rounded(panel2, 14f, Color.rgb(35, 76, 102))
            minHeight = dp(48)
            setOnClickListener { action() }
        }

    private fun iconBtn(resId: Int, title: String, action: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(panel2, 14f, Color.rgb(35, 76, 102))
            setPadding(dp(14), dp(8), dp(14), dp(8))
            minimumHeight = dp(52)
            addView(icon(resId, 24))
            addView(txt(title, 14f, Color.WHITE, true).apply {
                setPadding(dp(10), dp(4), dp(4), dp(4))
            })
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
            minHeight = dp(48)
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
            minHeight = dp(46)
            setOnClickListener { action() }
        }

    private fun stepBar(active: Int) {
        val box = LinearLayout(this).apply {
            background = rounded(panel, 13f, Color.rgb(35, 76, 102))
            setPadding(dp(8), dp(6), dp(8), dp(6))
            gravity = Gravity.CENTER
        }
        val labels = listOf("Local", "Pagamento", "Revisão")
        labels.forEachIndexed { i, label ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f)
            }
            item.addView(TextView(this).apply {
                text = (i + 1).toString()
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(if (i + 1 <= active) Color.BLACK else Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                background = rounded(if (i + 1 <= active) green else panel2, 50f, if (i + 1 <= active) green else Color.rgb(35, 76, 102))
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
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
            setPadding(dp(9), dp(4), dp(9), dp(4))
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
            setPadding(dp(14), dp(4), dp(14), dp(4))
            background = rounded(panel2, 12f, Color.rgb(35, 76, 102))
            minHeight = 54
        }

    private fun check(s: String) =
        CheckBox(this).apply {
            text = s
            textSize = 14f
            setTextColor(Color.WHITE)
            buttonTintList = android.content.res.ColorStateList.valueOf(green)
            setPadding(0, dp(2), 0, dp(2))
        }

    private fun radio(s: String) =
        RadioButton(this).apply {
            text = s
            textSize = 14f
            setTextColor(Color.WHITE)
            buttonTintList = android.content.res.ColorStateList.valueOf(green)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            minHeight = dp(52)
        }

        private fun moneyToCents(s: String): Long? =
        s.replace(".", "").replace(",", ".").toDoubleOrNull()?.let { (it * 100).toLong() }

    private fun centsText(v: Long?) =
        v?.let { money.format(it / 100.0) } ?: ""

    private fun location(e: EntregaLocal): String {
        if (e.semEndereco) return e.enderecoReferencia
        val parts = mutableListOf<String>()
        if (e.predio.isNotBlank()) parts.add(e.predio)
        if (e.bloco.isNotBlank()) parts.add(e.bloco)
        if (e.apartamento.isNotBlank()) parts.add(e.apartamento)
        return parts.joinToString(" • ")
    }

    private fun home() {
        base("")
        val list = db.listarDia(today)
        val pendingCount = list.count { !it.realizada }
        val doneCount = list.count { it.realizada }

        // Cabeçalho moderno: marca + status do modo entregador
        val head = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(panel, 18f, Color.rgb(35, 76, 102))
            setPadding(dp(14), dp(10), dp(10), dp(10))
        }
        head.addView(icon(R.drawable.ic_truck, 34).apply {
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        })
        val logo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setPadding(dp(10), 0, 0, 0)
        }
        logo.addView(txt("SAMUEL FRUTAS", 18f, Color.WHITE, true))
        logo.addView(txt("MODO ENTREGADOR", 10f, green, true))
        head.addView(logo)
        head.addView(icon(R.drawable.ic_menu, 22).apply {
            background = rounded(surface2, 12f, Color.rgb(35, 76, 102))
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            setOnClickListener { menuScreen() }
        })
        add(head, 0)

        val mode = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(4, 75, 48), 16f, green)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        mode.addView(icon(R.drawable.ic_check_circle, 28))
        mode.addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setPadding(dp(10), 0, 0, 0)
            addView(txt("ONLINE / OFFLINE", 13f, Color.WHITE, true))
            addView(txt("Pronto para registrar entregas", 11f, Color.rgb(190, 235, 211)))
        })
        add(mode, 10)

        add(section("Resumo de hoje"), 18)

        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val pending = iconTitle(R.drawable.ic_pending, "PENDENTES", pendingCount.toString())
        pending.background = rounded(panel, 16f, Color.rgb(35, 76, 102))
        pending.setOnClickListener { pending() }
        val done = iconTitle(R.drawable.ic_check_circle, "REALIZADAS", doneCount.toString())
        done.background = rounded(panel, 16f, Color.rgb(35, 76, 102))
        done.setOnClickListener { completed() }
        stats.addView(pending, LinearLayout.LayoutParams(0, dp(112), 1f).apply { rightMargin = dp(5) })
        stats.addView(done, LinearLayout.LayoutParams(0, dp(112), 1f).apply { leftMargin = dp(5) })
        add(stats, 8)

        val newBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = rounded(green, 16f)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { newDelivery() }
        }
        newBox.addView(icon(R.drawable.ic_add, 28).apply {
            setColorFilter(Color.BLACK)
        })
        newBox.addView(txt("NOVA ENTREGA", 16f, Color.BLACK, true).apply {
            setPadding(dp(8), dp(4), dp(0), dp(4))
        })
        add(newBox, 12)

        add(section("Seu dia"), 18)

        val day = iconTitle(R.drawable.ic_pending, "MEU DIA", list.size.toString() + " entregas registradas hoje").apply {
            background = rounded(panel2, 16f, Color.rgb(35, 76, 102))
            setOnClickListener { myDay() }
        }
        add(day, 8)

        val sync = iconTitle(R.drawable.ic_check_circle, "SINCRONIZAÇÃO", "Dados locais prontos para sincronizar quando a internet voltar.").apply {
            background = rounded(panel2, 16f, Color.rgb(35, 76, 102))
            setOnClickListener { syncScreen() }
        }
        add(sync, 8)

        val offline = iconTitle(R.drawable.ic_truck, "MODO OFFLINE", "O aplicativo continua funcionando sem internet.").apply {
            background = rounded(panel2, 16f, Color.rgb(35, 76, 102))
        }
        add(offline, 8)
    }

    private fun newDelivery() {
        base("Nova Entrega", { home() })
        stepBar(1)
        add(section("Local da entrega"), 12)

        val pred = field("Prédio *", InputType.TYPE_CLASS_NUMBER)
        val bloco = field("Bloco *", InputType.TYPE_CLASS_NUMBER)
        val semBloco = check("Prédio não possui bloco")
        val alphaBlock = check("Permitir letras no bloco")
        val ap = field("Apartamento *", InputType.TYPE_CLASS_NUMBER)
        val noAddress = check("Entrega sem endereço")
        val ref = field("Endereço / referência *")

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(pred, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(4) })
        row1.addView(bloco, LinearLayout.LayoutParams(0, dp(48), 0.72f).apply { leftMargin = dp(4); rightMargin = dp(4) })
        row1.addView(ap, LinearLayout.LayoutParams(0, dp(48), 0.9f).apply { leftMargin = dp(4) })
        add(row1); add(semBloco, 2); add(alphaBlock, 0); add(noAddress, 6); add(ref)

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
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val paid = radio("✓  Pago adiantado\n     Cliente já pagou. Não precisa informar o valor.")
        val unpaid = radio("●  Não pago\n     Cliente vai pagar na entrega. É obrigatório informar o valor.")
        val unknown = radio("○  Não informado\n     Não tenho o valor da entrega.")
        group.addView(paid); group.addView(unpaid); group.addView(unknown); unpaid.isChecked = true
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
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        row.addView(btn("EDITAR") { paymentStep() },
            LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
        row.addView(primary("✓  SALVAR ENTREGA") {
                val id = db.inserir(e)
                firebaseSync.sync(e.copy(id = id))
                confirmation()
            },
            LinearLayout.LayoutParams(-1, dp(52)))
        add(row, 14)
    }

    private fun confirmation() {
        base("Entrega registrada!", { pending() })
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(28), dp(18), dp(28))
            background = rounded(panel, 18f, green)
        }
        box.addView(TextView(this).apply { text = "✓"; textSize = 64f; gravity = Gravity.CENTER; setTextColor(green) })
        box.addView(txt("Entrega registrada!", 23f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        box.addView(txt("A entrega foi salva no aparelho e será sincronizada quando tiver internet.", 14f, muted).apply {
            gravity = Gravity.CENTER; setPadding(dp(20), dp(10), dp(20), dp(18))
        })
        add(box, 25)
        add(primary("＋  ADICIONAR NOVA ENTREGA") { newDelivery() }, 12)
        add(btn("☷  VER ENTREGAS PENDENTES") { pending() }, 10)
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
                list.filter { location(it).contains(q, true) }.forEach { item -> holder.addView(makePendingCard(item, list.indexOf(item) + 1)) }
                if (holder.childCount == 0) holder.addView(card("Nenhuma entrega encontrada.", 14f))
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        add(search, 8); add(holder)
        list.forEachIndexed { index, item -> holder.addView(makePendingCard(item, index + 1)) }
        if (list.isEmpty()) holder.addView(card("Nenhuma entrega pendente.", 15f))
        add(primary("＋  NOVA ENTREGA") { newDelivery() }, 14)
    }

    private fun makePendingCard(e: EntregaLocal, numero: Int): View {
        val accent = when (e.pagamentoInicial) {
            "NAO_PAGO" -> Color.rgb(245, 35, 58)
            "PAGO_ADIANTADO" -> Color.rgb(32, 139, 242)
            else -> Color.rgb(242, 166, 24)
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.rgb(6, 24, 37), 16f, accent)
            setPadding(0, 0, 0, 0)
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(accent, 16f)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(62), dp(154))
        }
        side.addView(txt("#" + e.id.toString().padStart(3, '0'), 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
        })
        side.addView(icon(R.drawable.ic_pending, 32))
        body.addView(side)

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setPadding(dp(10), dp(10), dp(8), dp(10))
        }

        val locationTitle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        locationTitle.addView(icon(R.drawable.ic_location, 23))
        locationTitle.addView(txt("LOCAL DA ENTREGA", 10.5f, muted, true).apply {
            setPadding(dp(7), dp(2), 0, dp(2))
        })
        info.addView(locationTitle)

        if (e.semEndereco) {
            info.addView(txt(e.enderecoReferencia, 15f, Color.WHITE, true).apply {
                setPadding(dp(2), dp(7), dp(2), dp(4))
            })
        } else {
            val addressRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(5), 0, 0)
            }

            fun numberBox(label: String, value: String, weight: Float): LinearLayout =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(0, dp(66), weight).apply {
                        rightMargin = dp(3)
                    }
                    addView(txt(label, 9.5f, muted, true).apply {
                        gravity = Gravity.CENTER
                        setPadding(0, 0, 0, dp(3))
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = value.ifBlank { "—" }
                        textSize = 17f
                        setTextColor(Color.WHITE)
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        background = rounded(Color.rgb(7, 28, 43), 11f, Color.rgb(35, 76, 102))
                        layoutParams = LinearLayout.LayoutParams(-1, dp(40))
                    })
                }

            addressRow.addView(numberBox("PRÉDIO", e.predio, 1.08f))
            addressRow.addView(txt("•", 16f, muted, true).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(66))
            })
            if (e.bloco.isNotBlank()) {
                addressRow.addView(numberBox("BLOCO", e.bloco, 0.82f))
                addressRow.addView(txt("•", 16f, muted, true).apply {
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(10), dp(66))
                })
            }
            addressRow.addView(numberBox("APTO", e.apartamento, 1.0f))
            info.addView(addressRow)
        }

        val purchase = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, 0)
        }
        purchase.addView(txt("VALOR", 9.5f, muted, true).apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), -2)
        })
        purchase.addView(txt(centsText(e.valorCompraCentavos).ifBlank { "Não informado" }, 15f, Color.WHITE, true))
        info.addView(purchase)
        body.addView(info)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(8), dp(9), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(122), -2)
        }
        val edit = TextView(this).apply {
            text = "EDITAR"
            textSize = 10f
            setTextColor(muted)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            isSingleLine = true
            background = rounded(Color.rgb(11, 38, 56), 9f, Color.rgb(35, 76, 102))
            minHeight = dp(30)
            setOnClickListener { editPending(e) }
        }
        actions.addView(edit, LinearLayout.LayoutParams(-1, dp(30)).apply { bottomMargin = dp(6) })
        actions.addView(statusChip(labelInitial(e.pagamentoInicial),
            if (e.pagamentoInicial == "NAO_PAGO") Color.rgb(225, 35, 52)
            else if (e.pagamentoInicial == "PAGO_ADIANTADO") Color.rgb(30, 122, 225)
            else Color.rgb(205, 130, 20)))
        actions.addView(TextView(this).apply {
            text = "ABRIR ENTREGA"
            textSize = 12.5f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            isSingleLine = true
            setPadding(dp(2), dp(8), dp(2), dp(8))
            background = rounded(green, 13f)
            minHeight = dp(46)
            setOnClickListener { finish(e) }
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        body.addView(actions)

        box.addView(body)
        return box.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            }
        }
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
            setPadding(dp(13), dp(10), dp(13), dp(10))
        }
        box.addView(txt(location(e) + "\n" + centsText(e.valorCompraCentavos).ifBlank { "Valor não informado" }, 15f, Color.WHITE, true))
        val result = if (e.resultadoPagamento == "PAGO") "Pago" else "Não pago"
        val detail = if (e.formaPagamento.isNotBlank()) result + " • " + e.formaPagamento else result
        box.addView(txt(detail, 12f, if (result == "Pago") green else Color.rgb(240, 70, 75), true))
        return box
    }

    private fun editPending(e: EntregaLocal) {
        base("Editar Entrega", { pending() })
        add(section("Corrigir dados da entrega"), 12)

        val pred = field("Prédio *", InputType.TYPE_CLASS_NUMBER).apply { setText(e.predio) }
        val bloco = field("Bloco", InputType.TYPE_CLASS_TEXT).apply { setText(e.bloco) }
        val ap = field("Apartamento *", InputType.TYPE_CLASS_NUMBER).apply { setText(e.apartamento) }
        val ref = field("Endereço / referência *").apply { setText(e.enderecoReferencia) }
        val noAddress = check("Entrega sem endereço").apply { isChecked = e.semEndereco }
        val semBloco = check("Prédio não possui bloco").apply { isChecked = e.bloco.isBlank() && !e.semEndereco }

        add(pred); add(bloco, 6); add(ap, 6)
        add(semBloco, 4); add(noAddress, 4); add(ref, 6)

        add(section("Pagamento e valor"), 14)
        val paymentGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            background = rounded(panel, 14f, Color.rgb(35, 76, 102))
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val paid = radio("✓  Pago adiantado")
        val unpaid = radio("●  Não pago")
        val unknown = radio("○  Não informado")
        paymentGroup.addView(paid); paymentGroup.addView(unpaid); paymentGroup.addView(unknown)
        when (e.pagamentoInicial) {
            "PAGO_ADIANTADO" -> paid.isChecked = true
            "NAO_PAGO" -> unpaid.isChecked = true
            else -> unknown.isChecked = true
        }
        add(paymentGroup, 6)

        val value = field(
            "Valor da compra",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        ).apply { setText(e.valorCompraCentavos?.let { String.format(Locale.US, "%.2f", it / 100.0) } ?: "") }
        add(value, 8)

        fun refreshPayment() {
            value.isEnabled = !paid.isChecked
            value.hint = when {
                paid.isChecked -> "Não necessário"
                unpaid.isChecked -> "Valor da compra *"
                else -> "Valor da compra (opcional)"
            }
        }
        paymentGroup.setOnCheckedChangeListener { _, _ -> refreshPayment() }
        refreshPayment()

        fun refresh() {
            val no = noAddress.isChecked
            pred.isEnabled = !no
            bloco.isEnabled = !no && !semBloco.isChecked
            ap.isEnabled = !no
            semBloco.isEnabled = !no
            ref.isEnabled = no
        }
        semBloco.setOnCheckedChangeListener { _, checked ->
            if (checked) bloco.setText("")
            refresh()
        }
        noAddress.setOnCheckedChangeListener { _, _ -> refresh() }
        refresh()

        add(primary("SALVAR ALTERAÇÕES") {
            if (!noAddress.isChecked) {
                if (pred.text.isBlank() || ap.text.isBlank()) {
                    toast("Preencha prédio e apartamento.")
                    return@primary
                }
                if (!semBloco.isChecked && bloco.text.isBlank()) {
                    toast("Informe o bloco ou marque 'Prédio não possui bloco'.")
                    return@primary
                }
            } else if (ref.text.isBlank()) {
                toast("Informe o endereço/referência.")
                return@primary
            }

            val initial = when (paymentGroup.checkedRadioButtonId) {
                paid.id -> "PAGO_ADIANTADO"
                unpaid.id -> "NAO_PAGO"
                else -> "NAO_INFORMADO"
            }
            val cents = if (initial == "PAGO_ADIANTADO") null else moneyToCents(value.text.toString())
            if (initial == "NAO_PAGO" && cents == null) {
                toast("Para 'Não pago', informe o valor da compra.")
                return@primary
            }

            val updated = e.copy(
                predio = pred.text.toString(),
                bloco = if (semBloco.isChecked) "" else bloco.text.toString(),
                apartamento = ap.text.toString(),
                semEndereco = noAddress.isChecked,
                enderecoReferencia = ref.text.toString(),
                valorCompraCentavos = cents,
                pagamentoInicial = initial
            )
            db.atualizarLocal(updated)
            firebaseSync.sync(updated)
            pending()
        }, 14)

        add(danger("EXCLUIR ENTREGA") {
            android.app.AlertDialog.Builder(this)
                .setTitle("Excluir entrega?")
                .setMessage("Essa entrega será removida do aparelho e não poderá ser recuperada.")
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("EXCLUIR") { _, _ ->
                    db.excluir(e.id)
                    firebaseSync.delete(e.id)
                    pending()
                }
                .show()
        }, 10)
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
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        val cash = radio("Dinheiro")
        val card = radio("Cartão")
        methods.addView(cash, RadioGroup.LayoutParams(0, dp(52), 1f))
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
            val tipCents = moneyToCents(tip.text.toString()) ?: 0
            val observation = obs.text.toString()
            db.atualizarFinal(e.id, result, form, tipCents, observation)
            firebaseSync.sync(e.copy(
                resultadoPagamento = result,
                formaPagamento = form,
                caixinhaCentavos = tipCents,
                observacao = observation,
                realizada = true
            ))
            confirmation()
        }, 16)
    }

private fun myDay() {
        val cached = getSharedPreferences("entregador_config", MODE_PRIVATE)
            .getLong("valor_entrega_entregador_centavos", 0L)
        renderMyDay(cached)
        firebaseSync.getValorPorEntrega(today) { remote ->
            if (remote != null) {
                getSharedPreferences("entregador_config", MODE_PRIVATE)
                    .edit()
                    .putLong("valor_entrega_entregador_centavos", remote)
                    .apply()
                runOnUiThread { renderMyDay(remote) }
            }
        }
    }

    private fun renderMyDay(valorPorEntrega: Long) {
        base("Meu Dia", { home() })
        val list = db.listarDia(today)
        val done = list.count { it.realizada }
        val pendingCount = list.count { !it.realizada }
        val realizadas = list.filter { it.realizada }
        val tips = realizadas.sumOf { it.caixinhaCentavos }
        val ganhoDoDia = valorPorEntrega * done
        val totalGeral = ganhoDoDia + tips
        val ganhoText = centsText(ganhoDoDia)

        add(iconTitle(R.drawable.ic_check_circle, "ENTREGAS REALIZADAS", done.toString()).apply {
            background = rounded(Color.rgb(6, 60, 95), 16f, Color.rgb(35, 76, 102))
        }, 8)
        add(iconTitle(R.drawable.ic_truck, "VALOR DO ENTREGADOR", ganhoText + "\n" +
            (if (valorPorEntrega > 0L) centsText(valorPorEntrega) + " × " + done + " entregas" else "Configure no dashboard web")).apply {
            background = rounded(Color.rgb(66, 22, 105), 16f, Color.rgb(35, 76, 102))
        }, 8)
        add(iconTitle(R.drawable.ic_add, "CAIXINHAS RECEBIDAS", centsText(tips)).apply {
            background = rounded(Color.rgb(73, 57, 3), 16f, Color.rgb(35, 76, 102))
        }, 8)
        add(iconTitle(R.drawable.ic_check_circle, "TOTAL GERAL", centsText(totalGeral)).apply {
            background = rounded(Color.rgb(6, 60, 95), 16f, Color.rgb(35, 76, 102))
        }, 8)
        add(iconTitle(R.drawable.ic_pending, "ENTREGAS PENDENTES", pendingCount.toString()).apply {
            background = rounded(Color.rgb(5, 72, 48), 16f, Color.rgb(35, 76, 102))
        }, 8)
        add(iconBtn(R.drawable.ic_check_circle, "VER ENTREGAS REALIZADAS") { completed() }, 14)
    }

        private fun syncScreen() {
        base("Sincronização")
        add(card("Status\n" + firebaseSync.status, 17f), 0)
        add(txt("As entregas são gravadas no aparelho e enviadas ao Firestore quando houver internet.", 14f, muted), 8)
        add(primary("SINCRONIZAR AGORA") {
            val itens = db.listarDia(today)
            if (itens.isEmpty()) {
                toast("Nenhuma entrega de hoje para receber.")
                syncScreen()
            } else {
                toast("Recebendo alterações do sistema...")
                itens.forEach { item ->
                    firebaseSync.pull(item) { ok ->
                        runOnUiThread {
                            if (!ok) toast("Não foi possível receber a alteração. Veja o status.")
                            syncScreen()
                        }
                    }
                }
            }
        }, 14)
        add(btn("←  Voltar") { home() }, 18)
    }

    private fun menuScreen() {
        base("Menu", { home() })
        add(iconTitle(R.drawable.ic_truck, "SAMUEL FRUTAS", "MODO ENTREGADOR").apply {
            background = rounded(panel, 16f, Color.rgb(35, 76, 102))
        }, 8)
        add(iconBtn(R.drawable.ic_truck, "Início") { home() }, 8)
        add(iconBtn(R.drawable.ic_pending, "Entregas Pendentes") { pending() })
        add(iconBtn(R.drawable.ic_check_circle, "Entregas Realizadas") { completed() })
        add(iconBtn(R.drawable.ic_pending, "Meu Dia") { myDay() })
        add(iconBtn(R.drawable.ic_check_circle, "Sincronização") { syncScreen() })
        add(danger("Sair do Aplicativo") { finishAndRemoveTask() }, 22)
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

    @Deprecated("Use systemBackAction navigation")
    override fun onBackPressed() {
        val action = systemBackAction
        if (action != null) {
            systemBackAction = null
            action()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        firebaseSync.stopRealtimeSync()
        db.close()
        super.onDestroy()
    }
}
