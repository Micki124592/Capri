package pl.capri.mobile;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.KeyEvent;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

final class MusicController {
    static final String YTM = "com.google.android.apps.youtube.music";
    private static final Handler handler = new Handler(Looper.getMainLooper());
    static volatile String pendingQuery;
    static volatile long expires;
    static volatile boolean clicked;
    static WeakReference<MainActivity> owner = new WeakReference<>(null);
    static int job;
    static void cancel() { pendingQuery = null; clicked = false; job++; }
    static void execute(MainActivity activity, ToolCommand command) throws Exception {
        cancel(); owner = new WeakReference<>(activity);
        if (command.type.equals("play_music")) { play(activity, command.query); return; }
        if (command.type.equals("open_youtube_music")) {
            Intent launch = activity.getPackageManager().getLaunchIntentForPackage(YTM);
            if (launch == null) throw new Exception("Zainstaluj YouTube Music na telefonie.");
            activity.startActivity(launch); activity.toolResult("Otworzyłam YouTube Music.", false); return;
        }
        AudioManager audio = activity.getSystemService(AudioManager.class);
        if (command.type.startsWith("volume_")) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, command.type.equals("volume_up") ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
            activity.toolResult("Głośność multimediów została zmieniona.", false); return;
        }
        MediaController controller = MusicNotificationListener.session();
        if (controller != null) {
            switch(command.type) {
                case "pause" -> controller.getTransportControls().pause();
                case "resume" -> controller.getTransportControls().play();
                case "next" -> controller.getTransportControls().skipToNext();
                case "previous" -> controller.getTransportControls().skipToPrevious();
            }
            activity.toolResult("Przekazałam polecenie do YouTube Music: " + command.label() + ".", false);
        } else if (activity.shellReady()) {
            activity.shellAction(command.type);
        } else {
            throw new Exception("Włącz dostęp do stanu odtwarzania albo połącz Shizuku w Ustawieniach Capri. Uruchom wcześniej muzykę.");
        }
    }
    private static void play(MainActivity activity, String query) throws Exception {
        if (activity.getPackageManager().getLaunchIntentForPackage(YTM) == null) throw new Exception("Zainstaluj YouTube Music na telefonie.");
        pendingQuery = query; expires = System.currentTimeMillis()+30000; clicked = false;
        int current = ++job;
        activity.status("Szukam: " + query, "acting");
        Intent play = new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).setPackage(YTM)
            .putExtra(SearchManager.QUERY, query).putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*");
        try { activity.startActivity(play); }
        catch (android.content.ActivityNotFoundException e) { openSearch(activity, query); }
        handler.postDelayed(() -> {
            if (job != current || pendingQuery == null || clicked) return;
            String title = MusicNotificationListener.playingTitle();
            if (matches(query, title)) { finish(title); return; }
            // Search is visible and can be completed by the bounded accessibility flow.
            try { openSearch(activity, query); } catch (Exception e) { activity.toolResult("Nie udało się otworzyć wyszukiwania YouTube Music.", true); cancel(); }
        }, 4500);
        for (int delay=1500; delay<=30000; delay+=1500) {
            handler.postDelayed(() -> { if (current==job) verifyPending(); }, delay);
        }
        handler.postDelayed(() -> {
            if (current != job || pendingQuery == null) return;
            String message = MusicNotificationListener.instance == null
                ? "Wysłałam wyszukiwanie „"+query+"”. Włącz dostęp do stanu odtwarzania, żebym mogła potwierdzać właściwy utwór. Jeśli wynik nie ruszył, wybierz go w YouTube Music."
                : "Nie potwierdziłam odtwarzania „"+query+"”. Wybierz właściwy wynik w YouTube Music. Przyciski mogą różnić się w tej wersji aplikacji.";
            MainActivity target = owner.get(); cancel(); if(target!=null) target.toolResult(message, true);
        }, 31000);
    }
    private static void openSearch(Activity activity, String query) {
        Uri uri = Uri.parse("https://music.youtube.com/search").buildUpon().appendQueryParameter("q", query).build();
        activity.startActivity(new Intent(Intent.ACTION_VIEW, uri).setPackage(YTM));
    }
    static boolean matches(String query, String metadata) {
        if (metadata == null) return false;
        String hay = ToolCommand.normalized(metadata);
        Set<String> tokens = new HashSet<>();
        for (String token : ToolCommand.normalized(query).split(" +")) if (token.length()>1 && !Set.of("od", "by", "the", "utwor", "piosenke").contains(token)) tokens.add(token);
        if (tokens.isEmpty()) return false;
        for (String token : tokens) if (!hay.contains(token)) return false;
        return true;
    }
    static void verifyPending() {
        String query = pendingQuery;
        if (query == null) return;
        String title = MusicNotificationListener.playingTitle();
        if(matches(query,title)) handler.post(() -> { if(pendingQuery!=null && matches(pendingQuery,title)) finish(title); });
    }
    private static void finish(String title) {
        MainActivity activity = owner.get(); cancel();
        if(activity!=null) activity.toolResult("Gra: " + title + ".", false);
    }
}
