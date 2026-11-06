package pl.capri.mobile;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import java.util.Random;

/** Transparent desktop pet. Controls live in a long-press menu and notification. */
public class AvatarOverlayService extends Service {
    static boolean running;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final PetMotion pet=new PetMotion(new Random());
    private WindowManager windows; private WindowManager.LayoutParams position; private WebView avatar;
    private boolean visible=true,dragged,menuShown; private long last; private int insetLeft,insetTop;
    private VelocityTracker velocity; private float downX,downY,startX,startY; private String lastState="";
    private float density(){return getResources().getDisplayMetrics().density;}
    private int dp(int value){return Math.round(value*density());}
    private double now(){return SystemClock.elapsedRealtime()/1000.0;}
    private void bounds(){
        WindowMetrics metrics=windows.getCurrentWindowMetrics();Rect area=metrics.getBounds();
        Insets insets=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        insetLeft=insets.left;insetTop=insets.top;
        pet.bounds((area.width()-insets.left-insets.right-position.width)/density(),(area.height()-insets.top-insets.bottom-position.height)/density());
    }
    private void draw(){
        position.x=insetLeft+Math.round(pet.x*density());position.y=insetTop+Math.round(pet.y*density());
        try{windows.updateViewLayout(avatar,position);}catch(IllegalArgumentException ignored){}
        String state="{moving:"+pet.moving+",left:"+pet.left+",dragging:"+pet.dragging+",falling:"+pet.falling+",act:'"+pet.act+"'}";
        if(!state.equals(lastState)){lastState=state;avatar.evaluateJavascript("window.capriPet?.("+state+")",null);}
    }
    private final Runnable tick=new Runnable(){public void run(){
        if(!running)return;long time=SystemClock.elapsedRealtime();float delta=Math.min(.1f,(time-last)/1000f);last=time;
        if(visible){bounds();pet.step(time/1000.0,delta);draw();}
        handler.postDelayed(this,visible?42:1000);
    }};
    private void open(boolean mic){startActivity(new Intent(this,MainActivity.class).putExtra("mic",mic).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));}
    private final Runnable menu=()->{
        if(dragged||!running)return;menuShown=true;
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Capri")
            .setItems(new String[]{"Otwórz rozmowę","Porozmawiaj przez mikrofon","Schowaj Capri"},(d,index)->{if(index==2)stopSelf();else open(index==1);}).create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        dialog.setOnDismissListener(d->{pet.tap(now());lastState="";});dialog.show();
    };
    @Override public void onCreate(){super.onCreate();
        NotificationManager notifications=getSystemService(NotificationManager.class);notifications.createNotificationChannel(new NotificationChannel("capri-avatar","Capri na ekranie",NotificationManager.IMPORTANCE_LOW));
        PendingIntent launch=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,AvatarOverlayService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(7,new Notification.Builder(this,"capri-avatar").setSmallIcon(R.drawable.ic_capri).setContentTitle("Capri na ekranie")
            .setContentText("Przeciągnij postać. Przytrzymaj, aby otworzyć menu.").setContentIntent(launch)
            .addAction(new Notification.Action.Builder(null,"Schowaj Capri",stop).build()).setOngoing(true).build());
        windows=getSystemService(WindowManager.class);avatar=AssetWebView.create(this);
        avatar.addJavascriptInterface(new Object(){@android.webkit.JavascriptInterface public void ready(){handler.post(()->{if(running){lastState="";draw();}});}},"PetNative");
        avatar.setBackgroundColor(Color.TRANSPARENT);avatar.setOverScrollMode(View.OVER_SCROLL_NEVER);avatar.setVerticalScrollBarEnabled(false);avatar.setHorizontalScrollBarEnabled(false);
        position=new WindowManager.LayoutParams(dp(144),dp(218),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        position.gravity=Gravity.TOP|Gravity.LEFT;bounds();pet.x=Math.min(20,pet.maxX);pet.y=pet.maxY;
        avatar.setOnTouchListener((view,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    dragged=false;menuShown=false;downX=event.getRawX();downY=event.getRawY();startX=pet.x;startY=pet.y;
                    pet.grab();lastState="";velocity=VelocityTracker.obtain();velocity.addMovement(event);handler.postDelayed(menu,550);return true;
                case MotionEvent.ACTION_MOVE:
                    if(velocity!=null)velocity.addMovement(event);
                    if(Math.hypot(event.getRawX()-downX,event.getRawY()-downY)>dp(8)){dragged=true;handler.removeCallbacks(menu);}
                    if(dragged&&!menuShown){pet.drag(startX+(event.getRawX()-downX)/density(),startY+(event.getRawY()-downY)/density());draw();}return true;
                case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(menu);
                    if(!menuShown){if(dragged&&event.getActionMasked()==MotionEvent.ACTION_UP&&velocity!=null){velocity.addMovement(event);velocity.computeCurrentVelocity(1000);pet.drop(velocity.getXVelocity()/density(),velocity.getYVelocity()/density());}else pet.tap(now());}
                    if(velocity!=null){velocity.recycle();velocity=null;}lastState="";return true;
                default:return false;
            }
        });
        avatar.loadUrl(AssetWebView.HOME.replace("index.html","pet.html"));
        registerReceiver(screen,new IntentFilter(){ {addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_SCREEN_ON);} });
        try{windows.addView(avatar,position);running=true;last=SystemClock.elapsedRealtime();handler.post(tick);}catch(RuntimeException error){stopSelf();}
    }
    private final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){visible=!Intent.ACTION_SCREEN_OFF.equals(intent.getAction());if(avatar!=null){avatar.setVisibility(visible?View.VISIBLE:View.GONE);if(visible){avatar.onResume();lastState="";}else avatar.onPause();}}};
    @Override public int onStartCommand(Intent intent,int flags,int id){if(intent!=null){if("stop".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}if("state".equals(intent.getAction())&&avatar!=null){String state=intent.getStringExtra("state");avatar.evaluateJavascript("window.capriEvent?.('status',{state:"+org.json.JSONObject.quote(state==null?"idle":state)+"})",null);}}return START_NOT_STICKY;}
    @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);if(position!=null){bounds();draw();}}
    @Override public void onDestroy(){running=false;handler.removeCallbacksAndMessages(null);if(velocity!=null)velocity.recycle();try{unregisterReceiver(screen);}catch(IllegalArgumentException ignored){}if(avatar!=null){try{windows.removeView(avatar);}catch(IllegalArgumentException ignored){}avatar.destroy();}stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
