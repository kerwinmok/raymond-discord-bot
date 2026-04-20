package com.example.discordbot.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.example.discordbot.BotRuntimeManager;
import com.example.discordbot.audio.PlayerManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

public class WebControlServer {
    private static final String HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 8080;

    private final BotRuntimeManager runtimeManager;
    private final HttpServer server;
    private final Long defaultChannelId;
    private final String panelKey;

    // Creates a local-only HTTP server for controlling bot sends from a browser.
    public WebControlServer(BotRuntimeManager runtimeManager) {
        try {
            this.runtimeManager = runtimeManager;

            int port = parsePort(System.getenv("BOT_WEB_PORT"));
            this.defaultChannelId = parseChannelId(System.getenv("BOT_DEFAULT_CHANNEL_ID"));
            this.panelKey = System.getenv("BOT_WEB_KEY");

            this.server = HttpServer.create(new InetSocketAddress(HOST, port), 0);
            this.server.createContext("/", new IndexHandler());
            this.server.createContext("/send", new SendHandler());
            this.server.createContext("/bot/start", new StartBotHandler());
            this.server.createContext("/bot/stop", new StopBotHandler());
            this.server.setExecutor(null);

            System.out.println("Web panel ready at http://" + HOST + ":" + port);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start local web panel.", e);
        }
    }

    public void start() {
        this.server.start();
    }

    private int parsePort(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PORT;
        }

        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed > 0) {
                return parsed;
            }
            return DEFAULT_PORT;
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    private Long parseChannelId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private class IndexHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method Not Allowed");
                return;
            }

            Map<String, String> query = parseFormUrlEncoded(exchange.getRequestURI().getRawQuery());
            String status = trimOrEmpty(query.get("status"));
            String statusMessage = trimOrEmpty(query.get("message"));
            String channelFromQuery = trimOrEmpty(query.get("channelId"));
            String volumeFromQuery = trimOrEmpty(query.get("volume"));

            String statusHtml = buildStatusHtml(status, statusMessage);
            String channelValue = resolveChannelValue(channelFromQuery);
            String volumeValue = resolveVolumeValue(channelValue, volumeFromQuery);
            String keyInput = buildKeyInputHtml();
            String botStatusHtml = buildBotStatusHtml();
            String html = renderPageHtml(statusHtml, botStatusHtml, channelValue, volumeValue, keyInput);

            sendHtml(exchange, 200, html);
        }

        private String resolveChannelValue(String channelFromQuery) {
            if (!channelFromQuery.isBlank()) {
                return channelFromQuery;
            }

            if (defaultChannelId == null) {
                return "";
            }

            return String.valueOf(defaultChannelId);
        }

        private String buildKeyInputHtml() {
            if (panelKey == null || panelKey.isBlank()) {
                return "";
            }

            return "<label>Panel key</label><input name=\"key\" type=\"password\" placeholder=\"BOT_WEB_KEY\" />";
        }

        private String buildStatusHtml(String status, String statusMessage) {
            if (status.isBlank()) {
                return "";
            }

            if ("success".equalsIgnoreCase(status)) {
                String message = "Successfully sent ✅";
                if (!statusMessage.isBlank()) {
                    message = escapeHtml(statusMessage);
                }
                return "<div class=\"status success\">" + message + "</div>";
            }

            if ("error".equalsIgnoreCase(status)) {
                String message = "Send failed.";
                if (!statusMessage.isBlank()) {
                    message = escapeHtml(statusMessage);
                }
                return "<div class=\"status error\">" + message + "</div>";
            }

            return "";
        }

        private String resolveVolumeValue(String channelValue, String volumeFromQuery) {
            if (!volumeFromQuery.isBlank()) {
                return volumeFromQuery;
            }

            JDA jda = runtimeManager.getJda();
            if (jda == null || !runtimeManager.isOnline()) {
                return String.valueOf(PlayerManager.DEFAULT_VOLUME);
            }

            Long channelId = parseChannelId(channelValue);
            if (channelId == null) {
                return String.valueOf(PlayerManager.DEFAULT_VOLUME);
            }

            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                return String.valueOf(PlayerManager.DEFAULT_VOLUME);
            }

            int currentVolume = PlayerManager.getInstance().getVolume(channel.getGuild());
            return String.valueOf(currentVolume);
        }

        private String buildBotStatusHtml() {
            boolean online = runtimeManager.isOnline();
            String statusLabel = escapeHtml(runtimeManager.getStatusLabel());
            String statusClass = online ? "success" : "error";
            String statusText = online ? "Online" : "Offline";
            return "<div class=\"status " + statusClass + "\">Bot: " + statusText + " (" + statusLabel + ")</div>";
        }

        private String renderPageHtml(String statusHtml, String botStatusHtml, String channelValue, String volumeValue, String keyInput) {
            return """
                    <!DOCTYPE html>
                    <html lang=\"en\">
                    <head>
                      <meta charset=\"UTF-8\" />
                      <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />
                      <title>Discord Bot Panel</title>
                      <style>
                        body { font-family: Arial, sans-serif; max-width: 700px; margin: 40px auto; padding: 0 16px; }
                        .top { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
                        h1 { margin: 0; }
                        p { color: #444; }
                        form { display: grid; gap: 10px; margin-top: 18px; }
                        input, textarea, button { padding: 10px; font-size: 14px; }
                        textarea { min-height: 120px; resize: vertical; }
                        button { cursor: pointer; }
                        .hint { font-size: 12px; color: #666; }
                        .status { font-size: 13px; padding: 8px 10px; border-radius: 8px; }
                        .status.success { background: #e9f8ee; color: #14532d; border: 1px solid #c7ebd4; }
                        .status.error { background: #feecec; color: #7f1d1d; border: 1px solid #fecaca; }
                      </style>
                    </head>
                    <body>
                      <div class=\"top\">
                        <h1>Discord Bot Send Panel</h1>
                        %s
                      </div>
                                            %s
                                            <form method=\"post\" action=\"/bot/start\" style=\"display:flex; gap:10px; margin-top: 14px;\">
                                                %s
                                                <button type=\"submit\">Start Bot</button>
                                            </form>
                                            <form method=\"post\" action=\"/bot/stop\" style=\"display:flex; gap:10px; margin-top: 10px;\">
                                                %s
                                                <button type=\"submit\">Stop Bot</button>
                                            </form>
                      <p>Send messages and images as your bot account.</p>
                      <form id=\"sendForm\" method=\"post\" action=\"/send\">
                        <label>Channel ID</label>
                        <input name=\"channelId\" value=\"%s\" placeholder=\"123456789012345678\" />
                        <div class=\"hint\">Leave blank only if BOT_DEFAULT_CHANNEL_ID is set.</div>

                        <label>Message</label>
                        <textarea id=\"messageBox\" name=\"message\" placeholder=\"Type your message...\"></textarea>

                        <label>Image URL (optional)</label>
                        <input name=\"imageUrl\" placeholder=\"https://example.com/image.png\" />

                        <label>Volume (0-150)</label>
                        <input name=\"volume\" type=\"number\" min=\"0\" max=\"150\" value=\"%s\" />

                        %s
                        <button type=\"submit\">Send as Bot</button>
                      </form>

                      <script>
                        // Enter sends. Shift+Enter creates a newline.
                        const messageBox = document.getElementById('messageBox');
                        const sendForm = document.getElementById('sendForm');
                        if (messageBox && sendForm) {
                          messageBox.addEventListener('keydown', function (event) {
                            if (event.key === 'Enter' && !event.shiftKey) {
                              event.preventDefault();
                              sendForm.requestSubmit();
                            }
                          });
                        }
                      </script>
                    </body>
                    </html>
                    """.formatted(statusHtml, botStatusHtml, keyInput, keyInput, escapeHtml(channelValue), escapeHtml(volumeValue), keyInput);
        }
    }

    private class SendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method Not Allowed");
                return;
            }

            Map<String, String> form = parseFormUrlEncoded(readRequestBody(exchange.getRequestBody()));

            if (panelKey != null && !panelKey.isBlank()) {
                String givenKey = form.getOrDefault("key", "");
                if (!panelKey.equals(givenKey)) {
                    sendRedirect(exchange, buildRedirect("error", "Invalid panel key.", form.get("channelId"), form.get("volume")));
                    return;
                }
            }

            Long channelId = resolveChannelId(form.get("channelId"));
            if (channelId == null) {
                sendRedirect(exchange, buildRedirect("error", "Missing or invalid Channel ID.", form.get("channelId"), form.get("volume")));
                return;
            }

            JDA jda = runtimeManager.getJda();
            if (jda == null || !runtimeManager.isOnline()) {
                sendRedirect(exchange, buildRedirect("error", "Bot is offline. Start it from the panel first.", form.get("channelId"), form.get("volume")));
                return;
            }

            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                sendRedirect(exchange, buildRedirect("error", "Channel not found for that ID.", form.get("channelId"), form.get("volume")));
                return;
            }

            String message = trimOrEmpty(form.get("message"));
            String imageUrl = trimOrEmpty(form.get("imageUrl"));
            String rawVolume = form.get("volume");
            Integer requestedVolume = null;
            if (hasText(rawVolume)) {
                requestedVolume = parseInteger(rawVolume);
                if (requestedVolume == null) {
                    sendRedirect(exchange, buildRedirect("error", "Volume must be a whole number.", form.get("channelId"), form.get("volume")));
                    return;
                }
            }

            if (message.isBlank() && imageUrl.isBlank() && requestedVolume == null) {
                sendRedirect(exchange, buildRedirect("error", "Message, image URL, or volume is required.", form.get("channelId"), form.get("volume")));
                return;
            }

            int appliedVolume;
            if (requestedVolume != null) {
                appliedVolume = PlayerManager.getInstance().setVolume(channel.getGuild(), requestedVolume);
            } else {
                appliedVolume = PlayerManager.getInstance().getVolume(channel.getGuild());
            }

            MessageCreateBuilder builder = new MessageCreateBuilder();
            if (!message.isBlank()) {
                builder.setContent(message);
            }
            if (!imageUrl.isBlank()) {
                builder.addEmbeds(new EmbedBuilder().setImage(imageUrl).build());
            }

            if (message.isBlank() && imageUrl.isBlank()) {
                sendRedirect(exchange, buildRedirect("success", "Volume set to " + appliedVolume + "%", form.get("channelId"), String.valueOf(appliedVolume)));
                return;
            }

            try {
                channel.sendMessage(builder.build()).complete();
                sendRedirect(exchange, buildRedirect("success", "Sent message. Volume is " + appliedVolume + "%", form.get("channelId"), String.valueOf(appliedVolume)));
            } catch (Exception e) {
                sendRedirect(exchange, buildRedirect("error", "Failed to send: " + e.getMessage(), form.get("channelId"), String.valueOf(appliedVolume)));
            }
        }

        private Integer parseInteger(String rawValue) {
            try {
                return Integer.parseInt(rawValue.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private boolean hasText(String value) {
            return value != null && !value.trim().isBlank();
        }

        private Long resolveChannelId(String fromForm) {
            String trimmed = trimOrEmpty(fromForm);
            if (trimmed.isBlank()) {
                return defaultChannelId;
            }

            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private class StartBotHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method Not Allowed");
                return;
            }

            Map<String, String> form = parseFormUrlEncoded(readRequestBody(exchange.getRequestBody()));
            if (!isPanelKeyValid(form)) {
                sendRedirect(exchange, buildRedirect("error", "Invalid panel key.", form.get("channelId"), form.get("volume")));
                return;
            }

            String result = runtimeManager.startBot();
            String status = runtimeManager.isOnline() ? "success" : "error";
            sendRedirect(exchange, buildRedirect(status, result, form.get("channelId"), form.get("volume")));
        }
    }

    private class StopBotHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method Not Allowed");
                return;
            }

            Map<String, String> form = parseFormUrlEncoded(readRequestBody(exchange.getRequestBody()));
            if (!isPanelKeyValid(form)) {
                sendRedirect(exchange, buildRedirect("error", "Invalid panel key.", form.get("channelId"), form.get("volume")));
                return;
            }

            String result = runtimeManager.stopBot();
            sendRedirect(exchange, buildRedirect("success", result, form.get("channelId"), form.get("volume")));
        }
    }

    private boolean isPanelKeyValid(Map<String, String> form) {
        if (panelKey == null || panelKey.isBlank()) {
            return true;
        }

        String givenKey = form.getOrDefault("key", "");
        return panelKey.equals(givenKey);
    }

    private String buildRedirect(String status, String message, String channelId, String volume) {
        String encodedStatus = URLEncoder.encode(status, StandardCharsets.UTF_8);
        String encodedMessage = URLEncoder.encode(trimOrEmpty(message), StandardCharsets.UTF_8);
        String encodedChannelId = URLEncoder.encode(trimOrEmpty(channelId), StandardCharsets.UTF_8);
        String encodedVolume = URLEncoder.encode(trimOrEmpty(volume), StandardCharsets.UTF_8);
        return "/?status=" + encodedStatus + "&message=" + encodedMessage + "&channelId=" + encodedChannelId + "&volume=" + encodedVolume;
    }

    private String readRequestBody(InputStream requestBody) throws IOException {
        return new String(requestBody.readAllBytes(), StandardCharsets.UTF_8);
    }

    private Map<String, String> parseFormUrlEncoded(String body) {
        Map<String, String> values = new HashMap<>();
        if (body == null || body.isBlank()) {
            return values;
        }

        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            String key = decodeUrl(keyValue[0]);
            String value = "";
            if (keyValue.length > 1) {
                value = decodeUrl(keyValue[1]);
            }
            values.put(key, value);
        }

        return values;
    }

    private String decodeUrl(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String trimOrEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private void sendHtml(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void sendText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void sendRedirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    private String escapeHtml(String input) {
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
