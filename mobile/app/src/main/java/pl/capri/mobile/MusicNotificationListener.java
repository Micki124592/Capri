package pl.capri.mobile;

import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.List;

/** Only YouTube Music playback metadata is inspected. Notification text is not collected. */
public class MusicNotificationListener extends NotificationListenerService {
    static volatile MusicNotificationListener instance;
    @Override public void onListenerConnected() { instance = this; }
    @Override public void onListenerDisconnected() { instance = null; }
    @Override public void onDestroy() { instance = null; super.onDestroy(); }
    @Override public void onNotificationPosted(StatusBarNotification notification) {
        if (MusicController.YTM.equals(notification.getPackageName())) MusicController.verifyPending();
    }
    static MediaController session() {
        MusicNotificationListener service = instance;
        if (service == null) return null;
        try {
            MediaSessionManager manager = service.getSystemService(MediaSessionManager.class);
            List<MediaController> sessions = manager.getActiveSessions(new ComponentName(service, MusicNotificationListener.class));
            for (MediaController controller : sessions) if (MusicController.YTM.equals(controller.getPackageName())) return controller;
        } catch (SecurityException ignored) { }
        return null;
    }
    static String playingTitle() {
        MediaController controller = session();
        if (controller == null || controller.getPlaybackState() == null || controller.getPlaybackState().getState() != PlaybackState.STATE_PLAYING) return null;
        MediaMetadata metadata = controller.getMetadata();
        if (metadata == null) return null;
        String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        return title == null ? null : title + (artist == null ? "" : " — " + artist);
    }
}
