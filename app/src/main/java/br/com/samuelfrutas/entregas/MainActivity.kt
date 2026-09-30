package br.com.samuelfrutas.entregas

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private val bg = Color.rgb(16, 20, 24)
    private val card = Color.rgb(27, 33, 38)
    private val green = Color.rgb(70, 210, 120)
    private val white = Color.WHITE
    private val muted = Color.rgb(180, 188, 194)

    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun base(title: String, subtitle: String? = null): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        titleBox.addView(label(title, 24f, white))
        subtitle?.let { titleBox.addView(label(it, 14f, muted)) }

        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))

        val status = label("● OFFLINE / ONLINE", 12f, green)
        header.addView(status)

        root.addView(header)
        return root
    }

    private fun label(text: String, size: Float, color: Int): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            setPadding(0, 4, 0, 8)
        }

    private fun button(text: String, action: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            setTextColor(white)
            setBackgroundColor(Color.rgb(42, 52, 58))
            setOnClickListener { action() }
            minimumHeight = 52
        }

    private fun primary(text: String, action: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            setTextColor(Color.BLACK)
            setBackgroundColor(green)
            setOnClickListener { action() }
            minimumHeight = 56
        }

    private fun card(title: String, body: String, actionText: String? = null, action: (() -> Unit)? = null): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(card)
        }
        box.addView(label(title, 18f, white))
        box.addView(label(body, 14f, muted))
        if (actionText != null && action != null) {
            box.addView(button(actionText, action), LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = 10
            })
        }
        return box
    }

    private fun addGap(parent: LinearLayout, height: Int = 12) {
        parent.addView(Space(this), LinearLayout.LayoutParams(1, height))
    }

    private fun edit(hint: String, numeric: Boolean = false): EditText =
        EditText(this).apply {
            this.hint = hint
            setHintTextColor(Color.rgb(125, 135, 142))
            setTextColor(white)
            setSingleLine(true)
            setPadding(14, 4, 14, 4)
            if (numeric) inputType = InputType.TYPE_CLASS_NUMBER
        }

    private fun finish(view: View) {
        setContentView(view)
    }

    private fun showHome() {
        val v = base("Samuel Frutas", "Entregas de hoje")
        addGap(v)
        v.addView(card("ENTREGAS PENDENTES", "0 entregas aguardando realização.", "VER PENDENTES") { showPending() })
        addGap(v)
        v.addView(card("ENTREGAS REALIZADAS", "0 entregas concluídas hoje.", "VER REALIZADAS") { showCompleted() })
        addGap(v)
        v.addView(card("STATUS", "Aplicativo preparado para operação offline. Os dados serão salvos localmente.", "SINCRONIZAÇÃO") { showSync() })
        addGap(v, 20)
        v.addView(primary("+ NOVA ENTREGA") { showNewDelivery() })
        addGap(v)
        v.addView(button("☰ MENU") { showMenu() })
        finish(v)
    }

    private fun showNewDelivery() {
        val v = base("Nova entrega", "Cadastro do local")
        addGap(v)

        v.addView(label("Prédio", 14f, muted))
        val predio = edit("Somente números", true)
        v.addView(predio)

        v.addView(label("Bloco", 14f, muted))
        val bloco = edit("Número inicialmente", true)
        v.addView(bloco)
        v.addView(button("ABC — permitir letras no bloco") {
            bloco.inputType = InputType.TYPE_CLASS_TEXT
            bloco.hint = "Ex.: E ou E13"
            bloco.requestFocus()
        })

        v.addView(label("Apartamento", 14f, muted))
        val apto = edit("Somente números", true)
        v.addView(apto)

        addGap(v)
        v.addView(label("Localização alternativa", 14f, muted))
        v.addView(checkBox("Entrega sem endereço") {
            // A regra de obrigatoriedade será implementada no bloco de pagamento/validação.
        })
        v.addView(edit("Endereço / referência, se necessário"))

        addGap(v)
        v.addView(label("Pagamento inicial", 14f, muted))
        val payment = RadioGroup(this)
        listOf("Pago adiantado", "Não pago", "Não informado").forEach {
            payment.addView(RadioButton(this).apply {
                text = it
                setTextColor(white)
            })
        }
        v.addView(payment)

        addGap(v)
        v.addView(button("CONTINUAR") { showReview() })
        addGap(v)
        v.addView(button("← VOLTAR") { showHome() })
        finish(v)
    }

    private fun showReview() {
        val v = base("Revisar entrega", "Confira antes de salvar")
        addGap(v)
        v.addView(card("LOCAL", "Prédio / Bloco / Apartamento\nEndereço ou referência quando necessário."))
        addGap(v)
        v.addView(card("PAGAMENTO", "Situação inicial selecionada na etapa anterior."))
        addGap(v)
        v.addView(card("CAIXINHA", "Será registrada durante a entrega."))
        addGap(v)
        v.addView(card("OBSERVAÇÃO", "Será registrada durante a entrega."))
        addGap(v)
        v.addView(primary("SALVAR COMO PENDENTE") { showPending() })
        addGap(v)
        v.addView(button("← VOLTAR") { showNewDelivery() })
        finish(v)
    }

    private fun showPending() {
        val v = base("Entregas pendentes", "Ainda não realizadas")
        addGap(v)
        v.addView(card("Nenhuma entrega exibida", "Quando houver entregas cadastradas para hoje, elas aparecerão aqui."))
        addGap(v)
        v.addView(primary("+ NOVA ENTREGA") { showNewDelivery() })
        addGap(v)
        v.addView(button("← INÍCIO") { showHome() })
        finish(v)
    }

    private fun showDelivery() {
        val v = base("Entrega", "Entrega pendente")
        addGap(v)
        v.addView(card("LOCAL", "Prédio 0000\nBloco 0\nApartamento 000\nReferência opcional"))
        addGap(v)
        v.addView(card("COBRANÇA", "Valor e situação da entrega serão definidos/registrados conforme as regras do sistema."))
        addGap(v)
        v.addView(card("OBSERVAÇÃO", "Nenhuma observação registrada."))
        addGap(v)
        v.addView(primary("CONCLUIR ENTREGA") { showConfirmation() })
        addGap(v)
        v.addView(button("← PENDENTES") { showPending() })
        finish(v)
    }

    private fun showConfirmation() {
        val v = base("Confirmar entrega", "Esta entrega será marcada como realizada")
        addGap(v)
        v.addView(card("CONFIRMAÇÃO", "Depois de confirmar, a entrega sai de Pendentes e aparece em Entregas realizadas."))
        addGap(v)
        v.addView(primary("CONFIRMAR COMO REALIZADA") { showCompleted() })
        addGap(v)
        v.addView(button("← VOLTAR") { showDelivery() })
        finish(v)
    }

    private fun showCompleted() {
        val v = base("Entregas realizadas", "Concluídas hoje")
        addGap(v)
        v.addView(card("Nenhuma entrega realizada", "As entregas concluídas aparecerão aqui."))
        addGap(v)
        v.addView(button("← INÍCIO") { showHome() })
        finish(v)
    }

    private fun showSync() {
        val v = base("Sincronização", "Operação offline-first")
        addGap(v)
        v.addView(card("STATUS", "A sincronização automática será implementada na etapa SQLite + Firebase."))
        addGap(v)
        v.addView(card("SEGURANÇA", "Registros locais não serão removidos antes da confirmação da sincronização."))
        addGap(v)
        v.addView(card("PENDENTES DE SINCRONIZAÇÃO", "0 registros nesta etapa inicial."))
        addGap(v)
        v.addView(button("← INÍCIO") { showHome() })
        finish(v)
    }

    private fun showMenu() {
        val v = base("Menu", "Sistema de Entregas")
        addGap(v)
        v.addView(button("ENTREGAS PENDENTES") { showPending() })
        addGap(v)
        v.addView(button("ENTREGAS REALIZADAS") { showCompleted() })
        addGap(v)
        v.addView(button("SINCRONIZAÇÃO") { showSync() })
        addGap(v)
        v.addView(button("← INÍCIO") { showHome() })
        finish(v)
    }

    private fun checkBox(text: String, action: () -> Unit): CheckBox =
        CheckBox(this).apply {
            this.text = text
            setTextColor(white)
            setOnCheckedChangeListener { _, _ -> action() }
        }
}
