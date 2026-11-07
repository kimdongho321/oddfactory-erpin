package com.oddfactory.erpincharging;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class LauncherActivity extends Activity {
    final Handler handler=new Handler(Looper.getMainLooper());Thread loader;TextView state;Switch automatic;boolean updating,resumed,startIssued,notificationInFlight;FeastView preview;String pendingAction;AlertDialog permissionGuide;
    int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    final Runnable status=new Runnable(){@Override public void run(){if(resumed){continuePending();refresh();handler.postDelayed(this,1000);}}};
    android.content.SharedPreferences prefs(){return getSharedPreferences("feast",0);}
    TextView text(LinearLayout box,String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(0xff69483f);t.setPadding(0,dp(7),0,dp(7));box.addView(t);return t;}
    Button button(LinearLayout box,String label,Runnable action){Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setMinHeight(dp(50));b.setOnClickListener(v->action.run());box.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);if(saved!=null)pendingAction=saved.getString("pendingAction");getWindow().setDecorFitsSystemWindows(false);build();}
    void requestStart(String action){pendingAction=action;if(!TrustedDisplayService.ready())showDisplayGuide();else continuePending();refresh();}
    void continuePending(){
        if(notificationInFlight||permissionGuide!=null&&permissionGuide.isShowing())return;
        boolean granted=checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
        int step=ReleaseModel.permissionStep(pendingAction!=null,resumed,TrustedDisplayService.ready(),granted,prefs().getBoolean("notificationAsked",false));
        if(step==2){notificationInFlight=true;prefs().edit().putBoolean("notificationAsked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},18);return;}
        if(step!=3)return;
        String action=pendingAction;pendingAction=null;if(ChargingService.ENABLE.equals(action))prefs().edit().putBoolean("enabled",true).apply();
        startIssued=true;try{startForegroundService(new Intent(this,FeastService.class).setAction(action));}catch(RuntimeException error){startIssued=false;Toast.makeText(this,"앱을 열어 둔 상태에서 다시 실행해 주세요.",Toast.LENGTH_LONG).show();}
    }
    void stopAll(){pendingAction=null;startIssued=false;if(permissionGuide!=null)permissionGuide.dismiss();prefs().edit().putBoolean("enabled",false).apply();ChargingService c=ChargingService.instance;if(c!=null)c.requestStop();else stopService(new Intent(this,FeastService.class));refresh();}
    void showDisplayGuide(){
        if(permissionGuide!=null&&permissionGuide.isShowing())return;
        permissionGuide=new AlertDialog.Builder(this).setTitle("처음 한 번 · 캐릭터 표시 연결")
            .setMessage("Android 접근성 설정에서 ‘에르핀 캐릭터 표시’를 직접 켜 주세요.\n\n설치된 앱(또는 다운로드한 앱) → 에르핀 캐릭터 표시 → 사용\n\n이 연결은 그림과 캐릭터 입력 영역만 표시합니다. 다른 앱의 화면 글자·내용을 읽거나 대신 누르지 않습니다. 충전 연결은 별도의 충전 서비스가 감지합니다.\n\n허용 후 에르핀 앱으로 돌아오면 요청한 실행을 이어갑니다.")
            .setPositiveButton("접근성 설정 열기",(d,w)->{try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(ActivityNotFoundException error){pendingAction=null;Toast.makeText(this,"Android 접근성 설정에서 에르핀 캐릭터 표시를 찾아 주세요.",Toast.LENGTH_LONG).show();}})
            .setNegativeButton("나중에",(d,w)->{pendingAction=null;refresh();}).create();
        permissionGuide.setOnCancelListener(d->{pendingAction=null;refresh();});permissionGuide.setOnDismissListener(d->permissionGuide=null);permissionGuide.show();UiFont.apply(permissionGuide.getWindow().getDecorView());
    }
    void build(){
        if(loader!=null)loader.interrupt();if(preview!=null)preview.setPlaying(false);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xfffff9f1);LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);column.setPadding(dp(22),dp(16),dp(22),dp(24));scroll.addView(column);
        scroll.setOnApplyWindowInsetsListener((v,i)->{Insets e=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(e.left,e.top,e.right,e.bottom);return i;});
        TextView title=text(column,"에르핀 충전 먹방",28);text(column,"폰은 충전 중, 에르핀은 먹는 중!",16);
        Button execute=button(column,"실행",()->requestStart(ChargingService.ENABLE));
        GradientDrawable shape=new GradientDrawable();shape.setColor(0xffb64f4b);shape.setCornerRadius(dp(18));shape.setStroke(dp(2),0xff69483f);execute.setBackground(shape);execute.setTextColor(Color.WHITE);execute.setTextSize(30);execute.setMinHeight(dp(88));
        text(column,"충전기를 연결하면 먹방을 시작해요.\n두 번 톡 · 종료·설정·숨김 메뉴\n길게 누른 뒤 끌기 · 위치 이동",16);
        state=text(column,"충전 상태 확인 중",15);
        button(column,"한 바퀴 미리보기",()->requestStart(ChargingService.PREVIEW));
        button(column,"종료",this::stopAll);
        LinearLayout advanced=new LinearLayout(this);advanced.setOrientation(LinearLayout.VERTICAL);advanced.setVisibility(View.GONE);
        Button more=button(column,"더보기 · 설정과 사용법",()->{});more.setOnClickListener(v->{boolean show=advanced.getVisibility()!=View.VISIBLE;advanced.setVisibility(show?View.VISIBLE:View.GONE);more.setText(show?"접기 · 설정과 사용법":"더보기 · 설정과 사용법");});column.addView(advanced);
        automatic=new Switch(this);automatic.setText("충전할 때 자동으로 먹방");automatic.setMinHeight(dp(48));advanced.addView(automatic);automatic.setOnCheckedChangeListener((v,on)->{if(updating)return;if(on)requestStart(ChargingService.ENABLE);else stopAll();refresh();});
        text(advanced,"크기",18);for(int size:new int[]{480,640,960})button(advanced,size==480?"작게":size==640?"기본":"크게",()->{prefs().edit().putInt("width",size).apply();if(ChargingService.instance!=null)ChargingService.instance.refresh();});
        button(advanced,"위치 초기화",()->{TrustedDisplayService.resetPosition(prefs());if(ChargingService.instance!=null)ChargingService.instance.refresh();});
        button(advanced,"캐릭터 표시 연결 안내",this::showDisplayGuide);
        text(advanced,"충전기를 빼거나 화면을 끄고 잠그면 숨겨요.\n컨베이어 → 빵 → 마카롱으로 이어져요.\n재부팅 후에는 앱에서 다시 실행해 주세요.",14);
        FrameLayout stage=new FrameLayout(this);advanced.addView(stage,new LinearLayout.LayoutParams(-1,dp(200)));
        loader=new Thread(()->{try{SceneData data=SceneData.load(this);handler.post(()->{if(isDestroyed()||isFinishing())return;preview=new FeastView(this,data);preview.fixedFrame=100;preview.setPlaying(false);stage.addView(preview,new FrameLayout.LayoutParams(-1,-1));});}catch(Exception ignored){}},"preview-load");loader.start();
        text(advanced,"메이플스토리 제공 서체 적용 · © NEXON Korea\n서체 이용 안내는 패키지에 포함되어 있습니다.",13);
        UiFont.apply(column);title.setTypeface(UiFont.bold(this));execute.setTypeface(UiFont.bold(this));setContentView(scroll);refresh();
    }
    void refresh(){if(state==null)return;Intent b=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));boolean plugged=b!=null&&b.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;state.setText(!TrustedDisplayService.ready()?(pendingAction!=null?"캐릭터 표시 연결을 기다려요. 설정 후 앱으로 돌아와 주세요.":"실행하려면 캐릭터 표시 연결을 켜 주세요."):(plugged?"충전 연결":"충전 대기")+(ChargingService.instance!=null&&ChargingService.instance.isShowing()?" · 먹방 재생 중":" · 표시 연결됨"));updating=true;automatic.setChecked(prefs().getBoolean("enabled",false));updating=false;}
    @Override protected void onResume(){super.onResume();resumed=true;handler.removeCallbacks(status);handler.post(status);continuePending();if(pendingAction==null&&!startIssued&&prefs().getBoolean("enabled",false)&&TrustedDisplayService.ready()&&ChargingService.instance==null)requestStart(ChargingService.ENABLE);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==18){notificationInFlight=false;continuePending();refresh();}}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("pendingAction",pendingAction);}
    @Override protected void onPause(){resumed=false;handler.removeCallbacks(status);super.onPause();}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);build();}
    @Override protected void onDestroy(){if(permissionGuide!=null){permissionGuide.setOnDismissListener(null);permissionGuide.dismiss();permissionGuide=null;}if(loader!=null)loader.interrupt();handler.removeCallbacksAndMessages(null);if(preview!=null)preview.setPlaying(false);super.onDestroy();}
}


