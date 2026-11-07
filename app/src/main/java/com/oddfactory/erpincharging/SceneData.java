package com.oddfactory.erpincharging;

import android.content.Context;
import android.graphics.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** The same complete PNGs and 30fps commands used by the reviewed web renderer. */
final class SceneData {
    static volatile SceneData shared;
    final Bitmap[] sprites;
    final Draw[][] frames;
    final String[] names,phases;
    final float[] times,mouthX,mouthY;
    final int[] bob,depth;
    final java.util.Map<String,Integer> ids=new HashMap<>();
    final RectF[][] oralRuns=new RectF[6][];
    final float[] oralLeft=new float[6];
    final Paint ink = new Paint();
    private final RectF drawBounds=new RectF();
    private final Path cakeMouth=new Path();
    final RectF inputExtent=new RectF();
    private final RectF inputDrawBounds=new RectF();
    private final Matrix inputMatrix=new Matrix();
    static final int WIDTH=640, HEIGHT=400, FPS=30;
    static final class Draw {
        int id; RectF bounds; RectF clip; Path mouth; float rotation,flight=-1,individual;
    }
    static synchronized SceneData load(Context context) throws Exception {
        if(shared==null) shared=new SceneData(context.getApplicationContext());
        return shared;
    }
    private SceneData(Context context) throws Exception {
        JSONObject data=new JSONObject(read(context,"data/scene.json"));
        JSONArray files=data.getJSONArray("assets"), raw=data.getJSONArray("frames");
        sprites=new Bitmap[files.length()]; frames=new Draw[raw.length()][];
        names=new String[sprites.length];phases=new String[frames.length];times=new float[frames.length];mouthX=new float[frames.length];mouthY=new float[frames.length];bob=new int[frames.length];depth=new int[frames.length];
        for(int i=0;i<names.length;i++){String file=files.getString(i);names[i]=data.getJSONArray("roles").getString(i);ids.put(names[i],i);}
        JSONObject masks=data.getJSONObject("oralMasks");
        for(int i=0;i<6;i++){JSONObject mask=masks.getJSONObject(Integer.toString(i));oralLeft[i]=(float)mask.getDouble("left");JSONArray rows=mask.getJSONArray("runs");oralRuns[i]=new RectF[rows.length()];for(int j=0;j<rows.length();j++)oralRuns[i][j]=rect(rows.getJSONArray(j));}
        BitmapFactory.Options options=new BitmapFactory.Options(); options.inScaled=false;
        for(int i=0;i<sprites.length;i++)try(InputStream stream=context.getAssets().open(files.getString(i))){
            if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
            sprites[i]=BitmapFactory.decodeStream(stream,null,options);
            if(sprites[i]==null)throw new IOException("Invalid sprite "+i);
            sprites[i].setDensity(Bitmap.DENSITY_NONE);
        }
        for(int f=0;f<frames.length;f++){
            if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
            JSONObject info=data.getJSONArray("frameInfo").getJSONObject(f);phases[f]=info.getString("phase");times[f]=(float)info.getDouble("localTime");bob[f]=info.optInt("bob",0);depth[f]=info.optInt("depth",-1);JSONObject mouth=info.optJSONObject("mouth");if(mouth!=null){mouthX[f]=(float)mouth.getDouble("x");mouthY[f]=(float)mouth.getDouble("y");}
            JSONArray list=raw.getJSONArray(f); frames[f]=new Draw[list.length()];
            for(int i=0;i<list.length();i++){
                JSONObject v=list.getJSONObject(i); Draw d=new Draw(); d.id=v.getInt("id");
                float x=(float)v.getDouble("x"),y=(float)v.getDouble("y");
                d.bounds=new RectF(x,y,x+(float)v.getDouble("w"),y+(float)v.getDouble("h"));
                d.rotation=(float)(v.optDouble("rotation",0)*180/Math.PI);
                d.flight=(float)v.optDouble("flightProgress",-1);d.individual=(float)v.optDouble("individualProgress",0);
                if(v.has("clip"))d.clip=rect(v.getJSONArray("clip"));
                if(v.has("mouthClip")){
                    JSONObject oral=v.getJSONObject("mouthClip"); d.mouth=new Path();
                    d.mouth.addRect(-200,0,(float)oral.getDouble("left"),HEIGHT,Path.Direction.CW);
                    JSONArray runs=oral.getJSONArray("runs");
                    for(int j=0;j<runs.length();j++)d.mouth.addRect(rect(runs.getJSONArray(j)),Path.Direction.CW);
                }
                frames[f][i]=d;
            }
        }
        ink.setAntiAlias(false); ink.setFilterBitmap(false); ink.setDither(false);
        maskInk.setColorFilter(new PorterDuffColorFilter(Color.WHITE,PorterDuff.Mode.SRC_IN));eraseInk.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }
    private static RectF rect(JSONArray a)throws JSONException {
        float x=(float)a.getDouble(0),y=(float)a.getDouble(1);
        return new RectF(x,y,x+(float)a.getDouble(2),y+(float)a.getDouble(3));
    }
    static String read(Context context,String file)throws IOException{
        try(InputStream in=context.getAssets().open(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            return out.toString("UTF-8");
        }
    }
    void paint(Canvas canvas,int frame){
        canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
        for(Draw d:frames[Math.floorMod(frame,frames.length)]){
            int saved=canvas.save();
            if(d.clip!=null)canvas.clipRect(d.clip);
            if(d.mouth!=null)canvas.clipPath(d.mouth);
            if(d.rotation!=0)canvas.rotate(d.rotation,d.bounds.centerX(),d.bounds.centerY());
            blit(canvas,d.id,d.bounds);canvas.restoreToCount(saved);
        }
    }

    private boolean inputPass;
    private final Paint maskInk=new Paint(),eraseInk=new Paint();
    private void blit(Canvas c,int id,RectF bounds){
        Paint p=ink;
        if(inputPass)p=names[id].startsWith("a")||names[id].startsWith("b")?eraseInk:maskInk;
        if(inputPass&&p==maskInk){inputDrawBounds.set(bounds);c.getMatrix(inputMatrix);inputMatrix.mapRect(inputDrawBounds);inputExtent.union(inputDrawBounds);}
        c.drawBitmap(sprites[id],null,bounds,p);
    }
    void paintInput(Canvas c,int f,int width,float anchor){inputExtent.setEmpty();inputPass=true;try{paintResponsive(c,f,width,anchor);}finally{inputPass=false;}}
    void paintInputAtOrigin(Canvas c,int f,int width,float origin){inputExtent.setEmpty();inputPass=true;try{paintResponsiveAtOrigin(c,f,width,origin);}finally{inputPass=false;}}
    static float clamp(float n){return Math.max(0,Math.min(1,n));}
    static float smooth(float n){n=clamp(n);return n*n*(3-2*n);}
    static float noise(int n){double v=Math.sin(n*127.1+311.7)*43758.5453;return (float)(v-Math.floor(v));}
    static float fieldNoise(double n){double v=Math.sin(n*127.1+311.7)*43758.5453;return (float)(v-Math.floor(v));}
    void draw(Canvas canvas,Draw d,float x,float y,int replacement){
        int saved=canvas.save();canvas.translate(x,y);
        if(d.clip!=null)canvas.clipRect(d.clip);if(d.mouth!=null)canvas.clipPath(d.mouth);
        if(d.rotation!=0)canvas.rotate(d.rotation,d.bounds.centerX(),d.bounds.centerY());
        blit(canvas,replacement<0?d.id:replacement,d.bounds);canvas.restoreToCount(saved);
    }
    /** Extend the scenery to physical display edges without scaling the actor. */
    void paintResponsive(Canvas canvas,int frame,int width,float anchor){
        paintResponsiveAtOrigin(canvas,frame,width,Math.round(Math.max(0,width-640)*clamp(anchor)));
    }
    void paintResponsiveAtOrigin(Canvas canvas,int frame,int width,float origin){
        canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);int f=Math.floorMod(frame,frames.length);
        float t=times[f];String phase=phases[f];Draw body=null;
        if(phase.equals("macaron")){paintMacaron(canvas,f,width,origin);return;}
        if(phase.equals("suction")){paintFieldSuction(canvas,f,width,origin);return;}
        int chew=0;
        if(phase.equals("cake")){
            float first=(mouthX[f]-15)/224,cycle=((t-first)%.5f+.5f)%.5f;
            chew=t<first?0:cycle<.12?0:cycle<.16?1:cycle<.23?2:cycle<.29?3:cycle<.34?4:cycle<.40?2:cycle<.445?3:5;
        }
        for(Draw d:frames[f]){
            String name=names[d.id];float dx=origin;int replacement=-1;
            if((phase.equals("cake")||phase.equals("fall"))&&name.equals("b01"))continue;
            if(phase.equals("cake")&&(name.startsWith("d")||name.startsWith("c"))){body=d;if(name.startsWith("d"))replacement=ids.get("d"+chew);else dx+=origin*(1-smooth(t/.9f));}
            if(phase.equals("bread")&&d.flight>=0)dx-=origin*(1-d.flight)*(1-d.flight);
            if(phase.equals("intro"))dx+=(name.equals("a14")?width-640-origin:-origin)*(1-smooth(t/.45f));
            if(phase.equals("fall")){
                if(name.equals("b00")||name.equals("b01"))dx-=origin*(1-(float)Math.pow(1-clamp(t/.32f),3));
                if(name.equals("a14"))dx+=(width-640-origin)*(1-(float)Math.pow(1-clamp(t/.32f),3));
            }
            draw(canvas,d,dx,0,replacement);
            if(name.equals("b00")&&d.clip!=null&&d.clip.height()<100){
                float step=d.clip.width();for(int k=1;k<=Math.ceil(origin/Math.max(1,step))+1;k++){
                    int saved=canvas.save();canvas.translate(dx-k*step,0);canvas.clipRect(d.clip);blit(canvas,d.id,d.bounds);canvas.restoreToCount(saved);
                }
            }
        }
        if(phase.equals("cake")&&body!=null)responsiveCakes(canvas,f,origin,body,chew,t,true);
        if(phase.equals("fall")){
            Draw endBody=null;for(Draw d:frames[200])if(names[d.id].startsWith("d"))endBody=d;
            if(endBody!=null){int saved=canvas.save();canvas.translate(-(780+origin)*(1-(float)Math.pow(1-clamp(t/.32f),3)),0);responsiveCakes(canvas,200,origin,endBody,0,6.2f,false);canvas.restoreToCount(saved);}
        }
    }
    static final int[] ROW_WIDTH={48,66,90,120,158},ROW_TOP={232,251,283,318,354},HEAD_ROW={0,2,4};
    static final float[] HEAD_OFFSETS={-.72f,-.24f,.24f,.72f};
    static final class Food {int id,row,seed;float cx,bottom,width,height,start,delay,exponent,hop,spin,skew,dx,dy,rotation;}
    static final class FoodField extends ArrayList<Food> {Food[][] byRow;float suctionP=Float.NaN;}
    final Map<Long,List<Food>> fields=new LinkedHashMap<>(4,.75f,true);
    int rows(int height){return 5+(int)Math.ceil(Math.max(0,height-400)/(158*.56f));}
    FoodField field(int width,int height){
        long key=((long)width<<32)|height;FoodField list=(FoodField)fields.get(key);if(list!=null)return list;list=new FoodField();
        // One continuous row coordinate system across the entire display.
        // No640px sections, copied section borders, or clipped food silhouettes.
        for(int row=0;row<rows(height);row++){float w=ROW_WIDTH[Math.min(4,row)],step=w*.60f,top=row<5?ROW_TOP[row]:354+(row-4)*158*.56f;for(int j=-1;j<=Math.ceil(width/step);j++){
            Food food=new Food();food.row=row;food.id=ids.get(Math.floorMod(j+row+10,2)==0?"b06":"b07");food.cx=j*step+(row%2==1?step/2:0);food.width=w;food.height=w*sprites[food.id].getHeight()/sprites[food.id].getWidth();food.bottom=top+food.height+noise(j+row*9)*3;food.seed=j+row*1009;
            food.start=.43f+fieldNoise(food.cx+food.row*41)*.28f;int i=food.seed;food.delay=noise(i+3)*.12f;food.exponent=1.45f+noise(i+18)*1.2f;food.hop=18+noise(i+51)*70;food.spin=(noise(i+71)>.5?1:-1)*(1.2f+noise(i+91)*2.6f);food.skew=noise(i+118)*2-1;list.add(food);
        }}
        list.byRow=new Food[rows(height)][];for(int row=0;row<list.byRow.length;row++){int count=0;for(Food food:list)if(food.row==row)count++;Food[] members=new Food[count];int at=0;for(Food food:list)if(food.row==row)members[at++]=food;list.byRow[row]=members;}
        if(fields.size()>=4)fields.remove(fields.keySet().iterator().next());fields.put(key,list);return list;
    }
    void centered(Canvas c,int id,float x,float bottom,float width){float height=width*sprites[id].getHeight()/sprites[id].getWidth();int w=Math.round(width),h=Math.round(height),left=Math.round(x-width/2),top=Math.round(bottom-height);drawBounds.set(left,top,left+w,top+h);blit(c,id,drawBounds);}
    void banks(Canvas c,int width,float down){for(int x=170-340;x<width+420;x+=340)centered(c,ids.get("b09"),x,465+down,420);}
    void paintMacaron(Canvas c,int f,int width,float origin){
        float t=times[f];FoodField foods=field(width,c.getHeight());
        if(t<1.35f){
            for(Draw d:frames[f])if(names[d.id].startsWith("h"))draw(c,d,origin,0,-1);
            for(int x=170-340,k=0;x<width+420;x+=340,k++){float p=clamp((t-.56f-noise(k+73)*.14f)/.42f);if(p>0)centered(c,ids.get("b09"),x,-100+565*p*p,420);}
            for(int index=0;index<foods.size();index++){Food food=foods.get(index);float p=clamp((t-food.start)/.43f);if(p>0)centered(c,food.id,food.cx,-100+(food.bottom+100)*p*p,food.width);}
            int count=Math.round(18*width/640f);for(int i=0;i<count;i++){float start=noise(i+3)*.20f,u=clamp((t-start)/.70f);if(u>0&&u<1)centered(c,ids.get(i%2==0?"b06":"b07"),20+noise(i*5+7)*(width-40),-80+500*u*u,58+noise(i)*25);}
            coveringDrops(c,t,origin);
            return;
        }
        banks(c,width,0);int headRow=depth[f]<0?-1:HEAD_ROW[depth[f]];
        for(int row=0;row<rows(c.getHeight());row++){
            if(row==headRow)for(Draw d:frames[f])if(names[d.id].startsWith("i")){
                float line=d.clip==null?ROW_TOP[row]:d.clip.bottom-3,w=ROW_WIDTH[row];
                for(float offset:HEAD_OFFSETS){int id=ids.get(offset>0?"b07":"b06");float h=w*sprites[id].getHeight()/sprites[id].getWidth();centered(c,id,origin+d.bounds.centerX()+offset*w,line+h*.60f,w);}
                draw(c,d,origin,0,-1);
                for(Draw bite:frames[f]){String name=names[bite.id];if(name.equals("b06")||name.equals("b07")){boolean standard=false;for(int rw:ROW_WIDTH)if(Math.abs(bite.bounds.width()-rw)<.1)standard=true;if(!standard)draw(c,bite,origin,0,-1);}}
            }
            for(Food food:foods.byRow[row])centered(c,food.id,food.cx,food.bottom,food.width);
        }
        coveringDrops(c,t,origin);
    }
    void coveringDrops(Canvas c,float t,float origin){
        // These complete foreground foods cover the crown before the hidden
        // body is replaced, then settle down into the pile without disappearing.
        if(t<.74f||t>1.8f)return;Draw last=null;for(Draw d:frames[445])if(names[d.id].equals("h04"))last=d;if(last==null)return;
        float center=origin+last.bounds.centerX()+35;
        for(int i=0;i<3;i++){float age=t-(.74f+i*.04f);if(age<0)continue;float u=clamp(age/.5f),after=Math.max(0,age-.5f),bottom=-100+375*u*u+200*after+1600*after*after;centered(c,ids.get(i%2==0?"b06":"b07"),center-50+i*60,bottom,158);}
    }
    void prepareSuction(FoodField foods,float p,int width){
        if(Float.floatToIntBits(foods.suctionP)==Float.floatToIntBits(p))return;
        for(int index=0;index<foods.size();index++){Food food=foods.get(index);float q=clamp((p-food.delay)/(1-food.delay));food.dx=(width+960)*(float)Math.pow(q,food.exponent);food.dy=-(float)Math.sin(q*Math.PI)*(food.hop+Math.min(50,food.width*.12f))+food.skew*22*q*q;food.rotation=food.spin*360*smooth(q);}
        foods.suctionP=p;
    }
    void suckedFood(Canvas c,Food food){
        int saved=c.save();c.rotate(food.rotation,food.cx+food.dx,food.bottom-food.height/2+food.dy);centered(c,food.id,food.cx+food.dx,food.bottom+food.dy,food.width);c.restoreToCount(saved);
    }
    void paintFieldSuction(Canvas c,int f,int width,float origin){
        float p=clamp(times[f]);if(p<.14f)banks(c,width,700*smooth(p/.14f));
        int headRow=depth[f]<0?2:HEAD_ROW[depth[f]];FoodField foods=field(width,c.getHeight());prepareSuction(foods,p,width);
        for(int row=0;row<rows(c.getHeight());row++){
            if(p<.24f&&row==headRow)for(Draw d:frames[f])if(names[d.id].startsWith("i")||names[d.id].startsWith("j"))draw(c,d,origin+(width-640)*(float)Math.pow(p,2.05),0,-1);
            for(Food food:foods.byRow[row])suckedFood(c,food);
        }
        if(p>=.24f)for(Draw d:frames[f])if(names[d.id].startsWith("j"))draw(c,d,origin+(width-640)*(float)Math.pow(p,2.05),0,-1);
    }
    void responsiveCakes(Canvas canvas,int f,float origin,Draw body,int chew,float t,boolean clip){
        float mx=origin+mouthX[f];Path mouth=cakeMouth;mouth.rewind();
        float left=origin+body.bounds.left+oralLeft[chew]*.655f;
        mouth.addRect(-200,0,left,400,Path.Direction.CW);
        for(RectF row:oralRuns[chew])mouth.addRect(origin+body.bounds.left+row.left*.655f,body.bounds.top+row.top*.655f,origin+body.bounds.left+row.right*.655f,body.bounds.top+row.bottom*.655f,Path.Direction.CW);
        int saved=canvas.save();if(clip)canvas.clipPath(mouth);
        for(int i=0;i<=Math.floor(t/.5f);i++){
            float edge=cakeEdge(t-i*.5f,origin,mouthX[f]),distance=edge-mx;if(distance>=18)continue;
            float approach=clamp((distance+36)/36),swallow=clamp(distance/18),scale=(1-.62f*approach)*(1-swallow);
            int w=Math.round(40*scale),h=Math.round(33*scale);if(w<2||h<2)continue;
            int x=Math.round(edge+8*approach*approach-w),y=Math.round(mouthY[f]-6.5f+3.5f*approach-h/2f+bob[f]*approach);
            drawBounds.set(x,y,x+w,y+h);blit(canvas,ids.get("b01"),drawBounds);
        }
        canvas.restoreToCount(saved);
    }
    static float cakeEdge(float age,float origin,float sourceMouth){
        // Cover the extra screen distance early, then meet the original belt
        // velocity before the final80px. Mouth timing and500ms births stay fixed.
        float q=clamp(age/((sourceMouth-80)/224));return 224*age+origin*(2*q-q*q);
    }
}
