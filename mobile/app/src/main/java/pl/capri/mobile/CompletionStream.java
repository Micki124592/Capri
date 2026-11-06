package pl.capri.mobile;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Parses OpenAI SSE without ever treating tool JSON as spoken text. */
final class CompletionStream {
    final StringBuilder text=new StringBuilder();
    private final Map<Integer,StringBuilder> names=new TreeMap<>(),arguments=new TreeMap<>();
    boolean finished;
    void line(String line) throws Exception {
        if(!line.startsWith("data:"))return;
        String data=line.substring(5).trim();if(data.equals("[DONE]")){finished=true;return;}
        JSONObject part=new JSONObject(data);if(part.has("error"))throw new IOException("Serwer AI odrzucił żądanie. Sprawdź połączenie i dostępność modelu na komputerze.");
        JSONArray choices=part.optJSONArray("choices");if(choices==null||choices.length()==0)return;
        JSONObject choice=choices.getJSONObject(0),delta=choice.optJSONObject("delta");if(delta==null)return;
        if(!delta.isNull("content"))text.append(delta.optString("content",""));
        JSONArray calls=delta.optJSONArray("tool_calls");if(calls!=null)for(int i=0;i<calls.length();i++){
            JSONObject call=calls.getJSONObject(i),fn=call.optJSONObject("function");if(fn==null)continue;int index=call.optInt("index",i);
            names.computeIfAbsent(index,k->new StringBuilder()).append(fn.optString("name",""));
            arguments.computeIfAbsent(index,k->new StringBuilder()).append(fn.optString("arguments",""));
        }
        if(text.length()>12000)throw new IOException("Odpowiedź przekroczyła limit długości.");
    }
    ToolCommand action() throws Exception {
        if(!finished)throw new IOException("Połączenie przerwano przed końcem odpowiedzi. Nie wykonano działania.");
        if(names.size()>1)throw new IOException("Podaj jedno polecenie na raz.");
        if(names.isEmpty())return null;
        int index=names.keySet().iterator().next();if(!names.get(index).toString().equals("control_phone"))throw new IOException("Nieznane narzędzie — niczego nie wykonano.");
        return ToolCommand.fromJson(new JSONObject(arguments.get(index).toString()));
    }
}
