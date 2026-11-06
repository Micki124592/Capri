package pl.capri.mobile;

import android.os.Binder;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;

/** Executes a small fixed vocabulary. No caller-controlled shell strings. */
public class CapriShellService extends ICapriShell.Stub {
    private final int ownerUid;
    @androidx.annotation.Keep
    public CapriShellService(android.content.Context context) { ownerUid = context.getApplicationInfo().uid; }
    private void verifyCaller() {
        int caller = Binder.getCallingUid();
        if (caller != ownerUid && caller != android.os.Process.myUid() && caller != 2000 && caller != 0)
            throw new SecurityException("Nieautoryzowany klient");
    }
    @Override public String execute(String action, String argument) {
        verifyCaller();
        String code = switch (action) {
            case "pause" -> "127"; case "resume" -> "126"; case "next" -> "87"; case "previous" -> "88";
            case "volume_up" -> "24"; case "volume_down" -> "25"; default -> null;
        };
        String[] command;
        if (code != null) command = new String[]{"/system/bin/input", "keyevent", code};
        else if (action.equals("identity")) command = new String[]{"/system/bin/id"};
        else return "ERROR: nieobsługiwane działanie";
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(8, TimeUnit.SECONDS)) { process.destroyForcibly(); return "ERROR: przekroczono limit czasu"; }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024]; int size;
            while ((size=process.getInputStream().read(buffer))!=-1 && out.size()<8192) out.write(buffer,0,size);
            return "exit=" + process.exitValue() + " " + new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) { return "ERROR: " + e.getClass().getSimpleName(); }
    }
    @Override public void destroy() { verifyCaller(); System.exit(0); }
}
