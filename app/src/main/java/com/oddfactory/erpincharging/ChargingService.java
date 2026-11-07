package com.oddfactory.erpincharging;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.os.*;

/** Battery/foreground authority; it never creates a display or input window. */
public class ChargingService extends Service {
    static ChargingService instance;
    static final String ENABLE="enable",PREVIEW="preview",STOP="stop";
    final Handler handler=new Handler(Looper.getMainLooper());
    SceneData data;Thread loader;
    boolean enabled,plugged,hidden,registered,destroyed,displayFailure;
    long previewUntil,pauseAt;
    final Runnable end=()->reconcile(false);
    SharedPreferences prefs(){return getSharedPreferences("feast",0);}
    final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
        if(Intent.ACTION_BATTERY_CHANGED.equals(i.getAction())){
            boolean now=i.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;
            if(plugged&&!now)cancelPreview();if(now!=plugged)hidden=false;plugged=now;
        }
        if(Intent.ACTION_SCREEN_OFF.equals(i.getAction()))cancelPreview();
        reconcile(false);
    }};
    @Override public void onCreate(){
        super.onCreate();instance=this;
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("feast","충전 먹방",NotificationManager.IMPORTANCE_LOW));
        startForeground(18,notification("충전 먹방 준비 중"),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        IntentFilter filter=new IntentFilter(Intent.ACTION_BATTERY_CHANGED);filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);
        Intent battery=registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);registered=true;plugged=battery!=null&&battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;
        loader=new Thread(()->{try{SceneData loaded=SceneData.load(this);handler.post(()->{if(!destroyed){data=loaded;if(previewUntil>0)preview();reconcile(true);}});}catch(Exception error){handler.post(()->{if(!destroyed)requestStop();});}},"scene-load");loader.start();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?ENABLE:intent.getAction();
        if(STOP.equals(action)){requestStop();return START_NOT_STICKY;}
        enabled=prefs().getBoolean("enabled",false);displayFailure=false;
        if(PREVIEW.equals(action)){hidden=false;preview();}reconcile(PREVIEW.equals(action));return START_NOT_STICKY;
    }
    void preview(){previewUntil=SystemClock.uptimeMillis()+21400;pauseAt=0;scheduleEnd();}
    void scheduleEnd(){handler.removeCallbacks(end);if(previewUntil>0&&pauseAt==0)handler.postDelayed(end,Math.max(1,previewUntil-SystemClock.uptimeMillis()+10));}
    void cancelPreview(){previewUntil=pauseAt=0;handler.removeCallbacks(end);}
    void pausePreview(){if(previewUntil>SystemClock.uptimeMillis()&&pauseAt==0){pauseAt=SystemClock.uptimeMillis();handler.removeCallbacks(end);}}
    void resumePreview(){if(pauseAt>0&&previewUntil>0)previewUntil+=SystemClock.uptimeMillis()-pauseAt;pauseAt=0;scheduleEnd();}
    boolean previewActive(){return previewUntil>(pauseAt>0?pauseAt:SystemClock.uptimeMillis());}
    boolean awake(){return getSystemService(PowerManager.class).isInteractive()&&!getSystemService(KeyguardManager.class).isKeyguardLocked();}
    boolean wantsDisplay(){return !destroyed&&!displayFailure&&instance==this&&ReleaseModel.show(enabled,plugged,hidden,previewActive(),awake(),TrustedDisplayService.ready(),data!=null);}
    boolean isShowing(){TrustedDisplayService h=TrustedDisplayService.instance;return h!=null&&h.isShowing(this);}
    void setEnabled(boolean value){enabled=value;prefs().edit().putBoolean("enabled",value).apply();reconcile(false);}
    void hideForConnection(){hidden=true;cancelPreview();reconcile(false);}
    void requestStop(){enabled=false;prefs().edit().putBoolean("enabled",false).apply();cancelPreview();TrustedDisplayService h=TrustedDisplayService.instance;if(h!=null)h.hideScene();stopSelf();}
    void displayUnavailable(){cancelPreview();reconcile(false);}
    void displayFailed(){displayFailure=true;cancelPreview();reconcile(false);}
    void reconcile(boolean restart){
        if(destroyed)return;
        TrustedDisplayService host=TrustedDisplayService.instance;boolean show=wantsDisplay();
        if(host!=null){if(show)host.showScene(this,data,restart);else host.hideScene();}
        String message=displayFailure?"표시창을 열지 못했어요. 앱에서 다시 실행해 주세요.":!TrustedDisplayService.ready()?"앱에서 캐릭터 표시 연결을 확인해 주세요.":isShowing()?"에르핀이 먹는 중":enabled?"충전 연결을 기다리는 중":"한 바퀴 보기 완료";
        getSystemService(NotificationManager.class).notify(18,notification(message));
        if(!enabled&&!previewActive()&&data!=null)stopSelf();
    }
    Notification notification(String text){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,FeastService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"feast").setSmallIcon(R.drawable.ic_status).setContentTitle("에르핀 충전 먹방").setContentText(text).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).addAction(new Notification.Action.Builder(null,"종료",stop).build()).build();
    }
    void refresh(){TrustedDisplayService h=TrustedDisplayService.instance;if(h!=null&&h.isShowing(this))h.refresh();reconcile(false);}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);refresh();}
    @Override public void onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);if(loader!=null)loader.interrupt();if(registered)unregisterReceiver(receiver);TrustedDisplayService h=TrustedDisplayService.instance;if(h!=null)h.hideScene();if(instance==this)instance=null;stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
