package com.tpappcc

import android.app.Activity
import android.os.Bundle
import android.os.CountDownTimer
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import android.graphics.drawable.GradientDrawable

class MainActivity : Activity() {
    private var timer: CountDownTimer? = null
    private lateinit var status: TextView
    private lateinit var timerView: TextView
    private lateinit var duration: Spinner

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(18), dp(20), dp(18))
        background = GradientDrawable().apply {
            setColor(Color.rgb(21, 25, 34))
            cornerRadius = dp(22).toFloat()
        }
    }

    private fun text(value: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun addSpace(parent: LinearLayout, h: Int) =
        parent.addView(Space(this), LinearLayout.LayoutParams(1, dp(h)))

    private fun showHome() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
            setBackgroundColor(Color.rgb(11, 13, 18))
        }

        root.addView(text("TPaPCC", 32f, Color.WHITE, true))
        root.addView(text("Trusted Phone and PC Connect", 14f, Color.rgb(169,173,186)))
        addSpace(root, 22)

        val hero = card()
        hero.addView(text("Доверенное подключение", 21f, Color.WHITE, true))
        addSpace(hero, 7)
        hero.addView(text("Подключайся к телефону друга только после его подтверждения.", 14f, Color.rgb(169,173,186)))
        addSpace(hero, 18)
        status = text("●  Сессия не активна", 15f, Color.rgb(169,173,186), true)
        hero.addView(status)
        root.addView(hero, LinearLayout.LayoutParams(-1, -2))

        addSpace(root, 16)
        val pairing = card()
        pairing.addView(text("Сопряжение", 18f, Color.WHITE, true))
        addSpace(pairing, 10)
        val code = EditText(this).apply {
            hint = "Введите 6-значный код"
            inputType = 2
            textSize = 16f
            setSingleLine()
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(120,125,138))
            background = GradientDrawable().apply {
                setColor(Color.rgb(29,34,48)); cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(16), 0, dp(16), 0)
        }
        pairing.addView(code, LinearLayout.LayoutParams(-1, dp(54)))
        addSpace(pairing, 10)
        val pair = Button(this).apply {
            text = "Подключиться"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(124,92,252)); cornerRadius = dp(14).toFloat()
            }
            setOnClickListener {
                status.text = if (code.text.toString().length == 6)
                    "●  Ожидаем подтверждение друга"
                else "Введите ровно 6 цифр"
            }
        }
        pairing.addView(pair, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(pairing)

        addSpace(root, 16)
        val session = card()
        session.addView(text("Длительность сессии", 18f, Color.WHITE, true))
        addSpace(session, 6)
        session.addView(text("Друг выбирает, как долго действует доступ. Его можно завершить раньше.", 14f, Color.rgb(169,173,186)))
        addSpace(session, 12)

        duration = Spinner(this)
        val options = arrayOf(
            "15 минут", "30 минут", "1 час", "2 часа", "3 часа",
            "6 часов", "12 часов", "24 часа"
        )
        duration.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        session.addView(duration, LinearLayout.LayoutParams(-1, dp(52)))
        addSpace(session, 10)

        timerView = text("", 22f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        session.addView(timerView, LinearLayout.LayoutParams(-1, dp(48)))

        val start = Button(this).apply {
            text = "Запустить сессию"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(67,209,122)); cornerRadius = dp(14).toFloat()
            }
            setOnClickListener { startSession(duration.selectedItemPosition) }
        }
        session.addView(start, LinearLayout.LayoutParams(-1, dp(52)))
        addSpace(session, 8)

        val stop = Button(this).apply {
            text = "Завершить доступ"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(255,95,109)); cornerRadius = dp(14).toFloat()
            }
            setOnClickListener { stopSession() }
        }
        session.addView(stop, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(session)

        addSpace(root, 16)
        val permissions = card()
        permissions.addView(text("Разрешения", 18f, Color.WHITE, true))
        addSpace(permissions, 8)
        permissions.addView(text("Экран  •  Микрофон  •  Файлы  •  Уведомления  •  Управление", 14f, Color.rgb(169,173,186)))
        addSpace(permissions, 8)
        permissions.addView(text("Каждая возможность включается отдельно и только с согласия владельца телефона.", 13f, Color.rgb(169,173,186)))
        root.addView(permissions)

        addSpace(root, 16)
        val capabilities = card()
        capabilities.addView(text("Возможности устройства", 18f, Color.WHITE, true))
        addSpace(capabilities, 6)
        capabilities.addView(text("Функции разделены по уровню доступа. Никаких скрытых действий.", 13f, Color.rgb(169,173,186)))

        addSpace(capabilities, 14)
        capabilities.addView(text("БЕЗ ROOT / SHIZUKU", 13f, Color.rgb(124,92,252), true))
        addSpace(capabilities, 6)
        capabilities.addView(text(
            "✓ Трансляция экрана\n" +
            "✓ Снимок экрана\n" +
            "✓ Запись экрана\n" +
            "✓ Микрофон — только с системным разрешением\n" +
            "✓ Аудио воспроизведения — где поддерживается Android\n" +
            "✓ Выбор файлов и папок через системный файловый picker\n" +
            "✓ Фото и видео через системные API\n" +
            "✓ Отправка уведомлений, если разрешено системой\n" +
            "✓ Изменение громкости\n" +
            "✓ Яркость экрана в разрешённых Android пределах\n" +
            "✓ Ограниченное управление интерфейсом через Accessibility\n" +
            "✓ Запуск выбранных действий/приложений через Android Intent\n" +
            "✓ Просмотр состояния батареи, сети и экрана",
            14f, Color.rgb(225,227,235)
        ))

        addSpace(capabilities, 16)
        capabilities.addView(text("ROOT / РАСШИРЕННЫЙ РЕЖИМ", 13f, Color.rgb(255,173,74), true))
        addSpace(capabilities, 6)
        capabilities.addView(text(
            "⚠ Требует отдельного явного разрешения владельца устройства.\n\n" +
            "• Расширенный доступ к файловой системе\n" +
            "• Системные настройки, недоступные обычному приложению\n" +
            "• Расширенное управление пакетами/приложениями\n" +
            "• Расширенное управление системными службами\n" +
            "• Дополнительные shell-команды устройства\n" +
            "• Расширенные сетевые и системные настройки\n\n" +
            "TPaPCC не будет выполнять скрытые команды, обходить подтверждения или получать доступ к данным без согласия владельца.",
            14f, Color.rgb(225,227,235)
        ))
        root.addView(capabilities)

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun startSession(index: Int) {
        val minutes = intArrayOf(15, 30, 60, 120, 180, 360, 720, 1440)[index]
        timer?.cancel()
        status.text = "●  Сессия активна"
        timerView.text = "%02d:%02d:00".format(minutes / 60, minutes % 60)
        timer = object : CountDownTimer(minutes * 60_000L, 1000L) {
            override fun onTick(ms: Long) {
                timerView.text = "%02d:%02d:%02d".format(
                    ms / 3600000, (ms / 60000) % 60, (ms / 1000) % 60
                )
            }
            override fun onFinish() {
                timerView.text = "00:00:00"
                status.text = "●  Сессия завершена"
            }
        }.start()
    }

    private fun stopSession() {
        timer?.cancel()
        timerView.text = ""
        status.text = "●  Доступ остановлен"
    }
}
