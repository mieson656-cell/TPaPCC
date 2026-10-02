package com.tpappcc
import android.app.*; import android.content.*; import android.content.pm.ServiceInfo; import android.media.projection.MediaProjection; import android.media.projection.MediaProjectionManager; import android.os.* 
class ScreenCaptureService: Service(){
 private var projection:MediaProjection?=null
 override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int{
  val n=Notification.Builder(this,"tpaPcc").setContentTitle("TPaPCC").setContentText("Трансляция экрана активна").setSmallIcon(android.R.drawable.ic_menu_view).build()
  if(Build.VERSION.SDK_INT>=29) startForeground(20,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(20,n)
  val code=i?.getIntExtra("resultCode",-1)?:-1; val data=i?.getParcelableExtra<Intent>("data")
  if(code==-1||data==null){stopSelf();return START_NOT_STICKY}
  val m=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  projection=m.getMediaProjection(code,data); projection?.registerCallback(object:MediaProjection.Callback(){override fun onStop(){stopSelf()}},null)
  return START_NOT_STICKY
 }
 override fun onDestroy(){projection?.stop();projection=null;super.onDestroy()}
 override fun onBind(i:Intent?):IBinder?=null
}