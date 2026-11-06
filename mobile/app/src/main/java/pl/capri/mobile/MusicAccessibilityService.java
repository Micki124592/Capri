package pl.capri.mobile;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** Operates only during a requested music search, only in YouTube Music. */
public class MusicAccessibilityService extends AccessibilityService {
    static volatile boolean connected;
    private long lastAttempt;
    @Override protected void onServiceConnected() { connected = true; }
    @Override public void onDestroy() { connected=false; super.onDestroy(); }
    @Override public void onInterrupt() { }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (MusicController.pendingQuery == null || MusicController.clicked || System.currentTimeMillis()>MusicController.expires) return;
        if (event.getPackageName()==null || !MusicController.YTM.contentEquals(event.getPackageName())) return;
        if (System.currentTimeMillis()-lastAttempt<600) return;
        lastAttempt = System.currentTimeMillis();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName()==null || !MusicController.YTM.contentEquals(root.getPackageName())) return;
        visit(root, MusicController.pendingQuery, 0);
    }
    private boolean visit(AccessibilityNodeInfo node, String query, int depth) {
        if (node == null || depth>24 || !node.isVisibleToUser()) return false;
        String desc = node.getContentDescription()==null ? "" : node.getContentDescription().toString();
        String norm = ToolCommand.normalized(desc);
        // Only a labelled play button or a matching song title; no blind coordinate taps.
        if ((norm.startsWith("play ") || norm.startsWith("odtworz ")) && MusicController.matches(query, desc) && node.isClickable()) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { MusicController.clicked=true; return true; }
        }
        String id = node.getViewIdResourceName();
        if(id!=null && (id.endsWith("/title") || id.endsWith("/text1"))) {
            AccessibilityNodeInfo row=node.getParent();
            if (row!=null) {
                String text = collect(row, 0);
                if(MusicController.matches(query, text)) {
                    AccessibilityNodeInfo clickable=row;
                    for(int i=0;i<3 && clickable!=null;i++,clickable=clickable.getParent()) {
                        if(clickable.isClickable() && clickable.isEnabled()) {
                            if(clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { MusicController.clicked=true; return true; }
                            break;
                        }
                    }
                }
            }
        }
        for(int i=0; i<node.getChildCount(); i++) if(visit(node.getChild(i),query,depth+1)) return true;
        return false;
    }
    private String collect(AccessibilityNodeInfo node, int depth) {
        if(node==null || depth>3) return "";
        StringBuilder text=new StringBuilder();
        if(node.getText()!=null) text.append(node.getText()).append(' ');
        if(node.getContentDescription()!=null) text.append(node.getContentDescription()).append(' ');
        for(int i=0;i<node.getChildCount();i++) text.append(collect(node.getChild(i),depth+1));
        return text.toString();
    }
}
