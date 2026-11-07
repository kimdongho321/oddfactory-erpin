package com.oddfactory.erpincharging;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Dialog;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.*;
import java.util.*;

/** Display host only. ChargingService is the sole playback authority. */
public final class TrustedDisplayService extends AccessibilityService {
    static volatile TrustedDisplayService instance;
    private boolean accepting;
    private WindowManager windows;
    private final Set<View> owned=Collections.newSetFromMap(new IdentityHashMap<>());
    private ChargingService controller;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WindowManager.LayoutParams layout;private FeastView view;private SceneData data;private TouchWindows inputs;
    private Dialog menu;private TapSequence taps;private float grabAnchor;private int grabY;
    private final ReleaseModel.Gesture gesture=new ReleaseModel.Gesture();
    private final Runnable mask=this::updateInput;
    private final Runnable hold=()->{gesture.hold(SystemClock.uptimeMillis(),ViewConfiguration.getLongPressTimeout());if(gesture.held&&taps!=null)taps.cancel();};
    static boolean ready(){TrustedDisplayService s=instance;return s!=null&&s.accepting;}
    boolean isShowing(ChargingService c){return controller==c&&view!=null;}
    private SharedPreferences prefs(){return getSharedPreferences("feast",0);}
    @Override protected void onServiceConnected(){
        super.onServiceConnected();taps=new TapSequence(new TapSequence.Scheduler(){public void post(Runnable r,long delay){handler.postDelayed(r,delay);}public void remove(Runnable r){handler.removeCallbacks(r);}},ViewConfiguration.getDoubleTapTimeout(),ViewConfiguration.get(this).getScaledDoubleTapSlop(),()->{},this::showMenu);windows=getSystemService(WindowManager.class);
        AccessibilityServiceInfo info=getServiceInfo();
        accepting=false;
        if(info!=null){info.eventTypes=0;info.flags=0;setServiceInfo(info);accepting=info.getCapabilities()==0;}
        instance=this;
        if(inputs==null)inputs=new TouchWindows(this,this::touch);
        ChargingService c=ChargingService.instance;if(c!=null)c.reconcile(false);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent ignored){}
    private void lost(){
        accepting=false;if(instance==this)instance=null;ChargingService c=controller;hideScene();
        if(c==null)c=ChargingService.instance;if(c!=null)c.displayUnavailable();
    }
    @Override public void onInterrupt(){lost();}
    @Override public boolean onUnbind(Intent intent){lost();return super.onUnbind(intent);}
    @Override public void onDestroy(){lost();handler.removeCallbacksAndMessages(null);super.onDestroy();}
    private boolean authorized(){return ReleaseModel.attachAllowed(controller!=null&&controller.wantsDisplay(),accepting&&instance==this,controller!=null&&ChargingService.instance==controller);}
    void attach(View v,WindowManager.LayoutParams p){
        if(!authorized()||p.type!=WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)throw new IllegalStateException("Display host unavailable");
        windows.addView(v,p);owned.add(v);
    }
    void updateOwned(View v,WindowManager.LayoutParams p){
        if(!authorized()||!owned.contains(v)||p.type!=WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)throw new IllegalStateException("Display host unavailable");
        windows.updateViewLayout(v,p);
    }
    void detach(View v){if(owned.remove(v))try{windows.removeViewImmediate(v);}catch(IllegalArgumentException ignored){}}
    void showScene(ChargingService c,SceneData scene,boolean restart){
        if(instance!=this||!ready()||ChargingService.instance!=c||!c.wantsDisplay())return;
        if(controller!=null&&controller!=c)hideScene();controller=c;data=scene;
        if(view==null)add();else if(restart)view.restart();
    }
    void hideScene(){
        cancel();handler.removeCallbacks(mask);if(inputs!=null)inputs.clear();
        if(menu!=null){Dialog d=menu;menu=null;d.setOnDismissListener(null);d.dismiss();}
        if(view!=null){view.frameReady=null;view.setPlaying(false);detach(view);view=null;}
        for(View v:new ArrayList<>(owned))detach(v);controller=null;data=null;
    }
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);if(view!=null){if(authorized())refresh();else hideScene();}}
    Rect safe(){WindowMetrics m=windows.getCurrentWindowMetrics();Rect b=m.getBounds();Insets i=m.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());return new Rect(i.left,i.top,b.width()-i.right,b.height()-i.bottom);}
    void configure(){
        Rect safe=safe(),display=windows.getCurrentWindowMetrics().getBounds();
        int size=ReleaseModel.size(prefs().getInt("width",640),getResources().getDisplayMetrics().density,safe.width(),safe.height()),height=Math.round(size*400f/640);
        boolean hasPosition=prefs().contains("x"),relative=prefs().getBoolean("positionV3",false);
        if(hasPosition&&!relative&&!prefs().getBoolean("physicalOriginV2",false)){
            int previous=ReleaseModel.y(safe.top,safe.bottom,height,prefs().getFloat("y",.5f));int physical=Math.max(safe.top,Math.min(display.height()-height,previous+safe.top));
            prefs().edit().putFloat("y",(physical-safe.top)/(float)Math.max(1,safe.height()-height)).putBoolean("physicalOriginV2",true).apply();
        }
        layout.width=display.width();layout.x=0;layout.y=ActorPlacement.initialY(size,safe.top,safe.bottom,hasPosition,relative,prefs().getFloat("y",.5f));layout.height=display.height()-layout.y;
        float origin=ActorPlacement.initialOrigin(display.width(),size,safe.left,safe.width(),hasPosition,relative,prefs().getFloat("x",.5f));
        view.viewportOrigin(size,origin,ActorPlacement.fraction(origin,size,safe.left,safe.width()));
    }
    void add(){
        view=new FeastView(this,data);layout=new WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        layout.gravity=Gravity.TOP|Gravity.LEFT;layout.setFitInsetsTypes(0);layout.alpha=1;layout.setTitle("에르핀 충전 먹방");configure();
        view.frameReady=()->{handler.removeCallbacks(mask);handler.post(mask);};
        try{attach(view,layout);}catch(RuntimeException error){failed();}
    }
    void updateInput(){
        if(view==null||gesture.down)return;
        if(!authorized()){hideScene();return;}
        try{inputs.update(view.inputCells(Math.max(1,Math.round(6*getResources().getDisplayMetrics().density))),layout.x,layout.y,safe());}
        catch(RuntimeException error){failed();}
    }
    void failed(){ChargingService c=controller;hideScene();if(c!=null)c.displayFailed();}
    void pause(){if(view!=null){view.hold();if(controller!=null)controller.pausePreview();}}
    void resumeScene(){if(controller!=null)controller.resumePreview();if(view!=null)view.release();}
    boolean touch(MotionEvent e){
        if(view==null||!authorized())return true;
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                if(gesture.down||menu!=null&&menu.isShowing())return true;
                taps.down(SystemClock.uptimeMillis(),e.getRawX(),e.getRawY());gesture.begin(e.getRawX(),e.getRawY(),SystemClock.uptimeMillis());grabAnchor=view.anchor;grabY=layout.y;pause();handler.postDelayed(hold,ViewConfiguration.getLongPressTimeout());return true;
            case MotionEvent.ACTION_MOVE:
                if(!gesture.down)return true;gesture.hold(SystemClock.uptimeMillis(),ViewConfiguration.getLongPressTimeout());gesture.move(e.getRawX(),e.getRawY(),ViewConfiguration.get(this).getScaledTouchSlop());
                if(gesture.held){taps.cancel();Rect b=safe();int baseHeight=Math.round(view.sceneSize*400f/640);float nextX=ReleaseModel.clamp(grabAnchor+(e.getRawX()-gesture.x)/Math.max(1,b.width()));float nextOrigin=ActorPlacement.origin(layout.width,view.sceneSize,b.left,b.width(),nextX);view.viewportOrigin(view.sceneSize,nextOrigin,ActorPlacement.fraction(nextOrigin,view.sceneSize,b.left,b.width()));layout.y=Math.max(b.top,Math.min(b.bottom-baseHeight,grabY+Math.round(e.getRawY()-gesture.y)));layout.height=windows.getCurrentWindowMetrics().getBounds().height()-layout.y;updateOwned(view,layout);}return true;
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(hold);long now=SystemClock.uptimeMillis();
                if(gesture.down&&gesture.held&&gesture.moved){Rect b=safe();int h=Math.round(view.sceneSize*400f/640);prefs().edit().putFloat("x",view.anchor).putFloat("y",ActorPlacement.verticalFraction(layout.y,view.sceneSize,b.top,b.bottom)).putBoolean("positionV3",true).apply();taps.cancel();}
                else if(gesture.down&&!gesture.moved&&!gesture.held)taps.up(now);
                else taps.cancel();
                gesture.cancel();if(menu==null)resumeScene();return true;
            case MotionEvent.ACTION_POINTER_DOWN:case MotionEvent.ACTION_CANCEL:cancel();resumeScene();return true;
        }return true;
    }
    void cancel(){gesture.cancel();if(taps!=null)taps.cancel();handler.removeCallbacks(hold);}
    void showMenu(){
        if(!authorized())return;cancel();pause();if(menu!=null)menu.dismiss();menu=new Dialog(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(18*getResources().getDisplayMetrics().density);box.setPadding(pad,pad,pad,pad);
        TextView title=new TextView(this);title.setText("에르핀 충전 먹방");title.setTextSize(23);box.addView(title);
        LinearLayout settings=new LinearLayout(this);settings.setOrientation(LinearLayout.VERTICAL);settings.setVisibility(View.GONE);
        option(box,"설정",()->settings.setVisibility(settings.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));box.addView(settings);
        Switch auto=new Switch(this);auto.setText("충전할 때 자동으로 먹방");auto.setChecked(controller!=null&&controller.enabled);settings.addView(auto);auto.setOnCheckedChangeListener((v,on)->{if(controller!=null)controller.setEnabled(on);});
        for(int size:new int[]{480,640,960})option(settings,size==480?"크기 · 작게":size==640?"크기 · 기본":"크기 · 크게",()->{prefs().edit().putInt("width",size).apply();refresh();});
        option(settings,"위치 초기화",()->{resetPosition(prefs());refresh();});
        option(box,"이번 충전 연결에서 숨기기",()->{ChargingService c=controller;if(c!=null)c.hideForConnection();});
        option(box,"종료",()->{ChargingService c=controller;if(c!=null)c.requestStop();});option(box,"닫기",()->menu.dismiss());
        UiFont.apply(box);title.setTypeface(UiFont.bold(this));ScrollView scroll=new ScrollView(this);scroll.addView(box);menu.setContentView(scroll);menu.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);menu.setOnDismissListener(d->{menu=null;resumeScene();if(controller!=null)controller.reconcile(false);});menu.show();menu.getWindow().setLayout(Math.max(1,Math.min(Math.round(360*getResources().getDisplayMetrics().density),safe().width()-pad)),WindowManager.LayoutParams.WRAP_CONTENT);
    }
    void option(LinearLayout box,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setMinHeight(Math.round(48*getResources().getDisplayMetrics().density));b.setOnClickListener(v->action.run());box.addView(b,new LinearLayout.LayoutParams(-1,-2));}
    static void resetPosition(SharedPreferences settings){settings.edit().remove("x").remove("y").remove("positionV3").remove("physicalOriginV2").apply();}
    void refresh(){cancel();if(inputs!=null)inputs.clear();if(menu!=null)menu.dismiss();resumeScene();if(view!=null&&authorized()){configure();updateOwned(view,layout);}handler.post(mask);}
}
