package pl.capri.mobile;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

final class NetworkClient {
    interface ChatEvents { void partial(String text); void done(String answer, ToolCommand action); }
    static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    final OkHttpClient client = new OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build();
    final Set<Call> active = ConcurrentHashMap.newKeySet();
    final Set<Call> speechCalls = ConcurrentHashMap.newKeySet();
    private final SecureSettings settings;
    NetworkClient(SecureSettings settings) { this.settings = settings; }
    void cancel() { for (Call call : active) call.cancel(); }
    void cancelSpeech() { for (Call call : speechCalls) call.cancel(); }
    private Call track(Request request) { Call call = client.newCall(request); active.add(call); return call; }
    String checkOllama() throws Exception {
        if (settings.endpoint().isEmpty()) throw new IOException("Wpisz adres serwera Tailscale w Ustawieniach.");
        Call call=track(new Request.Builder().url(settings.endpoint()+"/health").header("Authorization","Bearer "+settings.gatewayKey()).build());
        try(Response response=call.execute()) {
            if(!response.isSuccessful())throw new IOException("Serwer: HTTP "+response.code()+". Sprawdź Tailscale i klucz dostępu.");
            JSONObject result=new JSONObject(response.body().string());
            return "Połączenie działa: "+result.optString("backend","AI")+" / "+result.optString("engineModel",settings.model());
        }finally{active.remove(call);}
    }
    void chat(String input, JSONArray history, ChatEvents events) throws Exception {
        if (settings.endpoint().isEmpty()) throw new IOException("Najpierw wpisz adres serwera Tailscale w Ustawieniach. Awatar działa też offline.");
        String system = "Odgrywasz Capri, żywiołową cyfrową towarzyszkę. Mów poprawnie po polsku, o sobie w rodzaju żeńskim. "
            + "Jesteś figlarna, ciekawa, zadziorna i troskliwa. Zwykle odpowiadaj w 1–3 krótkich zdaniach, naturalnie, bez didaskaliów, emoji, znaczników emocji ani etykiety Capri. "
            + "Najpierw odpowiedz na pytanie, potem dodaj charakter. Nie udawaj obserwowania ekranu, znajomości pogody ani wykonania działań. "
            + "Nie masz dostępu do aktualnej pogody i internetu. Gdy potrzebujesz aktualnych danych, przyznaj ich brak. Nie zmyślaj faktów. "
            + "Sterowanie telefonem: dostępne jest tylko narzędzie control_phone. Wywołuj je wyłącznie na wyraźną prośbę użytkownika. "
            + "Dla muzyki query to wykonawca i tytuł. Jeśli użytkownik zostawia wybór Tobie, wybierz utwór z Cyberpunka lub Edgerunners. Nigdy nie twórz poleceń powłoki. "
            + "Narzędzie jest propozycją, wykonanie potwierdzi telefon. Nie mów że utwór gra przed otrzymaniem wyniku.";
        JSONArray messages = new JSONArray().put(new JSONObject().put("role", "system").put("content", system));
        int chars = 0;
        for (int i=Math.max(0, history.length()-10); i<history.length(); i++) {
            JSONObject old = history.getJSONObject(i); String role = old.optString("role");
            String content = old.optString("content");
            if ((role.equals("user") || role.equals("assistant")) && chars + content.length() < 6500) {
                messages.put(new JSONObject().put("role", role).put("content", content)); chars += content.length();
            }
        }
        messages.put(new JSONObject().put("role", "user").put("content", input));
        JSONArray types = new JSONArray(); for (String action : ToolCommand.ALLOWED) types.put(action);
        JSONObject parameters = new JSONObject().put("type", "object").put("properties", new JSONObject()
            .put("type", new JSONObject().put("type", "string").put("enum", types))
            .put("query", new JSONObject().put("type", "string").put("description", "Wykonawca i tytuł, wymagane dla play_music")))
            .put("required", new JSONArray().put("type"));
        JSONArray tools = new JSONArray().put(new JSONObject().put("type", "function").put("function", new JSONObject()
            .put("name", "control_phone").put("description", "Zaproponuj działanie muzyczne na telefonie użytkownika").put("parameters", parameters)));
        JSONObject body=new JSONObject().put("model",settings.model()).put("messages",messages).put("tools",tools).put("stream",true);
        Call call=track(new Request.Builder().url(settings.endpoint()+"/v1/chat/completions").header("Authorization","Bearer "+settings.gatewayKey()).post(RequestBody.create(body.toString(),JSON)).build());
        try(Response response=call.execute()) {
            if(!response.isSuccessful())throw new IOException("Serwer AI: HTTP "+response.code()+". Sprawdź Tailscale, klucz i serwer na komputerze.");
            CompletionStream stream=new CompletionStream();
            BufferedReader reader=new BufferedReader(new InputStreamReader(response.body().byteStream(),java.nio.charset.StandardCharsets.UTF_8));
            String line;
            while((line=reader.readLine())!=null){stream.line(line);if(stream.text.length()>0)events.partial(stream.text.toString());if(stream.finished)break;}
            ToolCommand action=stream.action();
            if(stream.text.toString().isBlank()&&action==null)throw new IOException("Model zwrócił pustą odpowiedź.");
            events.done(stream.text.toString().trim(),action);
        }finally{active.remove(call);}
    }
    File fish(String text, File cache) throws Exception {
        String key = settings.key();
        if (key.isEmpty()) throw new IOException("Dodaj klucz Fish Audio w Ustawieniach. Nie przełączam automatycznie na inny głos.");
        JSONObject body = new JSONObject().put("text", text).put("reference_id", settings.voiceId()).put("format", "mp3")
            .put("mp3_bitrate", 128).put("latency", "normal").put("normalize", true).put("temperature", 0.75);
        Request request = new Request.Builder().url("https://api.fish.audio/v1/tts")
            .header("Authorization", "Bearer " + key).header("model", settings.fishModel())
            .header("Accept", "audio/mpeg").post(RequestBody.create(body.toString(), JSON)).build();
        Call call = track(request);
        speechCalls.add(call);
        File output = File.createTempFile("capri-", ".mp3", cache);
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) throw new IOException("Fish Audio: HTTP " + response.code() + ". Sprawdź klucz, model, Voice ID i limit konta.");
            String type = response.header("Content-Type", "");
            if (type.contains("json") || type.contains("text/")) throw new IOException("Fish nie zwróciło dźwięku.");
            try (InputStream in = response.body().byteStream(); OutputStream out = new FileOutputStream(output)) {
                byte[] buf = new byte[16384]; int n; int total=0;
                while ((n=in.read(buf)) != -1) { total+=n; if (total>12_000_000) throw new IOException("Przekroczono limit nagrania"); out.write(buf,0,n); }
            }
            if (output.length()<100) throw new IOException("Fish zwróciło puste nagranie");
            return output;
        } catch (Exception e) { output.delete(); throw e; } finally { active.remove(call); speechCalls.remove(call); }
    }
}
