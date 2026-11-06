package pl.capri.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.net.URI;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureSettings {
    final SharedPreferences prefs;
    SecureSettings(Context c) { prefs = c.getSharedPreferences("capri", Context.MODE_PRIVATE); }
    String get(String key, String fallback) { return prefs.getString(key, fallback); }
    boolean flag(String key, boolean fallback) { return prefs.getBoolean(key, fallback); }
    String endpoint() { return get("ollama", "https://home.tail6f0542.ts.net:8443").replaceAll("/+$", ""); }
    String model() { return get("model", "codex-capricorn"); }
    String voiceId() { return get("voiceId", "499318f2459e4dd8a3ef701086a0e117"); }
    String fishModel() { return get("fishModel", "s2.1-pro-free"); }
    JSONObject publicConfig() throws Exception {
        return new JSONObject().put("ollama", endpoint()).put("model", model()).put("voiceId", voiceId())
            .put("fishModel", fishModel()).put("voice", flag("voice", true)).put("hasKey", !key().isEmpty())
            .put("hasGatewayKey", !gatewayKey().isEmpty()).put("history", prefs.getString("history", "[]"));
    }
    void save(JSONObject obj) throws Exception {
        String endpoint = obj.optString("ollama").trim().replaceAll("/+$", "");
        if (!endpoint.isEmpty()) {
            URI uri = URI.create(endpoint);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
                throw new IllegalArgumentException("Podaj adres serwera, np. https://home.tail6f0542.ts.net:8443");
        }
        String model = obj.optString("model", "codex-capricorn").trim();
        if (model.isEmpty() || model.length() > 150) throw new IllegalArgumentException("Podaj nazwę modelu serwera");
        String voice = obj.optString("voiceId", voiceId()).trim();
        if (!voice.matches("[a-zA-Z0-9_-]{1,100}")) throw new IllegalArgumentException("Nieprawidłowy Voice ID");
        String fishModel = obj.optString("fishModel", "s2.1-pro-free").trim();
        if (!fishModel.matches("[a-zA-Z0-9._-]{1,60}")) throw new IllegalArgumentException("Nieprawidłowa nazwa modelu Fish");
        SharedPreferences.Editor e = prefs.edit().putString("ollama", endpoint).putString("model", model)
            .putString("voiceId", voice).putString("fishModel", fishModel).putBoolean("voice", obj.optBoolean("voice", true));
        String secret = obj.optString("apiKey", "").trim();
        if (!secret.isEmpty()) e.putString("encryptedKey", encrypt(secret));
        if (obj.optBoolean("clearKey", false)) e.remove("encryptedKey");
        String gatewaySecret=obj.optString("gatewayKey", "").trim();
        if(!gatewaySecret.isEmpty())e.putString("encryptedGatewayKey",encrypt(gatewaySecret));
        e.apply();
    }
    private SecretKey secretKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (ks.containsAlias("capri-fish")) return ((KeyStore.SecretKeyEntry) ks.getEntry("capri-fish", null)).getSecretKey();
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder("capri-fish", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    private String encrypt(String value) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, secretKey());
        return Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(c.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }
    String gatewayKey() throws Exception { return decrypt(prefs.getString("encryptedGatewayKey", "")); }
    String key() throws Exception { return decrypt(prefs.getString("encryptedKey", "")); }
    private String decrypt(String saved) throws Exception {
        if (saved.isEmpty()) return "";
        String[] parts = saved.split(":", 2);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), java.nio.charset.StandardCharsets.UTF_8);
    }
}
