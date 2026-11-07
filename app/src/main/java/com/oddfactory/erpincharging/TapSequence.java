package com.oddfactory.erpincharging;

/** A candidate DOWN suspends confirmation until UP or cancellation. */
final class TapSequence {
 interface Scheduler { void post(Runnable r,long delay); void remove(Runnable r); }
 private final Scheduler scheduler;private final long timeout;private final float slop;
 private final Runnable single,menu;
 private Runnable pending;private int count,serial;private boolean down;private long at;private float x,y;
 TapSequence(Scheduler scheduler,long timeout,float slop,Runnable single,Runnable menu){this.scheduler=scheduler;this.timeout=timeout;this.slop=slop;this.single=single;this.menu=menu;}
 void down(long now,float px,float py){
  int next=pending!=null&&now-at>=0&&now-at<=timeout&&Math.hypot(px-x,py-y)<=slop?count+1:1;
  cancel();count=next;down=true;x=px;y=py;
 }
 void up(long now){
  if(!down)return;down=false;at=now;
  if(count>=2){cancel();menu.run();return;}
  final int ticket=++serial;
  pending=()->{if(ticket!=serial)return;pending=null;count=0;single.run();};
  scheduler.post(pending,timeout);
 }
 void cancel(){if(pending!=null)scheduler.remove(pending);pending=null;count=0;down=false;serial++;}
}
