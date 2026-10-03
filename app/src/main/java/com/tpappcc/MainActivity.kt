package com.tpappcc

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable

class MainActivity:Activity(){
 private var timer:CountDownTimer?=null
 private val sessionHandler=Handler(Looper.getMainLooper())
 private var sessionPolling=false
 private var lastPromptSession=""
 private var activeSessionId=""
 private var webRtcSession:WebRtcSession?=null
 private lateinit var status:TextView
 private lateinit var timerView:TextView
 private lateinit var duration:Spinner
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun t(v:String,s:Float,c:Int=Color.WHITE,b:Boolean=false)=TextView(this).apply{text=v;textSize=s;setTextColor(c);if(b)typeface=Typeface.DEFAULT_BOLD}
 private fun space(p:LinearLayout,h:Int)=p.addView(Space(this),LinearLayout.LayoutParams(1,dp(h)))
 private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(18),dp(20),dp(18));background=GradientDrawable().apply{setColor(Color.rgb(21,25,34));cornerRadius=dp(22).toFloat()}}
 private fun btn(v:String,click:()->Unit)=Button(this).apply{text=v;setTextColor(Color.WHITE);background=GradientDrawable().apply{setColor(Color.rgb(124,92,252));cornerRadius=dp(14).toFloat()};setOnClickListener{click()}}
 override fun onCreate(b:Bundle?){super.onCreate(b);createChannel();showHome();TpaPccApi.register(this,"Мой Android"){ok,_ -> if(ok) status.text="● Устройство подключено к TPaPCC" else status.text="● Ожидаем сеть"}}
 override fun onResume(){super.onResume();startSessionPolling()}
 override fun onPause(){super.onPause();stopSessionPolling()}
 private fun startSessionPolling(){if(sessionPolling)return;sessionPolling=true;sessionHandler.post(sessionPoll)}
 private fun stopSessionPolling(){sessionPolling=false;sessionHandler.removeCallbacks(sessionPoll)}
 private val sessionPoll=object:Runnable{override fun run(){if(!sessionPolling)return;TpaPccApi.sessionStatus(this@MainActivity){ok,obj->if(ok&&obj!=null){val s=obj.optJSONObject("session");if(s!=null){val id=s.optString("id");val approved=!s.isNull("approved_at");if(!approved&&id.isNotBlank()&&id!=lastPromptSession){lastPromptSession=id;showApprovalDialog(id)};if(approved){activeSessionId=id;status.text="● Удалённая сессия разрешена"}}}};sessionHandler.postDelayed(this,2000)}}
 private fun showApprovalDialog(sessionId:String){AlertDialog.Builder(this).setTitle("Запрос на подключение").setMessage("Доверенное устройство хочет подключиться.\n\nНажми «Разрешить», только если ты действительно ожидаешь это подключение.").setNegativeButton("Отклонить"){_,_->TpaPccApi.endSession(this,sessionId){_,_->}}.setPositiveButton("Разрешить экран"){_,_->activeSessionId=sessionId;TpaPccApi.approveSession(this,sessionId){ok,_->if(ok)requestScreen()else status.text="● Не удалось разрешить сессию"}}.setCancelable(false).show()}
 private fun createChannel(){if(Build.VERSION.SDK_INT>=26){val c=NotificationChannel("tpaPcc","TPaPCC services",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager::class.java).createNotificationChannel(c)}}
 private fun showHome(){
  val scroll=ScrollView(this);val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(28),dp(22),dp(28));setBackgroundColor(Color.rgb(11,13,18))}
  root.addView(t("TPaPCC",32f,Color.WHITE,true));root.addView(t("Trusted Phone and PC Connect",14f,Color.rgb(169,173,186)));space(root,20)
  val hero=card();hero.addView(t("Доверенное подключение",21f,Color.WHITE,true));space(hero,7);hero.addView(t("Все действия видимы владельцу телефона. Доступ можно остановить в любой момент.",14f,Color.rgb(169,173,186)));space(hero,15);status=t("● Регистрация устройства…",15f,Color.rgb(169,173,186),true);hero.addView(status);root.addView(hero);space(root,15)
  val pair=card();pair.addView(t("Сопряжение",18f,Color.WHITE,true));space(pair,8)
  val code=EditText(this).apply{hint="Введите 6-значный код";inputType=2;textSize=16f;setSingleLine();setTextColor(Color.WHITE);setHintTextColor(Color.rgb(120,125,138));background=GradientDrawable().apply{setColor(Color.rgb(29,34,48));cornerRadius=dp(14).toFloat()};setPadding(dp(16),0,dp(16),0)}
  pair.addView(code,LinearLayout.LayoutParams(-1,dp(54)));space(pair,9)
  pair.addView(btn("Подключиться"){if(code.text.length!=6){status.text="Введите ровно 6 цифр";return@btn};TpaPccApi.redeemCode(this,code.text.toString()){ok,result->status.text=if(ok)"● Устройство сопряжено" else "● Ошибка сопряжения: ${result ?: "unknown"}"}},LinearLayout.LayoutParams(-1,dp(52)));space(pair,7)
  pair.addView(btn("🔗 Создать код для Telegram"){TpaPccApi.createCode(this){ok,result->if(ok){val c=try{org.json.JSONObject(result?:"").optString("code")}catch(_:Exception){""};status.text=if(c.isNotBlank())"● Код Telegram: $c (10 минут)" else "● Код создан"}else status.text="● Не удалось создать код: ${result ?: "unknown"}"}},LinearLayout.LayoutParams(-1,dp(52)))
  root.addView(pair);space(root,15)
  val session=card();session.addView(t("Длительность сессии",18f,Color.WHITE,true));space(session,5);session.addView(t("Выбирается владельцем телефона.",14f,Color.rgb(169,173,186)));space(session,9)
  duration=Spinner(this);duration.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,arrayOf("15 минут","30 минут","1 час","2 часа","3 часа","6 часов","12 часов","24 часа"));session.addView(duration,LinearLayout.LayoutParams(-1,dp(52)));space(session,7)
  timerView=t("",22f,Color.WHITE,true).apply{gravity=Gravity.CENTER};session.addView(timerView,LinearLayout.LayoutParams(-1,dp(45)));session.addView(btn("Запустить локальную сессию"){startSession(duration.selectedItemPosition)},LinearLayout.LayoutParams(-1,dp(52)));space(session,7);session.addView(btn("Завершить доступ"){stopSession()},LinearLayout.LayoutParams(-1,dp(52)));root.addView(session);space(root,15)
  val actions=card();actions.addView(t("Разрешения и функции",18f,Color.WHITE,true));space(actions,9)
  actions.addView(btn("🎙 Разрешение микрофона"){requestMic()});space(actions,7);actions.addView(btn("🔔 Разрешение уведомлений"){requestNotifications()});space(actions,7);actions.addView(btn("📺 Запросить доступ к экрану"){requestScreen()});space(actions,7);actions.addView(btn("📁 Выбрать файл"){pickFile()});space(actions,7);actions.addView(btn("🖐 Открыть Accessibility"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))});space(actions,7);actions.addView(btn("☀️ Настройки яркости"){startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))});space(actions,7);actions.addView(btn("🔊 Настройки звука"){startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))});root.addView(actions);space(root,15)
  val caps=card();caps.addView(t("Возможности",18f,Color.WHITE,true));space(caps,7);caps.addView(t("БЕЗ ROOT / SHIZUKU",13f,Color.rgb(124,92,252),true));space(caps,5);caps.addView(t("✓ экран и запись экрана\n✓ скриншоты\n✓ микрофон\n✓ системный выбор файлов\n✓ фото/видео через системные API\n✓ уведомления\n✓ громкость и доступные настройки\n✓ Accessibility-управление после включения владельцем\n✓ батарея, сеть и состояние устройства",14f,Color.rgb(225,227,235)));space(caps,12);caps.addView(t("ROOT / РАСШИРЕННЫЙ",13f,Color.rgb(255,173,74),true));space(caps,5);caps.addView(t("• расширенная файловая система\n• дополнительные системные настройки\n• расширенное управление пакетами и службами\n• shell-команды устройства\n• расширенные сетевые настройки",14f,Color.rgb(225,227,235)));space(caps,8);caps.addView(t("Root-функции будут отдельным режимом и никогда не включаются скрытно.",13f,Color.rgb(169,173,186)));root.addView(caps)
  scroll.addView(root);setContentView(scroll)
 }
 private fun requestMic(){if(Build.VERSION.SDK_INT>=23)requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),10)}
 private fun requestNotifications(){if(Build.VERSION.SDK_INT>=33)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),11)else Toast.makeText(this,"На этой версии Android отдельное разрешение не требуется",Toast.LENGTH_SHORT).show()}
 private fun requestScreen(){val m=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager;startActivityForResult(m.createScreenCaptureIntent(),20)}
 private fun pickFile(){startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="*/*";addCategory(Intent.CATEGORY_OPENABLE)})}
 override fun onActivityResult(req:Int,res:Int,data:Intent?){super.onActivityResult(req,res,data);if(req==20&&res==RESULT_OK&&data!=null){val i=Intent(this,ScreenCaptureService::class.java).putExtra("resultCode",res).putExtra("data",data);if(Build.VERSION.SDK_INT>=26)startForegroundService(i)else startService(i);status.text="● Трансляция экрана активна"}}
 private fun startSession(i:Int){val m=intArrayOf(15,30,60,120,180,360,720,1440)[i];timer?.cancel();status.text="● Сессия активна";timer=object:CountDownTimer(m*60000L,1000){override fun onTick(x:Long){timerView.text="%02d:%02d:%02d".format(x/3600000,(x/60000)%60,(x/1000)%60)};override fun onFinish(){timerView.text="00:00:00";status.text="● Сессия завершена"}}.start()}
 private fun stopSession(){timer?.cancel();timerView.text="";webRtcSession?.stop();webRtcSession=null;if(activeSessionId.isNotBlank()){val id=activeSessionId;activeSessionId="";TpaPccApi.endSession(this,id){_,_->}};status.text="● Доступ остановлен";stopService(Intent(this,ScreenCaptureService::class.java));stopService(Intent(this,MicrophoneService::class.java))}
}