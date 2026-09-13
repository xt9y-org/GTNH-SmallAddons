package com.xt9y.features.xtprofile;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
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
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        executor = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
        server.setExecutor(executor);
        server.createContext("/api/state", this::state);
        server.createContext("/api/reset", this::reset);
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
        manager.reset();
        write(exchange, 204, "text/plain; charset=utf-8", new byte[0]);
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
            "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; connect-src 'self'");
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
        try (InputStream input = XTProfileHttpServer.class
            .getResourceAsStream("/assets/xt9yfeatures/xtprofile/index.html")) {
            if (input == null) return "XTProfile dashboard resource missing".getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return output.toByteArray();
        } catch (IOException ignored) {
            return "XTProfile dashboard resource could not be loaded".getBytes(StandardCharsets.UTF_8);
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
