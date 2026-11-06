package pl.capri.mobile;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.speech.*;
import android.view.*;
import android.webkit.*;
import org.json.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private WebView web;
    private SecureSettings settings;
    private NetworkClient network;
    private JSONArray history = new JSONArray();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(), voiceWorker=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final AtomicInteger turn=new AtomicInteger(), voiceTurn=new AtomicInteger();
    private boolean ready, busy;
    private SpeechRecognizer speech;
    private MediaPlayer player;
    private File playingFile;
    private AudioFocusRequest focus;
    private ICapriShell shell;
    private Shizuku.UserServiceArgs shellArgs;
    private final Shizuku.OnBinderReceivedListener binderReceived=()->ui.post(this::sendConfig);
    private final Shizuku.OnBinderDeadListener binderDead=()->ui.post(()->{ shell=null; sendConfig(); });
    private final Shizuku.OnRequestPermissionResultListener permissionResult=(code,result)->ui.post(()->{
        if(result==PackageManager.PERMISSION_GRANTED) bindShell(); else status("Nie przyznano dostępu Shizuku. Pozostałe funkcje nadal działają.","idle");
    });
    private final ServiceConnection shellConnection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name, IBinder binder){ shell=ICapriShell.Stub.asInterface(binder); sendConfig(); status("Shizuku połączone. Narzędzia multimediów gotowe.","success"); }
        public void onServiceDisconnected(ComponentName name){ shell=null; sendConfig(); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(11,13,23)); getWindow().setNavigationBarColor(Color.rgb(11,13,23));
        settings=new SecureSettings(this); network=new NetworkClient(settings);
        try { history=new JSONArray(settings.get("history","[]")); } catch(Exception ignored){}
        web=AssetWebView.create(this); web.addJavascriptInterface(new Bridge(),"Android");
        android.widget.FrameLayout container=new android.widget.FrameLayout(this);
        container.addView(web,new android.widget.FrameLayout.LayoutParams(-1,-1));setContentView(container);
        container.setOnApplyWindowInsetsListener((view,insets)->{
            android.graphics.Insets edges=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());
            view.setPadding(edges.left,edges.top,edges.right,edges.bottom);return insets;
        });
        web.loadUrl(AssetWebView.HOME);
        Shizuku.addBinderReceivedListenerSticky(binderReceived); Shizuku.addBinderDeadListener(binderDead); Shizuku.addRequestPermissionResultListener(permissionResult);
        shellArgs=new Shizuku.UserServiceArgs(new ComponentName(this,CapriShellService.class)).daemon(false).processNameSuffix("capri_tools").version(1);
    }
    @Override protected void onResume(){ super.onResume(); if(ready) sendConfig(); }
    @Override public void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("mic",false))ui.postDelayed(this::listen,450);}
    final class Bridge {
        @JavascriptInterface public void action(String name,String payload){ui.post(()->dispatch(name,payload));}
    }
    private void dispatch(String name,String payload){
        try {
            switch(name){
                case "ready" -> {ready=true; sendConfig(); emit("history",new JSONObject().put("items",history)); if(getIntent().getBooleanExtra("mic",false))ui.postDelayed(this::listen,500);}
                case "send" -> send(payload);
                case "mic" -> listen();
                case "stop" -> cancel();
                case "save" -> {settings.save(new JSONObject(payload));sendConfig();status("Ustawienia zapisane na telefonie.","success");}
                case "test" -> worker.execute(()->{try{String result=network.checkOllama();ui.post(()->status(result,"success"));}catch(Exception e){failure(e);}});
                case "voice_test" -> speak("Hej, choom! Capri na łączach. No, to co dziś kombinujemy?",true);
                case "shizuku" -> connectShell();
                case "accessibility" -> new AlertDialog.Builder(this).setTitle("Sterowanie YouTube Music")
                    .setMessage("Capri będzie odczytywać elementy interfejsu i klikać pasujący wynik wyłącznie w YouTube Music, po Twoim poleceniu. Treść ekranu nie jest wysyłana do modelu. W ustawieniach włącz usługę Capri — YouTube Music.")
                    .setPositiveButton("Otwórz ustawienia",(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).setNegativeButton("Wróć",null).show();
                case "notifications" -> new AlertDialog.Builder(this).setTitle("Potwierdzanie odtwarzania")
                    .setMessage("Dostęp do powiadomień pozwala Capri sprawdzać tytuł, wykonawcę i stan odtwarzania YouTube Music. Kod nie odczytuje tekstów innych powiadomień. Włącz Capri — stan odtwarzania.")
                    .setPositiveButton("Otwórz ustawienia",(d,w)->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))).setNegativeButton("Wróć",null).show();
                case "overlay" -> toggleOverlay();
                case "clear" -> new AlertDialog.Builder(this).setTitle("Wyczyścić lokalną rozmowę?").setMessage("Usunie to historię zapisaną w tej aplikacji.")
                    .setPositiveButton("Wyczyść",(d,w)->{cancel();history=new JSONArray();persist();emit("history",obj("items",history));}).setNegativeButton("Wróć",null).show();
                case "licenses" -> new AlertDialog.Builder(this).setTitle("Autorstwo i licencje").setMessage("Renderer WebGL i sekwencje animacji: © 2026 mochiiwuff, capri-desktop, PolyForm Noncommercial 1.0.0. Zmiany: mobilny sterownik animacji i interfejs Android. Grafiki postaci mają odrębnych właścicieli praw; nie są objęte licencją kodu. Projekt fanowski do osobistego użytku, bez afiliacji z CD PROJEKT RED. Pełna licencja jest dołączona do APK i kodu źródłowego.\n\nShizuku API: RikkaApps, Apache-2.0. OkHttp: Square, Apache-2.0.").setPositiveButton("Zamknij",null).show();
            }
        }catch(Exception e){failure(e);}
    }
    private void sendConfig(){
        if(!ready || isFinishing())return;
        try{
            JSONObject cfg=settings.publicConfig(); cfg.remove("history");
            cfg.put("shizukuAvailable",Shizuku.pingBinder()).put("shizuku",shellReady())
                .put("accessibility",MusicAccessibilityService.connected).put("notifications",MusicNotificationListener.instance!=null).put("overlay",AvatarOverlayService.running);
            emit("config",cfg);
        }catch(Exception e){failure(e);}
    }
    private void send(String input){
        input=input.trim(); if(input.isEmpty())return;
        if(input.length()>6000){status("Wiadomość jest za długa. Podziel ją na krótsze fragmenty.","error");return;}
        if(busy){status("Kończę poprzednią odpowiedź. Możesz ją zatrzymać przyciskiem Stop.","thinking");return;}
        stopAudio(); MusicController.cancel();
        final String question=input; final int id=turn.incrementAndGet();
        JSONArray previous=new JSONArray();try{previous=new JSONArray(history.toString());}catch(Exception ignored){}
        addMessage("user",question); ToolCommand direct=ToolCommand.direct(question);
        if(direct!=null){perform(direct);return;}
        busy=true; emit("busy",obj("value",true));status("Myślę…","thinking");
        final JSONArray context=previous;
        worker.execute(()->{
            try{network.chat(question,context,new NetworkClient.ChatEvents(){
                long last;
                public void partial(String answer){if(System.currentTimeMillis()-last<60)return;last=System.currentTimeMillis();ui.post(()->{if(turn.get()==id)emit("partial",obj("text",answer));});}
                public void done(String answer,ToolCommand action){ui.post(()->{
                    if(turn.get()!=id)return;busy=false;emit("busy",obj("value",false));emit("partial",obj("text",""));
                    if(!answer.isBlank())addMessage("assistant",answer);
                    if(action!=null){status("Propozycja działania","alert");new AlertDialog.Builder(MainActivity.this).setTitle("Wykonać działanie?").setMessage(action.label())
                        .setPositiveButton("Wykonaj",(d,w)->perform(action)).setNegativeButton("Anuluj",(d,w)->status("Działanie anulowane.","idle")).show();}
                    else{status("Na łączach","idle");if(settings.flag("voice",true))speak(answer,false);}
                });}
            });}catch(Exception e){ui.post(()->{if(turn.get()==id){busy=false;emit("busy",obj("value",false));emit("partial",obj("text",""));failure(e);}});}
        });
    }
    private void perform(ToolCommand command){
        stopAudio();
        try{MusicController.execute(this,command);}catch(Exception e){failure(e);}
    }
    void toolResult(String text,boolean warning){ui.post(()->{addMessage("tool",text);status(warning?"Sprawdź wynik w aplikacji":"Działanie obsłużone",warning?"alert":"success");if(!warning && settings.flag("voice",true))speak(text,false);});}
    void status(String text,String state){
        ui.post(()->{emit("status",obj("text",text,"state",state));
            if(AvatarOverlayService.running){Intent intent=new Intent(this,AvatarOverlayService.class).setAction("state").putExtra("state",state);startService(intent);}
        });
    }
    private void addMessage(String role,String text){try{history.put(new JSONObject().put("role",role.equals("tool")?"assistant":role).put("content",text));persist();emit("message",new JSONObject().put("role",role).put("text",text));}catch(Exception ignored){}}
    private void persist(){while(history.length()>60)history.remove(0);settings.prefs.edit().putString("history",history.toString()).apply();}
    private void emit(String type,JSONObject payload){if(!ready || web==null || isFinishing())return;String script="window.capriEvent("+JSONObject.quote(type)+","+payload+")";web.evaluateJavascript(script,null);}
    static JSONObject obj(String key,Object value){try{return new JSONObject().put(key,value);}catch(Exception e){throw new IllegalStateException(e);}}
    static JSONObject obj(String key,Object value,String second,Object secondValue){try{return obj(key,value).put(second,secondValue);}catch(Exception e){throw new IllegalStateException(e);}}
    private void failure(Exception e){ui.post(()->{String msg=e.getMessage();if(msg==null || msg.isBlank())msg=e.getClass().getSimpleName();if(e instanceof java.net.ConnectException || e instanceof java.net.SocketTimeoutException)msg="Nie mogę połączyć się z serwerem. Sprawdź Tailscale, adres serwera oraz czy komputer i serwer są włączone.";status(msg,"error");});}
    private void cancel(){turn.incrementAndGet();voiceTurn.incrementAndGet();network.cancel();busy=false;MusicController.cancel();if(speech!=null)speech.cancel();stopAudio();emit("busy",obj("value",false));emit("partial",obj("text",""));status("Zatrzymano","idle");}
    private void listen(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},71);return;}
        if(!SpeechRecognizer.isRecognitionAvailable(this)){status("Brak usługi rozpoznawania mowy. Włącz usługę głosową Google lub Samsung.","error");return;}
        cancel();if(speech!=null)speech.destroy();speech=SpeechRecognizer.createSpeechRecognizer(this);
        speech.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle p){status("Słucham…","listening");}
            public void onBeginningOfSpeech(){} public void onRmsChanged(float value){emit("level",obj("value",Math.max(0,Math.min(1,value/12))));}
            public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){status("Rozpoznaję mowę…","thinking");}
            public void onError(int error){status(error==SpeechRecognizer.ERROR_NO_MATCH || error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT?"Nie usłyszałam polecenia. Spróbuj ponownie.":"Rozpoznawanie mowy: błąd "+error,"idle");}
            public void onResults(Bundle result){ArrayList<String> lines=result.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(lines!=null && !lines.isEmpty())send(lines.get(0));else status("Nie rozpoznałam wypowiedzi.","idle");}
            public void onPartialResults(Bundle results){} public void onEvent(int event,Bundle p){}
        });
        Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE,"pl-PL").putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
        speech.startListening(intent);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==71 && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)listen();}
    private void speak(String text,boolean test){
        stopAudio();if(text==null || text.isBlank())return;
        int id=voiceTurn.incrementAndGet(); status("Przygotowuję głos Fish…","thinking");
        voiceWorker.execute(()->{try{File audio=network.fish(text,getCacheDir());ui.post(()->{if(voiceTurn.get()!=id){audio.delete();return;}play(audio);});}
            catch(Exception e){if(voiceTurn.get()==id)failure(e);}});
    }
    private void play(File audio){
        try{
            playingFile=audio;player=new MediaPlayer();
            AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            player.setAudioAttributes(attributes);player.setDataSource(audio.getAbsolutePath());
            AudioManager audioManager=getSystemService(AudioManager.class);
            focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes).setOnAudioFocusChangeListener(change->{if(change==AudioManager.AUDIOFOCUS_LOSS || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)stopAudio();}).build();
            if(audioManager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){stopAudio();status("Inna aplikacja korzysta teraz z dźwięku.","idle");return;}
            player.setOnPreparedListener(p->{p.start();status("Mówię…","speaking");});
            player.setOnCompletionListener(p->{stopAudio();status("Na łączach","idle");});
            player.setOnErrorListener((p,w,e)->{stopAudio();status("Nie udało się odtworzyć dźwięku Fish.","error");return true;});player.prepareAsync();
        }catch(Exception e){stopAudio();failure(e);}
    }
    private void stopAudio(){voiceTurn.incrementAndGet();if(network!=null)network.cancelSpeech();if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}if(playingFile!=null){playingFile.delete();playingFile=null;}if(focus!=null){getSystemService(AudioManager.class).abandonAudioFocusRequest(focus);focus=null;}}
    boolean shellReady(){return shell!=null && shell.asBinder().isBinderAlive();}
    private void connectShell(){
        if(!Shizuku.pingBinder()){status("Uruchom Shizuku na telefonie, a potem wróć i wybierz Połącz.","alert");return;}
        if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){Shizuku.requestPermission(91);return;}bindShell();
    }
    private void bindShell(){try{Shizuku.bindUserService(shellArgs,shellConnection);}catch(Exception e){failure(e);}}
    void shellAction(String action){worker.execute(()->{try{String result=shell.execute(action,"");if(!result.startsWith("exit=0"))throw new Exception("Shizuku nie wykonało działania: "+result);toolResult("Polecenie multimediów wysłane przez Shizuku.",false);}catch(Exception e){failure(e);}});}
    private void toggleOverlay(){
        if(AvatarOverlayService.running){stopService(new Intent(this,AvatarOverlayService.class));sendConfig();return;}
        if(!Settings.canDrawOverlays(this)){new AlertDialog.Builder(this).setTitle("Capri nad innymi aplikacjami").setMessage("Pozwól wyświetlać awatara nad aplikacjami. Po powrocie naciśnij przycisk ponownie.")
            .setPositiveButton("Otwórz ustawienia",(d,w)->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())))).setNegativeButton("Wróć",null).show();return;}
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},72);
        startForegroundService(new Intent(this,AvatarOverlayService.class));ui.postDelayed(this::sendConfig,400);
    }
    @Override protected void onDestroy(){ready=false;turn.incrementAndGet();network.cancel();stopAudio();if(speech!=null)speech.destroy();
        Shizuku.removeBinderReceivedListener(binderReceived);Shizuku.removeBinderDeadListener(binderDead);Shizuku.removeRequestPermissionResultListener(permissionResult);
        try{Shizuku.unbindUserService(shellArgs,shellConnection,false);}catch(Exception ignored){}
        worker.shutdownNow();voiceWorker.shutdownNow();if(web!=null){web.removeJavascriptInterface("Android");web.destroy();}super.onDestroy();}
}
