package dev.mobilepilot.server;

import android.content.Context;
import dev.mobilepilot.core.AuditLog;
import dev.mobilepilot.core.ToolRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class McpHttpServer {
    private volatile boolean running;
    private ServerSocket server;
    private Thread thread;
    private final ToolRegistry tools;

    public McpHttpServer(Context context) {
        tools = new ToolRegistry(context);
    }

    public synchronized void start(int port) throws IOException {
        if (running) return;
        server = new ServerSocket();
        server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
        running = true;
        thread = new Thread(this::loop, "MobilePilot-MCP");
        thread.start();
        AuditLog.add("mcp", "started 127.0.0.1:" + port);
    }

    public synchronized void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        AuditLog.add("mcp", "stopped");
    }

    private void loop() {
        while (running) {
            try {
                Socket s = server.accept();
                new Thread(() -> handle(s), "MobilePilot-MCP-client").start();
            } catch (IOException e) {
                if (running) AuditLog.add("mcp_error", e.toString());
            }
        }
    }

    private void handle(Socket s) {
        try (Socket socket = s;
             BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
             OutputStream out = socket.getOutputStream()) {
            String requestLine = readLine(in);
            if (requestLine == null) return;
            Map<String,String> headers = new HashMap<>();
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int p = line.indexOf(':');
                if (p > 0) headers.put(line.substring(0,p).trim().toLowerCase(Locale.ROOT), line.substring(p+1).trim());
            }
            int length = Integer.parseInt(headers.getOrDefault("content-length","0"));
            byte[] body = readExactly(in, length);

            if (requestLine.startsWith("OPTIONS ")) {
                write(out, 204, "", "text/plain");
                return;
            }
            if (!requestLine.startsWith("POST /mcp")) {
                write(out, 404, "{\"error\":\"Use POST /mcp\"}", "application/json");
                return;
            }

            JSONObject req = new JSONObject(new String(body, StandardCharsets.UTF_8));
            JSONObject resp = process(req);
            if (resp.length() == 0) {
                write(out, 202, "", "application/json");
            } else {
                write(out, 200, resp.toString(), "application/json");
            }
        } catch (Exception e) {
            AuditLog.add("mcp_client_error", e.toString());
        }
    }

    private JSONObject process(JSONObject req) throws Exception {
        Object id = req.has("id") ? req.get("id") : JSONObject.NULL;
        String method = req.optString("method","");
        JSONObject base = new JSONObject().put("jsonrpc","2.0").put("id",id);

        if ("initialize".equals(method)) {
            JSONObject result = new JSONObject()
                    .put("protocolVersion", "2025-03-26")
                    .put("capabilities", new JSONObject().put("tools", new JSONObject().put("listChanged", false)))
                    .put("serverInfo", new JSONObject().put("name","MobilePilot").put("version","0.1.0"));
            return base.put("result",result);
        }
        if ("ping".equals(method)) return base.put("result",new JSONObject());
        if ("tools/list".equals(method)) return base.put("result",new JSONObject().put("tools",tools.listTools()));
        if ("tools/call".equals(method)) {
            JSONObject p = req.optJSONObject("params");
            String name = p == null ? "" : p.optString("name","");
            JSONObject args = p == null ? new JSONObject() : p.optJSONObject("arguments");
            if (args == null) args = new JSONObject();
            JSONObject data = tools.call(name,args);
            JSONArray content = new JSONArray().put(new JSONObject().put("type","text").put("text",data.toString()));
            return base.put("result",new JSONObject()
                    .put("content",content)
                    .put("structuredContent",data)
                    .put("isError",!data.optBoolean("ok",false)));
        }
        if ("notifications/initialized".equals(method)) return new JSONObject();
        return base.put("error",new JSONObject().put("code",-32601).put("message","Method not found: " + method));
    }

    private static byte[] readExactly(InputStream in, int length) throws IOException {
        byte[] b = new byte[length];
        int off = 0;
        while (off < length) {
            int n = in.read(b, off, length - off);
            if (n < 0) throw new EOFException("Unexpected EOF");
            off += n;
        }
        return b;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
        }
        if (c == -1 && b.size() == 0) return null;
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void write(OutputStream out, int status, String body, String type) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String msg = status == 200 ? "OK" : status == 202 ? "Accepted" : status == 204 ? "No Content" : "Not Found";
        String h = "HTTP/1.1 " + status + " " + msg + "\r\n" +
                "Content-Type: " + type + "; charset=utf-8\r\n" +
                "Content-Length: " + b.length + "\r\n" +
                "Connection: close\r\n" +
                "Access-Control-Allow-Origin: http://127.0.0.1\r\n" +
                "Access-Control-Allow-Headers: Content-Type, MCP-Protocol-Version, MCP-Session-Id\r\n" +
                "Access-Control-Allow-Methods: POST, OPTIONS\r\n\r\n";
        out.write(h.getBytes(StandardCharsets.UTF_8));
        out.write(b);
        out.flush();
    }
}
