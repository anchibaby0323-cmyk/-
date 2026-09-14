package com.capybara.gptgeminiminisbridge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class BridgePolicy {
    static final int MAX_TEXT = 12000;
    static boolean allowed(String pkg) {
        return "com.openai.chatgpt".equals(pkg) || "com.google.android.apps.bard".equals(pkg);
    }
    static String normalize(String text) { return text == null ? "" : text.trim().replaceAll("\\s+", " "); }
    static String digest(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(normalize(text).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format("%02x", b & 255));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
