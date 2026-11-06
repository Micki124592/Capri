package pl.capri.mobile;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;

/** A user-started, bounded overlay. Walking is local and never calls the AI. */
public class AvatarOverlayService extends Service {
    static boolean running;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WindowManager windows; private WindowManager.LayoutParams position; private LinearLayout root; private WebView avatar;
    private boolean dragging,visible=true; private float direction=1; private long last,restUntil; private int maxX,maxY;
    private final Runnable move=new Runnable(){public void run(){
        if(!running)return;
        long now=SystemClock.elapsedRealtime();float dt=Math.min(.1f,(now-last)/1000f);last=now;
        if(visible&&!dragging&&now>restUntil){
            bounds();position.x=Math.max(0,Math.min(maxX,position.x+Math.round(direction*dp(44)*dt)));
            if(position.x<=0||position.x>=maxX){direction=-direction;restUntil=now+1500;}
            try{windows.updateViewLayout(root,position);}catch(IllegalArgumentException ignored){}
            avatar.evaluateJavascript("window.capriWalk?.("+(now>restUntil)+","+(direction<0)+")",null);
        }
        handler.postDelayed(this,visible?50:1000);
    }};
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void bounds(){android.graphics.Rect area=windows.getCurrentWindowMetrics().getBounds();maxX=Math.max(0,area.width()-position.width);maxY=Math.max(0,area.height()-position.height-dp(32));position.y=Math.max(dp(24),Math.min(maxY,position.y));}
    @Override public void onCreate(){super.onCreate();
        NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("capri-avatar","Capri na ekranie",NotificationManager.IMPORTANCE_LOW));
        Intent open=new Intent(this,MainActivity.class);PendingIntent launch=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,AvatarOverlayService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(7,new Notification.Builder(this,"capri-avatar").setSmallIcon(pl.capri.mobile.R.drawable.ic_capri).setContentTitle("Capri spaceruje po ekranie").setContentText("Dotknij, aby porozmawiać. Zatrzymaj nakładkę przyciskiem ×.").setContentIntent(launch).addAction(new Notification.Action.Builder(null,"Zatrzymaj",stop).build()).setOngoing(true).build());
        windows=getSystemService(WindowManager.class);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout bar=new LinearLayout(this);TextView grip=new TextView(this);grip.setText(" CAPRI  ⠿");grip.setTextColor(Color.rgb(255,73,122));grip.setTextSize(11);bar.addView(grip,new LinearLayout.LayoutParams(0,dp(32),1));
        Button chat=new Button(this);chat.setText("●");chat.setTextSize(12);chat.setContentDescription("Otwórz Capri i mikrofon");bar.addView(chat,new LinearLayout.LayoutParams(dp(45),dp(34)));
        Button close=new Button(this);close.setText("×");close.setContentDescription("Zamknij nakładkę");bar.addView(close,new LinearLayout.LayoutParams(dp(45),dp(34)));root.addView(bar);
        avatar=AssetWebView.create(this);root.addView(avatar,new LinearLayout.LayoutParams(-1,0,1));avatar.loadUrl(AssetWebView.HOME+"?overlay=1");
        position=new WindowManager.LayoutParams(dp(185),dp(240),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        position.gravity=Gravity.TOP|Gravity.LEFT;position.x=dp(20);position.y=windows.getCurrentWindowMetrics().getBounds().height()-dp(300);bounds();
        final float[] touch=new float[4];grip.setOnTouchListener((v,event)->{switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:dragging=true;touch[0]=event.getRawX();touch[1]=event.getRawY();touch[2]=position.x;touch[3]=position.y;return true;
            case MotionEvent.ACTION_MOVE:bounds();position.x=Math.max(0,Math.min(maxX,Math.round(touch[2]+event.getRawX()-touch[0])));position.y=Math.max(dp(24),Math.min(maxY,Math.round(touch[3]+event.getRawY()-touch[1])));windows.updateViewLayout(root,position);return true;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:dragging=false;restUntil=SystemClock.elapsedRealtime()+4000;return true;
            default:return false;}});
        chat.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class).putExtra("mic",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP)));
        close.setOnClickListener(v->stopSelf());
        registerReceiver(screen,new IntentFilter(){ {addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_SCREEN_ON);} });
        try{windows.addView(root,position);running=true;last=SystemClock.elapsedRealtime();handler.post(move);}catch(RuntimeException error){stopSelf();}
    }
    private final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){visible=!Intent.ACTION_SCREEN_OFF.equals(intent.getAction());if(root!=null)root.setVisibility(visible?View.VISIBLE:View.GONE);if(avatar!=null){if(visible)avatar.onResume();else avatar.onPause();}}};
    @Override public int onStartCommand(Intent intent,int flags,int id){if(intent!=null){if("stop".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}if("state".equals(intent.getAction())&&avatar!=null){String state=intent.getStringExtra("state");avatar.evaluateJavascript("window.capriEvent?.('status',{text:'',state:"+org.json.JSONObject.quote(state==null?"idle":state)+"})",null);}}return START_NOT_STICKY;}
    @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);if(position!=null){bounds();position.x=Math.min(position.x,maxX);windows.updateViewLayout(root,position);}}
    @Override public void onDestroy(){running=false;handler.removeCallbacksAndMessages(null);try{unregisterReceiver(screen);}catch(IllegalArgumentException ignored){}if(root!=null){try{windows.removeView(root);}catch(IllegalArgumentException ignored){}}if(avatar!=null)avatar.destroy();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
