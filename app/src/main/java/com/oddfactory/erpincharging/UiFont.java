package com.oddfactory.erpincharging;
import android.content.Context;
import android.graphics.Typeface;
import android.view.*;
import android.widget.TextView;
final class UiFont {
 private static Typeface regular,heavy;
 static synchronized Typeface face(Context c,boolean bold){
  if(regular==null){regular=Typeface.createFromAsset(c.getAssets(),"fonts/Maplestory-Light.ttf");heavy=Typeface.createFromAsset(c.getAssets(),"fonts/Maplestory-Bold.ttf");}
  return bold?heavy:regular;
 }
 static Typeface light(Context c){return face(c,false);}
 static Typeface bold(Context c){return face(c,true);}
 static void apply(View v){if(v instanceof TextView){TextView t=(TextView)v;t.setTypeface(face(v.getContext(),t.getTypeface()!=null&&t.getTypeface().isBold()));}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)apply(g.getChildAt(i));}}
}
