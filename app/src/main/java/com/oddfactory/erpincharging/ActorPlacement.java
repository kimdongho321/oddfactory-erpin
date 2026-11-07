package com.oddfactory.erpincharging;

/** Fixed reviewed cake/body registration, not the transparent640x400 canvas center. */
final class ActorPlacement {
    static final float CENTER_X=434.5f,CENTER_Y=226.5f;
    static final float BODY_LEFT=105,BODY_RIGHT=624;
    static float origin(int displayWidth,int size,int left,int safeWidth,float fraction){return originAt(left+safeWidth*ReleaseModel.clamp(fraction),size,left,safeWidth);}
    private static float originAt(float centerX,int size,int left,int safeWidth){float wanted=Math.round(centerX*640f/size-CENTER_X),lo=(float)Math.ceil(left*640f/size-BODY_LEFT),hi=(float)Math.floor((left+safeWidth)*640f/size-BODY_RIGHT);return lo<=hi?Math.max(lo,Math.min(hi,wanted)):wanted;}
    static float fraction(float origin,int size,int left,int safeWidth){return ReleaseModel.clamp(((origin+CENTER_X)*size/640f-left)/Math.max(1,safeWidth));}
    static int centeredY(int size,int top,int bottom,float fraction){int height=Math.round(size*400f/640);return Math.max(top,Math.min(bottom-height,Math.round(top+(bottom-top)*ReleaseModel.clamp(fraction)-CENTER_Y*size/640f)));}
    static float verticalFraction(int y,int size,int top,int bottom){return ReleaseModel.clamp((y+CENTER_Y*size/640f-top)/Math.max(1,bottom-top));}
    static float initialOrigin(int width,int size,int left,int safeWidth,boolean hasPosition,boolean relative,float storedX){
        if(!hasPosition)return originAt(width/2f,size,left,safeWidth);
        return !relative?Math.round(Math.max(0,Math.max(640,Math.round(width*640f/size))-640)*ReleaseModel.clamp(storedX)):origin(width,size,left,safeWidth,storedX);
    }
    static int initialY(int size,int top,int bottom,boolean hasPosition,boolean relative,float storedY){
        int height=Math.round(size*400f/640);
        if(!hasPosition)return ReleaseModel.y(top,bottom,height,1f);
        return relative?centeredY(size,top,bottom,storedY):ReleaseModel.y(top,bottom,height,storedY);
    }
}
