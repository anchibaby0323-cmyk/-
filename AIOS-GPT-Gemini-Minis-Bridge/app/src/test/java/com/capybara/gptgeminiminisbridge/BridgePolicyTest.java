package com.capybara.gptgeminiminisbridge;
import org.junit.Test;
import static org.junit.Assert.*;
public class BridgePolicyTest {
    @Test public void sourceBoundaryRejectsOtherAppsAndLookalikes() {
        assertTrue(BridgePolicy.allowed("com.openai.chatgpt"));
        assertTrue(BridgePolicy.allowed("com.google.android.apps.bard"));
        for (String pkg : new String[]{null, "", "com.openminis.app", "com.capybara.gptgeminiminisbridge", "com.google.android.googlequicksearchbox", "com.openai.chatgpt.fake"}) assertFalse(BridgePolicy.allowed(pkg));
    }
    @Test public void whitespaceCannotBypassDuplicateGuard() {
        assertEquals(BridgePolicy.digest("  開啟 LINE\n"), BridgePolicy.digest("開啟   LINE"));
        assertNotEquals(BridgePolicy.digest("開啟 LINE"), BridgePolicy.digest("開啟 Gmail"));
        assertEquals(64, BridgePolicy.digest("私人任務").length());
    }
}
