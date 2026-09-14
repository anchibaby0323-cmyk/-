package com.capybara.gptgeminiminisbridge;
import java.util.*;
import java.util.regex.*;

/** Only a new, complete, stable block can reach automatic handoff. */
final class AutoGate {
    static final long STABLE_MS = 3000, COUNTDOWN_MS = 5000;
    static final String OPEN = "[[MINIS_TASK]]", CLOSE = "[[/MINIS_TASK]]";
    private final Set<String> seen = new HashSet<>();
    private boolean baseline;
    private String candidate = "";
    private long since;
    static List<String> blocks(String frame) {
        List<String> out = new ArrayList<>();
        if (frame == null) return out;
        Matcher m = Pattern.compile("^" + Pattern.quote(OPEN) + "[ \\t]*\\r?\\n(.*?)\\r?\\n" + Pattern.quote(CLOSE) + "[ \\t]*$", Pattern.DOTALL | Pattern.MULTILINE).matcher(frame);
        while (m.find()) {
            String task = m.group(1).trim();
            if (!task.isEmpty() && task.length() <= BridgePolicy.MAX_TEXT && !task.contains(OPEN)) out.add(task);
        }
        return out;
    }
    AutoGate(Set<String> delivered) { seen.addAll(delivered); }
    void rebaseline() { baseline = false; cancel(); }
    void cancel() { candidate = ""; since = 0; }
    void skip() { if (!candidate.isEmpty()) seen.add(BridgePolicy.digest(candidate)); cancel(); }
    String observe(String frame, boolean generating, long now) {
        List<String> blocks = blocks(frame);
        if (!baseline) {
            for (String task : blocks) seen.add(BridgePolicy.digest(task));
            baseline = true; cancel(); return "";
        }
        List<String> fresh = new ArrayList<>();
        for (String task : blocks) if (!seen.contains(BridgePolicy.digest(task)) && !fresh.contains(task)) fresh.add(task);
        if (generating || fresh.size() != 1) { cancel(); return ""; }
        String next = fresh.get(0);
        if (!candidate.equals(next)) { candidate = next; since = now; }
        return now - since >= STABLE_MS + COUNTDOWN_MS ? candidate : "";
    }
    String candidate() { return candidate; }
    int countdown(long now) {
        if (candidate.isEmpty() || now - since < STABLE_MS) return -1;
        return (int) Math.max(0, (STABLE_MS + COUNTDOWN_MS - (now - since) + 999) / 1000);
    }
}
