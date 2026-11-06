package pl.capri.mobile;

import org.json.JSONObject;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Typed tools only. Model output can never become a shell command. */
public final class ToolCommand {
    public static final Set<String> ALLOWED = Set.of("play_music", "pause", "resume", "next", "previous", "volume_up", "volume_down", "open_youtube_music");
    public final String type, query;
    public ToolCommand(String type, String query) {
        if (!ALLOWED.contains(type)) throw new IllegalArgumentException("Nieobsługiwane działanie: " + type);
        this.type = type;
        this.query = query == null ? "" : query.trim();
        if (this.query.length() > 300 || (type.equals("play_music") && this.query.isEmpty())) throw new IllegalArgumentException("Nieprawidłowy tytuł utworu");
    }
    public static String normalized(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT).replace('ł','l'), Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^a-z0-9 ]", " ").replaceAll(" +", " ").trim();
    }
    public static ToolCommand fromJson(JSONObject action) {
        return new ToolCommand(action.optString("type", ""), action.optString("query", ""));
    }
    public static ToolCommand direct(String input) {
        String n = normalized(input.replaceFirst("(?i)^\\s*(hej[, ]+)?capri[,!:. ]*", ""));
        if (n.matches("(pauza|wstrzymaj|zatrzymaj)( muzyke| odtwarzanie)?")) return new ToolCommand("pause", "");
        if (n.matches("(wznow|kontynuuj)( muzyke| odtwarzanie)?")) return new ToolCommand("resume", "");
        if (n.matches("(nastepny|kolejny)( utwor| kawalek| numer)?")) return new ToolCommand("next", "");
        if (n.matches("poprzedni( utwor| kawalek| numer)?")) return new ToolCommand("previous", "");
        if (n.matches("(glosniej|podglosnij)( muzyke)?")) return new ToolCommand("volume_up", "");
        if (n.matches("(ciszej|scisz)( muzyke)?")) return new ToolCommand("volume_down", "");
        if (n.matches("(otworz|uruchom|wlacz) (youtube music|yt music)")) return new ToolCommand("open_youtube_music", "");
        if (n.matches(".*\\b(jakis|jakas|dowolny|dowolna|cokolwiek|wybierz)\\b.*")) return null;
        // Preserve the original spelling of song names.
        String original = input.replaceFirst("(?i)^\\s*(hej[, ]+)?capri[,!:. ]*", "").trim();
        String[] words = original.split("\\s+", 2);
        if (words.length == 2 && (normalized(words[0]).matches("pusc|odtworz|zagraj") ||
            (normalized(words[0]).equals("wlacz") && n.matches(".*(youtube music|yt music).*")))) {
            String q = words[1].replaceAll("(?i)\\s+(w|na)\\s+(youtube\\s*music|yt\\s*music)\\s*[.!]?\\s*$", "").trim();
            if (!q.isEmpty() && !normalized(q).equals("muzyke")) return new ToolCommand("play_music", q);
        }
        return null;
    }
    public String label() {
        return switch (type) {
            case "play_music" -> "Odtwórz w YouTube Music: " + query;
            case "open_youtube_music" -> "Otwórz YouTube Music";
            case "pause" -> "Wstrzymaj muzykę";
            case "resume" -> "Wznów muzykę";
            case "next" -> "Następny utwór";
            case "previous" -> "Poprzedni utwór";
            case "volume_up" -> "Zwiększ głośność";
            default -> "Zmniejsz głośność";
        };
    }
}
