package com.oddfactory.erpincharging;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

final class FeastView extends View {
    final SceneData data;
    Bitmap surface=Bitmap.createBitmap(640,400,Bitmap.Config.ARGB_8888);
    Canvas logical=new Canvas(surface);
    final Paint pixels=new Paint();
    long origin=SystemClock.uptimeMillis();
    int fixedFrame=-1, last=-1;
    boolean playing=true;
    boolean responsive=false;int sceneSize=640;float anchor=.5f;
    Runnable frameReady;
    Bitmap mask,physicalMask;int[] maskPixels;
    Canvas maskCanvas,physicalMaskCanvas;
    final Rect outputBounds=new Rect(),maskBounds=new Rect();
    final int[] inputRegion=new int[4];
    float actorOrigin=Float.NaN;
    int inputFrame=-1,inputWidth,inputHeight,inputViewWidth,inputViewHeight,inputCell,inputOrigin;
    java.util.List<int[]> cachedCells;
    boolean held;
    FeastView(Context context,SceneData scene){super(context);data=scene;pixels.setFilterBitmap(false);pixels.setAntiAlias(false);setContentDescription("에르핀 먹방");}
    void restart(){origin=SystemClock.uptimeMillis();last=-1;playing=true;invalidate();}
    void setPlaying(boolean value){playing=value;if(value)invalidate();}
    void hold(){if(!held){fixedFrame=currentFrame();held=true;playing=false;}}
    void release(){if(held){origin=SystemClock.uptimeMillis()-fixedFrame*1000L/30;fixedFrame=-1;held=false;playing=true;invalidate();}}
    java.util.List<int[]> inputCells(int cell){
        if(getWidth()<1||getHeight()<1)return java.util.Collections.emptyList();
        if(mask==null||mask.getWidth()!=surface.getWidth()||mask.getHeight()!=surface.getHeight()){if(mask!=null)mask.recycle();mask=Bitmap.createBitmap(surface.getWidth(),surface.getHeight(),Bitmap.Config.ARGB_8888);}
        int frame=last>=0?last:currentFrame(),originBits=Float.floatToIntBits(actorOrigin);
        if(cachedCells!=null&&inputFrame==frame&&inputWidth==surface.getWidth()&&inputHeight==surface.getHeight()&&inputViewWidth==getWidth()&&inputViewHeight==getHeight()&&inputCell==cell&&inputOrigin==originBits)return cachedCells;
        if(maskCanvas==null||maskCanvas.getWidth()!=mask.getWidth()||maskCanvas.getHeight()!=mask.getHeight())maskCanvas=new Canvas(mask);
        if(Float.isNaN(actorOrigin))data.paintInput(maskCanvas,frame,surface.getWidth(),anchor);else data.paintInputAtOrigin(maskCanvas,frame,surface.getWidth(),actorOrigin);
        if(physicalMask==null||physicalMask.getWidth()!=getWidth()||physicalMask.getHeight()!=getHeight()){if(physicalMask!=null)physicalMask.recycle();physicalMask=Bitmap.createBitmap(Math.max(1,getWidth()),Math.max(1,getHeight()),Bitmap.Config.ARGB_8888);}
        physicalMask.eraseColor(Color.TRANSPARENT);float scale=Math.min(getWidth()/(float)mask.getWidth(),getHeight()/(float)mask.getHeight());
        if(physicalMaskCanvas==null||physicalMaskCanvas.getWidth()!=physicalMask.getWidth()||physicalMaskCanvas.getHeight()!=physicalMask.getHeight())physicalMaskCanvas=new Canvas(physicalMask);
        maskBounds.set(0,0,Math.round(mask.getWidth()*scale),Math.round(mask.getHeight()*scale));physicalMaskCanvas.drawBitmap(mask,null,maskBounds,pixels);
        inputFrame=frame;inputWidth=surface.getWidth();inputHeight=surface.getHeight();inputViewWidth=getWidth();inputViewHeight=getHeight();inputCell=cell;inputOrigin=originBits;
        RectF support=data.inputExtent;
        if(support.isEmpty())return cachedCells=java.util.Collections.emptyList();
        float sx=maskBounds.right/(float)mask.getWidth(),sy=maskBounds.bottom/(float)mask.getHeight();
        OpaqueCells.region(support.left,support.top,support.right,support.bottom,sx,sy,getWidth(),getHeight(),cell,inputRegion);int left=inputRegion[0],top=inputRegion[1],right=inputRegion[2],bottom=inputRegion[3];
        int width=right-left,height=bottom-top;if(width<=0||height<=0)return cachedCells=java.util.Collections.emptyList();
        if(maskPixels==null||maskPixels.length<width*height)maskPixels=new int[width*height];
        physicalMask.getPixels(maskPixels,0,width,left,top,width,height);
        java.util.List<int[]> cells=OpaqueCells.rectanglesPrefix(maskPixels,width,height,cell);
        for(int[] r:cells){r[0]+=left;r[2]+=left;r[1]+=top;r[3]+=top;}
        return cachedCells=cells;
    }
    void viewport(int size,float position){responsive=true;sceneSize=size;anchor=position;actorOrigin=Float.NaN;cachedCells=null;last=-1;invalidate();}
    void viewportOrigin(int size,float origin,float position){responsive=true;sceneSize=size;anchor=position;actorOrigin=origin;cachedCells=null;last=-1;invalidate();}
    int currentFrame(){return fixedFrame>=0?fixedFrame:(int)((SystemClock.uptimeMillis()-origin)*30/1000)%data.frames.length;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);int f=currentFrame();int width=responsive?Math.max(640,Math.round(getWidth()*640f/Math.max(1,sceneSize))):640;
        int height=responsive?Math.max(400,Math.round(getHeight()*640f/Math.max(1,sceneSize))):400;
        if(surface.getWidth()!=width||surface.getHeight()!=height){surface.recycle();surface=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);logical=new Canvas(surface);last=-1;}
        boolean changed=f!=last;
        if(changed){if(responsive){if(Float.isNaN(actorOrigin))data.paintResponsive(logical,f,width,anchor);else data.paintResponsiveAtOrigin(logical,f,width,actorOrigin);}else data.paint(logical,f);last=f;}
        float scale=Math.min(getWidth()/(float)width,getHeight()/(float)height);int w=Math.round(width*scale),h=Math.round(height*scale),x=(getWidth()-w)/2,y=responsive?0:(getHeight()-h)/2;
        outputBounds.set(x,y,x+w,y+h);canvas.drawBitmap(surface,null,outputBounds,pixels);
        if(changed&&frameReady!=null)frameReady.run();
        if(playing&&fixedFrame<0){long elapsed=Math.max(0,SystemClock.uptimeMillis()-origin),next=((elapsed*30/1000+1)*1000+29)/30;postInvalidateDelayed(Math.max(1,next-elapsed));}
    }
    @Override protected void onDetachedFromWindow(){playing=false;super.onDetachedFromWindow();}
}
