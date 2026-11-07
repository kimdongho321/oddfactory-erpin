package com.oddfactory.erpincharging;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.view.*;
import java.util.*;

final class TouchWindows {
    interface Touch{boolean accept(MotionEvent e);}
    private final TrustedDisplayService host;private final Context context;private final Touch touch;
    private final List<View> views=new ArrayList<>();private final List<Rect> bounds=new ArrayList<>();private View owner;
    TouchWindows(TrustedDisplayService owner,Touch t){host=owner;context=owner;touch=t;}
    void update(List<int[]> rectangles,int x,int y,Rect safe){
        if(owner!=null)return;
        List<Rect> next=new ArrayList<>();
        for(int[] r:rectangles){Rect p=new Rect(x+r[0],y+r[1],x+r[2],y+r[3]);if(p.intersect(safe))next.add(p);}
        for(int i=views.size()-1;i>=next.size();i--){host.detach(views.remove(i));bounds.remove(i);}
        for(int i=0;i<next.size();i++){
            Rect r=next.get(i);
            if(i<views.size()&&bounds.get(i).equals(r))continue;
            WindowManager.LayoutParams p=new WindowManager.LayoutParams(r.width(),r.height(),WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,android.graphics.PixelFormat.TRANSLUCENT);
            p.gravity=Gravity.TOP|Gravity.LEFT;p.x=r.left;p.y=r.top;p.setFitInsetsTypes(0);
            if(i==views.size()){
                View v=new View(context){
                    @SuppressLint("ClickableViewAccessibility")@Override public boolean onTouchEvent(MotionEvent e){
                        int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN&&owner==null)owner=this;if(owner!=this)return true;
                        boolean result=touch.accept(e);if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)owner=null;return result;
                    }
                    @Override public boolean performClick(){super.performClick();return true;}
                };
                host.attach(v,p);views.add(v);bounds.add(r);
            }else if(!bounds.get(i).equals(r)){host.updateOwned(views.get(i),p);bounds.set(i,r);}
        }
    }
    void clear(){owner=null;for(View v:views)host.detach(v);views.clear();bounds.clear();}
}

