package com.oddfactory.erpincharging;

final class ReleaseModel {
    static boolean attachAllowed(boolean requested,boolean trustedConnected,boolean ownerMatches){return requested&&trustedConnected&&ownerMatches;}
    static int permissionStep(boolean requested,boolean foreground,boolean displayReady,boolean notificationGranted,boolean notificationAsked){
        if(!requested||!foreground)return 0;
        if(!displayReady)return 1;
        return notificationGranted||notificationAsked?3:2;
    }
    static boolean show(boolean enabled,boolean plugged,boolean hidden,boolean preview,boolean awake,boolean allowed,boolean loaded){return ((enabled&&plugged&&!hidden)||preview)&&awake&&allowed&&loaded;}
    static int size(int preferred,float density,int width,int safeHeight){return Math.max(1,Math.min(preferred,Math.min(width,Math.round(safeHeight*640f/400))));}
    static int y(int top,int bottom,int baseHeight,float fraction){return top+Math.round(Math.max(0,bottom-top-baseHeight)*clamp(fraction));}
    static float clamp(float n){return Float.isFinite(n)?Math.max(0,Math.min(1,n)):.5f;}
    static final class Gesture {
        boolean down,held,moved;long at;float x,y;
        void begin(float px,float py,long now){down=true;held=moved=false;x=px;y=py;at=now;}
        void hold(long now,long timeout){if(down&&!moved&&now-at>=timeout)held=true;}
        void move(float px,float py,float slop){if(Math.hypot(px-x,py-y)>slop)moved=true;}
        void cancel(){down=held=moved=false;}
    }
}
