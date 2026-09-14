package com.capybara.gptgeminiminisbridge;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class AutoGateTest {
    private String block(String s) { return AutoGate.OPEN+"\n"+s+"\n"+AutoGate.CLOSE; }
    private AutoGate armed() { AutoGate g=new AutoGate(Collections.emptySet()); g.observe("ordinary conversation",false,0); return g; }
    @Test public void ordinaryInlineInstructionsAndIncompleteBlocksNeverSend() {
        for (String s : new String[]{"開啟 LINE",AutoGate.OPEN+"\nhello","開頭是 "+AutoGate.OPEN+"，結尾是 "+AutoGate.CLOSE,block("")}) assertTrue(AutoGate.blocks(s).isEmpty());
        assertTrue(AutoGate.blocks(block(AutoGate.OPEN+"\ninner")).isEmpty());
    }
    @Test public void ignoresExistingTaskAtStart() {
        AutoGate g=new AutoGate(Collections.emptySet());
        assertEquals("",g.observe(block("old"),false,0));
        assertEquals("",g.observe(block("old"),false,100000));
    }
    @Test public void completeNewTaskNeedsStabilityAndCancelableCountdown() {
        AutoGate g=armed(); String b=block("new");
        assertEquals("",g.observe(b,false,100));
        assertEquals("",g.observe(b,false,3100)); assertEquals(5,g.countdown(3100));
        assertEquals("",g.observe(b,false,8099)); assertEquals("new",g.observe(b,false,8100));
    }
    @Test public void generationAndChangesRestartTimer() {
        AutoGate g=armed(); g.observe(block("first"),false,1);
        assertEquals("",g.observe(block("second"),false,8001));
        assertEquals("",g.observe(block("second"),true,16001));
        assertEquals("",g.observe(block("second"),false,17001));
        assertEquals("second",g.observe(block("second"),false,25001));
    }
    @Test public void navigationAndDisappearanceCannotReuseCountdown() {
        AutoGate g=armed(); g.observe(block("task"),false,1); g.observe("",false,4001);
        assertEquals("",g.observe(block("task"),false,9001));
        g.rebaseline(); assertEquals("",g.observe(block("task"),false,90000));
        assertEquals("",g.observe(block("task"),false,100000));
    }
    @Test public void cancelAndPersistentDedupPreventRetry() {
        AutoGate g=armed(); g.observe(block("task"),false,1); g.skip();
        assertEquals("",g.observe(block("task"),false,50000));
        g=new AutoGate(Collections.singleton(BridgePolicy.digest("task"))); g.observe("",false,0);
        assertEquals("",g.observe(block("  task  "),false,50000));
    }
    @Test public void ambiguousMultipleTasksNeverSend() {
        AutoGate g=armed(); String frame=block("a")+"\n"+block("b");
        assertEquals("",g.observe(frame,false,1)); assertEquals("",g.observe(frame,false,100000));
    }
}
