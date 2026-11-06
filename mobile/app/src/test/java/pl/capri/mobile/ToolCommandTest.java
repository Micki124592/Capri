package pl.capri.mobile;

import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class ToolCommandTest {
    @Test public void polishVoiceMusicKeepsOriginalTitle() {
        ToolCommand c=ToolCommand.direct("Capri, odtwórz SAMURAI Never Fade Away w YouTube Music");
        assertEquals("play_music",c.type);assertEquals("SAMURAI Never Fade Away",c.query);
    }
    @Test public void youtubeAppIsNotMisreadAsSong() {
        assertEquals("open_youtube_music",ToolCommand.direct("Włącz YouTube Music").type);
    }
    @Test public void unrelatedCommandsMustGoThroughModelAndConfirmation() {
        assertNull(ToolCommand.direct("Włącz kalkulator"));
        assertNull(ToolCommand.direct("Czy możesz coś odtworzyć?"));
        assertNull(ToolCommand.direct("Usuń wszystkie pliki"));
        assertNull(ToolCommand.direct("Odtwórz jakiś hit Night City"));
    }
    @Test public void shellOutputFromModelCannotExecute() {
        assertThrows(IllegalArgumentException.class,()->ToolCommand.fromJson(new JSONObject().put("type","run_shell").put("query","rm -rf /")));
    }
    @Test public void emptyAndOversizedQueriesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new ToolCommand("play_music","  "));
        assertThrows(IllegalArgumentException.class,()->new ToolCommand("play_music","x".repeat(301)));
    }
    @Test public void accentsAndPunctuationAreNormalized() {
        assertEquals("wlacz glosniej lodz",ToolCommand.normalized("Włącz! Głośniej, Łódź."));
        assertEquals("pause",ToolCommand.direct("Wstrzymaj muzykę").type);
        assertEquals("next",ToolCommand.direct("Następny utwór").type);
    }
}
