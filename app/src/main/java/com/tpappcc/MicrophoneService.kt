package com.tpappcc
import android.app.*; import android.content.*; import android.content.pm.ServiceInfo; import android.media.*; import android.os.*
class MicrophoneService:Service(){
 private var recorder:AudioRecord?=null; private var running=false
 override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int{
  val n=Notification.Builder(this,"tpaPcc").setContentTitle("TPaPCC").setContentText("Микрофон активен").setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
  if(Build.VERSION.SDK_INT>=29)startForeground(21,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)else startForeground(21,n)
  if(running)return START_NOT_STICKY
  val r=16000; recorder=AudioRecord(MediaRecorder.AudioSource.MIC,r,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,r*2); recorder?.startRecording();running=true
  Thread{val b=ByteArray(2048);while(running){recorder?.read(b,0,b.size)}}.start();return START_NOT_STICKY
 }
 override fun onDestroy(){running=false;recorder?.stop();recorder?.release();recorder=null;super.onDestroy()}
 override fun onBind(i:Intent?):IBinder?=null
}