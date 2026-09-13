package com.xt9y.features.xtprofile;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

final class XTProfileHttpServer {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final byte[] DASHBOARD = loadDashboard();

    private final XTProfileManager manager;
    private HttpServer server;
    private ExecutorService executor;
    private String url;

    XTProfileHttpServer(XTProfileManager manager) {
        this.manager = manager;
    }

    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
        server.setExecutor(executor);
        server.createContext("/api/state", this::state);
        server.createContext("/api/reset", this::reset);
        server.createContext("/api/icon", this::icon);
        server.createContext("/", new DashboardHandler());
        server.start();

        InetSocketAddress address = server.getAddress();
        url = "http://127.0.0.1:" + address.getPort() + "/";
    }

    String getUrl() {
        return url;
    }

    void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    static boolean openBrowser(String url) {
        if (url == null || url.isEmpty() || !Desktop.isDesktopSupported()) return false;
        try {
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) return false;
            Thread thread = new Thread(() -> {
                try {
                    desktop.browse(URI.create(url));
                } catch (Throwable ignored) {}
            }, "XTProfile-Browser");
            thread.setDaemon(true);
            thread.start();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void state(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "GET");
            return;
        }
        byte[] body = GSON.toJson(manager.snapshot()).getBytes(StandardCharsets.UTF_8);
        write(exchange, 200, "application/json; charset=utf-8", body);
    }

    private void reset(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "POST");
            return;
        }
        if (!"1".equals(exchange.getRequestHeaders().getFirst("X-XTProfile"))) {
            write(exchange, 403, "text/plain; charset=utf-8", "Forbidden".getBytes(StandardCharsets.UTF_8));
            return;
        }
        manager.reset();
        write(exchange, 204, "text/plain; charset=utf-8", new byte[0]);
    }

    private void icon(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "GET");
            return;
        }
        String name = queryValue(exchange.getRequestURI().getRawQuery(), "name");
        byte[] icon = loadIcon(name);
        if (icon == null) {
            write(exchange, 404, "text/plain; charset=utf-8", "Not Found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        write(exchange, 200, "image/png", icon);
    }

    private static String queryValue(String query, String key) {
        if (query == null || query.isEmpty()) return null;
        for (String part : query.split("&")) {
            int equals = part.indexOf('=');
            String rawKey = equals < 0 ? part : part.substring(0, equals);
            if (!key.equals(decode(rawKey))) continue;
            return decode(equals < 0 ? "" : part.substring(equals + 1));
        }
        return null;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }

    private static byte[] loadIcon(String name) {
        if (name == null || name.length() > 240 || name.contains("..") || name.indexOf('\\') >= 0) return null;
        int colon = name.indexOf(':');
        if (colon <= 0 || colon == name.length() - 1 || name.indexOf(':', colon + 1) >= 0) return null;
        String domain = name.substring(0, colon);
        String path = name.substring(colon + 1);
        if (!safeResourcePart(domain) || !safeResourcePart(path)) return null;

        byte[] icon = loadResource("/assets/" + domain + "/textures/items/" + path + ".png");
        if (icon != null) return icon;
        return loadResource("/assets/" + domain + "/textures/blocks/" + path + ".png");
    }

    private static boolean safeResourcePart(String value) {
        if (value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '/' || c == '.') continue;
            return false;
        }
        return true;
    }

    private static void methodNotAllowed(HttpExchange exchange, String allow) throws IOException {
        exchange.getResponseHeaders().set("Allow", allow);
        write(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
    }

    private static void write(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set(
            "Content-Security-Policy",
            "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; "
                + "connect-src 'self'; img-src 'self' data:");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : body.length);
        if (status != 204) {
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        } else {
            exchange.close();
        }
    }

    private static byte[] loadDashboard() {
        byte[] bytes = loadResource("/assets/xt9yfeatures/xtprofile/index.html");
        return bytes == null ? "XTProfile dashboard resource missing".getBytes(StandardCharsets.UTF_8) : bytes;
    }

    private static byte[] loadResource(String path) {
        try (InputStream input = XTProfileHttpServer.class.getResourceAsStream(path)) {
            if (input == null) return null;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return output.toByteArray();
        } catch (IOException ignored) {
            return null;
        }
    }

    private static final class DashboardHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod()) || !"/".equals(exchange.getRequestURI().getPath())) {
                write(exchange, 404, "text/plain; charset=utf-8", "Not Found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            write(exchange, 200, "text/html; charset=utf-8", DASHBOARD);
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "XTProfile-HTTP");
            thread.setDaemon(true);
            return thread;
        }
    }
}
