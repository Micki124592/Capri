package pl.capri.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class CompletionStreamTest {
    @Test public void textAndFragmentedTools() throws Exception {
        CompletionStream stream=new CompletionStream();
        stream.line("data: {\"choices\":[{\"delta\":{\"content\":\"Cześć\"}}]}");
        stream.line("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"name\":\"control_phone\",\"arguments\":\"{\\\"type\\\":\"}}]}}]}");
        stream.line("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"pause\\\"}\"}}]}}]}");
        stream.line("data: [DONE]");assertEquals("Cześć",stream.text.toString());assertEquals("pause",stream.action().type);
    }
    @Test(expected=java.io.IOException.class) public void incompleteCannotExecute() throws Exception {new CompletionStream().action();}
    @Test(expected=java.io.IOException.class) public void unknownToolRejected() throws Exception {
        CompletionStream stream=new CompletionStream();stream.line("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"function\":{\"name\":\"run_shell\",\"arguments\":\"{}\"}}]}}]}");stream.line("data: [DONE]");stream.action();
    }
}
