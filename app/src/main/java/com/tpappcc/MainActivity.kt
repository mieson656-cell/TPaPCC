package com.tpappcc

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*

class MainActivity : Activity() {
    private var timer: CountDownTimer? = null
    private val sessionHandler = Handler(Looper.getMainLooper())
    private var sessionPolling = false
    private var lastPromptSession = ""
    private var activeSessionId = ""
    private var webRtcSession: WebRtcSession? = null
    private var activePermissions = emptySet<String>()
    private lateinit var status: TextView
    private lateinit var timerView: TextView
    private lateinit var duration: Spinner
    private var tone: ToneGenerator? = null
    private var connectedSessionPanel: View? = null
    private var connectedActionsPanel: View? = null

    private val bg = Color.rgb(9, 11, 16)
    private val cardBg = Color.rgb(20, 24, 34)
    private val fieldBg = Color.rgb(29, 34, 48)
    private val accent = Color.rgb(124, 92, 252)
    private val muted = Color.rgb(165, 170, 184)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun text(v: String, s: Float, c: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = v
            textSize = s
            setTextColor(c)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
    private fun gap(parent: LinearLayout, h: Int) =
        parent.addView(Space(this), LinearLayout.LayoutParams(1, dp(h)))
    private fun rounded(color: Int, radius: Int = 20) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(18), dp(20), dp(18))
        background = rounded(cardBg, 22)
    }
    private fun button(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        textSize = 15f
        setTextColor(Color.WHITE)
        background = rounded(accent, 15)
        isAllCaps = false
        setOnClickListener { click() }
    }
    private fun animateIn(v: View, delay: Long = 0) {
        v.alpha = 0f
        v.translationY = dp(18).toFloat()
        v.postDelayed({
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(v, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, dp(18).toFloat(), 0f)
                )
                duration = 420
                interpolator = DecelerateInterpolator()
                start()
            }
        }, delay)
    }
    private fun switchView(build: () -> View) {
        val old = window.decorView.findViewById<View>(android.R.id.content)
        val next = build()
        next.alpha = 0f
        next.scaleX = .97f
        next.scaleY = .97f
        setContentView(next)
        next.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(DecelerateInterpolator()).start()
        old?.let { it.animate().alpha(0f).setDuration(120).start() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        createChannel()
        tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
        val prefs = getSharedPreferences("tpapcc_ui", MODE_PRIVATE)
        if (prefs.getBoolean("onboarded", false)) showWelcomeBack()
        else showWelcome()
        TpaPccApi.register(this, "Мой Android") { ok, _ ->
            if (::status.isInitialized) status.text = if (ok) "● Устройство подключено" else "● Ожидаем сеть"
        }
    }

    override fun onResume() { super.onResume(); startSessionPolling() }
    override fun onPause() { super.onPause(); stopSessionPolling() }

    private fun startSessionPolling() {
        if (sessionPolling) return
        sessionPolling = true
        sessionHandler.post(sessionPoll)
    }
    private fun stopSessionPolling() {
        sessionPolling = false
        sessionHandler.removeCallbacks(sessionPoll)
    }
    private val sessionPoll = object : Runnable {
        override fun run() {
            if (!sessionPolling) return
            TpaPccApi.sessionStatus(this@MainActivity) { ok, obj ->
                if (ok && obj != null) {
                    val s = obj.optJSONObject("session")
                    if (s == null) {
                        if (activeSessionId.isNotBlank()) { webRtcSession?.stop(); webRtcSession = null; activeSessionId = "" }
                        activePermissions = emptySet()
                        lastPromptSession = ""
                        if (::status.isInitialized) status.text = "● Активной удалённой сессии нет"
                    } else {
                        val id = s.optString("id")
                        val approved = !s.isNull("approved_at")
                        if (!approved && id.isNotBlank() && id != lastPromptSession) {
                            lastPromptSession = id
                            showApprovalDialog(id)
                        }
                        if (approved) {
                            activeSessionId = id
                            activePermissions = obj.optJSONArray("permissions")?.let { a ->
                                buildSet { for (i in 0 until a.length()) add(a.optString(i)) }
                            } ?: emptySet()
                            if (::status.isInitialized) { status.text = "● Удалённая сессия разрешена"; beep(true) }
                            connectedSessionPanel?.let { sessionView -> connectedActionsPanel?.let { actionsView -> revealConnectedControls(sessionView, actionsView) } }
                        }
                    }
                }
            }
            sessionHandler.postDelayed(this, 2000)
        }
    }

    private fun showWelcome() {
        getSharedPreferences("tpapcc_ui", MODE_PRIVATE).edit().putBoolean("onboarded", true).apply()
        val root = onboardingRoot()
        val title = text("Привет! 👋", 32f, Color.WHITE, true)
        val sub = text("Добро пожаловать в TPaPCC", 18f, muted)
        root.addView(title); gap(root, 8); root.addView(sub); gap(root, 28)
        root.addView(text("Доверенное подключение устройств без скрытого доступа.", 16f, Color.WHITE))
        gap(root, 34)
        root.addView(button("Начать →") { showRolePicker() }, LinearLayout.LayoutParams(-1, dp(56)))
        setContentView(root)
        animateIn(title); animateIn(sub, 90)
    }

    private fun showWelcomeBack() {
        val root = onboardingRoot()
        val title = text("С возвращением, бро! 👋", 30f, Color.WHITE, true)
        val sub = text("TPaPCC снова готов к работе", 18f, muted)
        root.addView(title); gap(root, 8); root.addView(sub); gap(root, 28)
        root.addView(text("Продолжим с твоими доверенными подключениями.", 16f, Color.WHITE))
        gap(root, 34)
        root.addView(button("Продолжить →") { showRolePicker() }, LinearLayout.LayoutParams(-1, dp(56)))
        setContentView(root)
        animateIn(title); animateIn(sub, 90)
    }

    private fun onboardingRoot() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(28), dp(30), dp(28), dp(30))
        setBackgroundColor(bg)
    }

    private fun showRolePicker() {
        val scroll = baseScroll()
        val root = scroll.getChildAt(0) as LinearLayout
        root.addView(text("Кто ты будешь сегодня?", 28f, Color.WHITE, true))
        gap(root, 9)
        root.addView(text("Выбери режим — его можно сменить позже.", 15f, muted))
        gap(root, 24)

        val title = root.getChildAt(0) as TextView
        title.gravity = Gravity.CENTER
        val subtitle = root.getChildAt(2) as TextView
        subtitle.gravity = Gravity.CENTER

        val host = roleCard("📱", "Просто Хост", "Создай код и принимай подключения") {
            showMain("Просто Хост")
        }
        val friend = roleCard("🔗", "Подключение к другу", "Введи код, который дал друг") {
            showMain("Подключение к другу")
        }
        root.addView(host); gap(root, 12); root.addView(friend)
        setContentView(scroll)
        animateIn(root.getChildAt(0)); animateIn(host, 70); animateIn(friend, 130)
    }

    private fun roleCard(icon: String, title: String, desc: String, click: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(cardBg, 20)
            isClickable = true
            setOnClickListener { click() }
            val i = text(icon, 28f); addView(i, LinearLayout.LayoutParams(dp(48), dp(60)))
            val col = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(title, 17f, Color.WHITE, true)); gap(this, 5); addView(text(desc, 13f, muted))
            }
            addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            addView(text("›", 30f, muted))
        }

    private fun baseScroll() = ScrollView(this).apply {
        setBackgroundColor(bg)
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(44), dp(22), dp(40))
        })
    }

    private fun showMain(role: String) {
        val scroll = baseScroll()
        val root = scroll.getChildAt(0) as LinearLayout
        val isFriend = role == "Подключение к другу"

        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        header.addView(text("TPaPCC", 32f, Color.WHITE, true))
        gap(header, 5)
        header.addView(text(role, 14f, accent, true))
        root.addView(header); gap(root, 20)

        val hero = card()
        hero.addView(text("Доверенное подключение", 21f, Color.WHITE, true))
        gap(hero, 7)
        hero.addView(text("Все действия видимы владельцу. Доступ можно остановить в любой момент.", 14f, muted))
        gap(hero, 14)
        status = text("● Регистрация устройства…", 15f, muted, true)
        hero.addView(status)
        root.addView(hero); gap(root, 14)

        run {
            val pair = card()
            pair.addView(text(if (isFriend) "Подключение к другу" else "Создание подключения", 18f, Color.WHITE, true))
            gap(pair, 8)

            if (isFriend) {
                val code = EditText(this).apply {
                    hint = "Введи 6-значный код друга"
                    inputType = 2
                    textSize = 16f
                    setSingleLine()
                    setTextColor(Color.WHITE)
                    setHintTextColor(Color.rgb(120, 125, 138))
                    background = rounded(fieldBg, 14)
                    setPadding(dp(16), 0, dp(16), 0)
                }
                pair.addView(code, LinearLayout.LayoutParams(-1, dp(54))); gap(pair, 9)
                pair.addView(button("🔗 Подключиться") {
                    if (code.text.length != 6) {
                        status.text = "Введите ровно 6 цифр"
                        return@button
                    }
                    TpaPccApi.redeemCode(this, code.text.toString()) { ok, result ->
                        status.text = if (ok) "● Устройство сопряжено" else "● Ошибка сопряжения: ${result ?: "unknown"}"
                    }
                }, LinearLayout.LayoutParams(-1, dp(52)))
            } else {
                val codeView = text("Код появится здесь", 30f, Color.WHITE, true).apply {
                    gravity = Gravity.CENTER
                    letterSpacing = .12f
                    setPadding(0, dp(8), 0, dp(8))
                }
                pair.addView(codeView, LinearLayout.LayoutParams(-1, dp(68)))
                gap(pair, 4)
                val hint = text("Создай код — он останется видимым до подключения друга.", 13f, muted)
                pair.addView(hint)
                gap(pair, 10)
                pair.addView(button("✨ Создать код") {
                    TpaPccApi.createCode(this) { ok, result ->
                        val c = try { org.json.JSONObject(result ?: "").optString("code") } catch (_: Exception) { "" }
                        if (ok && c.isNotBlank()) {
                            codeView.text = c
                            hint.text = "Код активен • действует 10 минут • жди подключения друга"
                            status.text = "● Ожидаем подключение друга"
                            animateIn(codeView)
                            animateIn(hint, 70)
                        } else {
                            status.text = "● Не удалось создать код"
                        }
                    }
                }, LinearLayout.LayoutParams(-1, dp(52)))
            }

            root.addView(pair); gap(root, 14)

            val test = card()
            test.addView(text("Проверка приложения", 18f, Color.WHITE, true))
            gap(test, 7)
            test.addView(text("Если под рукой только один телефон, можно проверить сервер, запрос сессии и подтверждение без второго устройства.", 14f, muted))
            gap(test, 10)
            test.addView(button("🧪 Тест на этом телефоне") {
                TpaPccApi.selfTestSession(this) { ok, result ->
                    if (ok) status.text = "● Тестовая сессия создана — подтверди запрос"
                    else status.text = "● Ошибка тестовой сессии: ${result ?: "unknown"}"
                }
            }, LinearLayout.LayoutParams(-1, dp(52)))
            root.addView(test); gap(root, 14)

            val session = card()
            session.addView(text("Сессия", 18f, Color.WHITE, true)); gap(session, 5)
            session.addView(text("Максимальная длительность — 24 часа.", 14f, muted)); gap(session, 9)
            duration = Spinner(this)
            duration.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
                arrayOf("15 минут", "30 минут", "1 час", "2 часа", "3 часа", "6 часов", "12 часов", "24 часа"))
            session.addView(duration, LinearLayout.LayoutParams(-1, dp(52))); gap(session, 8)
            timerView = text("", 22f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
            session.addView(timerView, LinearLayout.LayoutParams(-1, dp(42)))
            session.addView(button("▶ Запустить локальную сессию") { startSession(duration.selectedItemPosition) },
                LinearLayout.LayoutParams(-1, dp(52)))
            gap(session, 8)
            session.addView(button("■ Завершить доступ") { stopSession() }, LinearLayout.LayoutParams(-1, dp(52)))
            session.visibility = View.GONE
            connectedSessionPanel = session
            root.addView(session); gap(root, 14)

            val actions = card()
            actions.addView(text("Функции и разрешения", 18f, Color.WHITE, true)); gap(actions, 9)
            val actionList = if (isFriend) {
                listOf(
                    "🎙 Микрофон" to { sendRemoteControl("microphone") },
                    "🔔 Уведомления" to { sendRemoteControl("notifications") },
                    "📺 Доступ к экрану" to { sendRemoteControl("screen") },
                    "📸 Скриншот" to { sendRemoteControl("screenshot") },
                    "🖐 Accessibility" to { sendRemoteControl("accessibility") },
                    "☀️ Яркость" to { sendRemoteControl("brightness") },
                    "🔊 Громкость" to { sendRemoteControl("volume") }
                )
            } else {
                listOf(
                    "🎙 Микрофон" to { requestMic() },
                    "🔔 Уведомления" to { requestNotifications() },
                    "📺 Доступ к экрану" to { requestScreen() },
                    "📸 Скриншот" to { requestScreen() },
                    "🖐 Accessibility" to { requestAccessibility() },
                    "☀️ Яркость" to { adjustLocalBrightness() },
                    "🔊 Громкость" to { adjustLocalVolume() }
                )
            }
            actionList.forEachIndexed { index, pairAction ->
                actions.addView(button(pairAction.first, pairAction.second), LinearLayout.LayoutParams(-1, dp(50)))
                if (index != actionList.lastIndex) gap(actions, 7)
            }
            actions.visibility = View.GONE
            connectedActionsPanel = actions
            root.addView(actions); gap(root, 14)

            val caps = card()
            caps.addView(text("Возможности", 18f, Color.WHITE, true)); gap(caps, 7)
            caps.addView(text("БЕЗ ROOT / SHIZUKU", 13f, accent, true)); gap(caps, 5)
            caps.addView(text("✓ экран и запись экрана\\n✓ скриншоты\\n✓ микрофон\\n✓ системный выбор файлов\\n✓ уведомления\\n✓ громкость и доступные настройки\\n✓ Accessibility после включения владельцем", 14f, Color.rgb(225, 227, 235)))
            gap(caps, 12)
            caps.addView(text("ROOT / РАСШИРЕННЫЙ", 13f, Color.rgb(255, 173, 74), true)); gap(caps, 5)
            caps.addView(text("• расширенная файловая система\\n• системные настройки\\n• пакеты и службы\\n• shell-команды\\n• расширенные сетевые настройки", 14f, Color.rgb(225, 227, 235)))
            gap(caps, 8)
            caps.addView(text("Root-функции — только отдельным явным режимом.", 13f, muted))
            root.addView(caps)

            root.post {
                TpaPccApi.sessionStatus(this) { ok, obj ->
                    val approved = ok && obj?.optJSONObject("session")?.isNull("approved_at") == false
                    if (approved) revealConnectedControls(session, actions)
                }
            }
        }

        val footer = TextView(this).apply {
            text = "TPaPCC — Trusted Phone and PC Connection"
            textSize = 12f; setTextColor(Color.rgb(105, 110, 124)); gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, 0)
        }
        root.addView(footer)
        setContentView(scroll)
        animateIn(header)
        animateIn(hero, 70)
        animateIn(root.getChildAt(2), 130)
    }

    private fun revealConnectedControls(session: View, actions: View) {
        if (session.visibility == View.VISIBLE && actions.visibility == View.VISIBLE) return
        session.visibility = View.VISIBLE
        actions.visibility = View.VISIBLE
        session.alpha = 0f
        session.translationY = dp(24).toFloat()
        actions.alpha = 0f
        actions.translationY = dp(24).toFloat()
        session.animate().alpha(1f).translationY(0f).setDuration(420).setInterpolator(DecelerateInterpolator()).start()
        actions.animate().alpha(1f).translationY(0f).setStartDelay(90).setDuration(420).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun showApprovalDialog(sessionId: String) {
        AlertDialog.Builder(this)
            .setTitle("Запрос на подключение")
            .setMessage("Доверенное устройство запрашивает удалённую сессию.\n\nРазрешение открывает заявленные функции: экран, скриншоты, микрофон, файлы и системные функции. Разрешай только ожидаемое подключение.")
            .setNegativeButton("Отклонить") { _, _ -> TpaPccApi.endSession(this, sessionId) { _, _ -> } }
            .setPositiveButton("Разрешить экран") { _, _ ->
                activeSessionId = sessionId
                TpaPccApi.approveSession(this, sessionId) { ok, _ ->
                    if (ok) { if (::status.isInitialized) status.text = "● Подключение разрешено"; beep(true); requestScreen() }
                    else if (::status.isInitialized) status.text = "● Не удалось разрешить сессию"
                }
            }.setCancelable(false).show()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val c = NotificationChannel("tpaPcc", "TPaPCC services", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
    }
    private fun beep(success: Boolean) { tone?.startTone(if (success) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, 120) }
    private fun requestMic() { if (Build.VERSION.SDK_INT >= 23) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10) }
    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        else Toast.makeText(this, "На этой версии Android отдельное разрешение не требуется", Toast.LENGTH_SHORT).show()
    }
    private fun requestScreen() {
        val m = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(m.createScreenCaptureIntent(), 20)
    }

    private fun startScreenCaptureForegroundService(onReady: () -> Unit) {
        val i = Intent(this, ScreenCaptureService::class.java)
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                if (resultCode == 1) onReady()
                else if (::status.isInitialized) status.text = "● Сервис трансляции экрана не запустился"
            }
        }
        i.putExtra("ready", receiver)
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        } catch (e: Exception) {
            if (::status.isInitialized) status.text = "● Не удалось запустить сервис экрана: " + (e.message ?: "ошибка")
        }
    }
    private fun pickFile() {
        startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) })
    }
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 20) {
            if (res != RESULT_OK || data == null) {
                if (::status.isInitialized) status.text = "● Доступ к экрану не разрешён"
                return
            }
            status.text = "● Запускаем трансляцию экрана…"
            val captureData = data
            startScreenCaptureForegroundService {
                try {
                    if (activeSessionId.isBlank()) {
                        status.text = "● Экран разрешён, но активной удалённой сессии нет"
                        return@startScreenCaptureForegroundService
                    }
                    webRtcSession?.stop()
                    webRtcSession = WebRtcSession(
                        this,
                        activeSessionId,
                        { s -> if (::status.isInitialized) status.text = "● " + s },
                        { control -> handleRemoteControl(control) }
                    )
                    webRtcSession?.startScreen(captureData)
                } catch (e: Exception) {
                    if (::status.isInitialized) status.text = "● Не удалось запустить экран: " + (e.message ?: "ошибка")
                }
            }
        }
    }
    private fun startSession(i: Int) {
        val m = intArrayOf(15, 30, 60, 120, 180, 360, 720, 1440)[i]
        timer?.cancel()
        status.text = "● Сессия активна"
        timer = object : CountDownTimer(m * 60000L, 1000) {
            override fun onTick(x: Long) { timerView.text = "%02d:%02d:%02d".format(x / 3600000, (x / 60000) % 60, (x / 1000) % 60) }
            override fun onFinish() { timerView.text = "00:00:00"; status.text = "● Сессия завершена" }
        }.start()
    }
    private fun sendRemoteControl(command: String) {
        if (activeSessionId.isBlank()) {
            if (::status.isInitialized) status.text = "● Нет активного подключения"
            return
        }
        val payload = org.json.JSONObject().put("command", command)
        TpaPccApi.sendSignal(this, activeSessionId, "control", payload) { ok, _ ->
            if (::status.isInitialized) {
                status.text = if (ok) "● Команда отправлена другу" else "● Не удалось отправить команду"
            }
        }
    }

    private fun adjustLocalBrightness() {
        try {
            val resolver = contentResolver
            val value = android.provider.Settings.System.getInt(resolver, android.provider.Settings.System.SCREEN_BRIGHTNESS)
            android.provider.Settings.System.putInt(resolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, value)
            if (::status.isInitialized) status.text = "● Яркость управляется системой"
        } catch (_: Exception) {
            if (::status.isInitialized) status.text = "● Для яркости нужен системный доступ"
        }
    }

    private fun adjustLocalVolume() {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, current, 0)
        if (::status.isInitialized) status.text = "● Громкость управляется системой"
    }

    private fun handleRemoteControl(control: org.json.JSONObject) {
        when (control.optString("command")) {
            "microphone" -> requestMic()
            "screen" -> requestScreen()
            "files" -> pickFile()
            "accessibility" -> requestAccessibility()
            "brightness" -> startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            "volume" -> startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
            "notifications" -> requestNotifications()
            "screenshot" -> requestScreen()
            else -> if (::status.isInitialized) status.text = "● Неизвестная команда"
        }
    }

    private fun requestAccessibility() {
        AlertDialog.Builder(this)
            .setTitle("Нужен Accessibility")
            .setMessage("Для этой функции Android требует специальный доступ Accessibility (Специальные возможности). Без него TPaPCC не сможет выполнить команду удалённого управления. Включение выполняется только тобой вручную в системных настройках.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Открыть настройки") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .show()
    }

    override fun onDestroy() { tone?.release(); tone = null; timer?.cancel(); webRtcSession?.stop(); super.onDestroy() }

    private fun stopSession() {
        timer?.cancel(); timerView.text = ""; webRtcSession?.stop(); webRtcSession = null
        if (activeSessionId.isNotBlank()) {
            val id = activeSessionId; activeSessionId = ""
            TpaPccApi.endSession(this, id) { _, _ -> }
        }
        status.text = "● Доступ остановлен"
        beep(false)
        stopService(Intent(this, ScreenCaptureService::class.java))
        stopService(Intent(this, MicrophoneService::class.java))
    }
}
