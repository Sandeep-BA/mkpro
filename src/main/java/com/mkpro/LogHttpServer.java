package com.mkpro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;

public class LogHttpServer {

    private final int port;
    private final int wsPort;
    private HttpServer server;
    private final List<String> aggregatedLogs = Collections.synchronizedList(new ArrayList<>());
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public LogHttpServer(int port, int wsPort) {
        this.port = port;
        this.wsPort = wsPort;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/logs", this::handleLogsRequest);
        server.start();
        ActionLogger.logAction("LogHttpServer started on port " + port + " (WS port: " + wsPort + ")");
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            ActionLogger.logAction("LogHttpServer stopped");
        }
    }

    private void handleLogsRequest(HttpExchange exchange) {
        try {
            // CORS Headers
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if ("/logs/aggregate".equals(path) && "POST".equalsIgnoreCase(method)) {
                handleAggregateLogs(exchange);
            } else if ("/logs".equals(path) && "GET".equalsIgnoreCase(method)) {
                handleGetLogs(exchange);
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
            }
        } catch (Exception e) {
            handleError(exchange, e);
        }
    }

    private void handleAggregateLogs(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            String body = reader.lines().collect(Collectors.joining("\n"));
            JsonNode node = objectMapper.readTree(body);
            String instance = node.has("instance") ? node.get("instance").asText() : "Unknown";
            String log = node.has("log") ? node.get("log").asText() : "";
            String formattedLog = "[" + instance + "] " + log;
            aggregatedLogs.add(formattedLog);
            
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        } catch (Exception e) {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
        }
    }

    private void handleGetLogs(HttpExchange exchange) throws IOException {
        List<String> localLogs = ActionLogger.getAllLogs();
        List<String> combinedLogs = new ArrayList<>(localLogs);
        synchronized (aggregatedLogs) {
            combinedLogs.addAll(aggregatedLogs);
        }

        String html = html(
            head(
                title("mkpro Logs"),
                style(
                    "body { background-color: #121212; color: #e0e0e0; font-family: 'Courier New', Courier, monospace; margin: 0; padding: 20px; line-height: 1.5; }" +
                    "h1 { color: #bb86fc; margin: 0; }" +
                    ".header { display: flex; align-items: center; justify-content: space-between; border-bottom: 2px solid #bb86fc; padding-bottom: 10px; margin-bottom: 20px; }" +
                    ".container { background-color: #1e1e1e; padding: 20px; border-radius: 8px; box-shadow: 0 4px 10px rgba(0,0,0,0.5); }" +
                    ".log-entry { border-bottom: 1px solid #333; padding: 8px 0; white-space: pre-wrap; word-wrap: break-word; font-size: 14px; }" +
                    ".log-entry:last-child { border-bottom: none; }" +
                    ".instance-prefix { color: #03dac6; font-weight: bold; }" +
                    ".footer { margin-top: 20px; font-size: 12px; color: #777; }" +
                    ".switch { position: relative; display: inline-block; width: 40px; height: 22px; vertical-align: middle; }" +
                    ".switch input { opacity: 0; width: 0; height: 0; }" +
                    ".slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: #333; transition: .4s; border-radius: 34px; }" +
                    ".slider:before { position: absolute; content: ''; height: 16px; width: 16px; left: 3px; bottom: 3px; background-color: white; transition: .4s; border-radius: 50%; }" +
                    "input:checked + .slider { background-color: #bb86fc; }" +
                    "input:checked + .slider:before { transform: translateX(18px); }" +
                    ".follow-text { margin-left: 10px; font-weight: bold; color: #bb86fc; vertical-align: middle; }"
                )
            ),
            body(
                div(
                    h1("mkpro Logs"),
                    div(
                        label(
                            input().withType("checkbox").withId("follow-toggle").attr("checked", "checked"),
                            span().withClass("slider")
                        ).withClass("switch"),
                        span("Follow Logs").withClass("follow-text")
                    )
                ).withClass("header"),
                div(
                    each(combinedLogs, log -> {
                        if (log.startsWith("[")) {
                            int closingBracket = log.indexOf("]");
                            if (closingBracket != -1) {
                                String prefix = log.substring(0, closingBracket + 1);
                                String content = log.substring(closingBracket + 1);
                                // Check if it looks like a timestamp (e.g., [2026-...)
                                boolean isTimestamp = prefix.matches("\\[\\d{4}-.*");
                                if (!isTimestamp) {
                                    return div(
                                        span(prefix).withClass("instance-prefix"),
                                        text(content)
                                    ).withClass("log-entry");
                                }
                            }
                        }
                        return div(log).withClass("log-entry");
                    })
                ).withId("logs-list").withClass("container"),
                div("WebSocket Port: " + wsPort).withClass("footer"),
                script(rawHtml(
                    "const wsPort = " + wsPort + ";\n" +
                    "const logsList = document.getElementById('logs-list');\n" +
                    "const followToggle = document.getElementById('follow-toggle');\n" +
                    "\n" +
                    "const socket = new WebSocket('ws://' + window.location.hostname + ':' + wsPort);\n" +
                    "\n" +
                    "socket.onmessage = function(event) {\n" +
                    "    const data = JSON.parse(event.data);\n" +
                    "    if (data.type === 'log') {\n" +
                    "        const logEntry = document.createElement('div');\n" +
                    "        logEntry.className = 'log-entry';\n" +
                    "        \n" +
                    "        const content = data.content;\n" +
                    "        if (content.startsWith('[')) {\n" +
                    "            const closingBracket = content.indexOf(']');\n" +
                    "            if (closingBracket !== -1) {\n" +
                    "                const prefix = content.substring(0, closingBracket + 1);\n" +
                    "                const rest = content.substring(closingBracket + 1);\n" +
                    "                const isTimestamp = /^\\[\\d{4}-/.test(prefix);\n" +
                    "                if (!isTimestamp) {\n" +
                    "                    const span = document.createElement('span');\n" +
                    "                    span.className = 'instance-prefix';\n" +
                    "                    span.textContent = prefix;\n" +
                    "                    logEntry.appendChild(span);\n" +
                    "                    logEntry.appendChild(document.createTextNode(rest));\n" +
                    "                } else {\n" +
                    "                    logEntry.textContent = content;\n" +
                    "                }\n" +
                    "            } else {\n" +
                    "                logEntry.textContent = content;\n" +
                    "            }\n" +
                    "        } else {\n" +
                    "            logEntry.textContent = content;\n" +
                    "        }\n" +
                    "        \n" +
                    "        logsList.appendChild(logEntry);\n" +
                    "        \n" +
                    "        if (followToggle.checked) {\n" +
                    "            window.scrollTo(0, document.body.scrollHeight);\n" +
                    "        }\n" +
                    "    }\n" +
                    "};\n" +
                    "\n" +
                    "socket.onopen = function() { console.log('Connected to log stream'); };\n" +
                    "socket.onclose = function() { console.log('Disconnected from log stream'); };\n" +
                    "\n" +
                    "window.onload = function() {\n" +
                    "    if (followToggle.checked) {\n" +
                    "        window.scrollTo(0, document.body.scrollHeight);\n" +
                    "    }\n" +
                    "};"
                ))
            )
        ).render();

        byte[] responseBytes = html.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private void handleError(HttpExchange exchange, Exception e) {
        try {
            String errorHtml = html(
                head(title("Error")),
                body(h1("Internal Server Error"), p(e.getMessage()))
            ).render();
            byte[] errorBytes = errorHtml.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(500, errorBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorBytes);
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }
}
