package com.contextcraft.portal.telegram;

import com.contextcraft.portal.config.TelegramProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Adapter for the Telegram Bot API.
 *
 * Handles:
 *  - Sending plain text messages
 *  - Sending messages with inline keyboard markup
 *  - Exponential backoff retry on transient failures
 *
 * Telegram Bot API base: https://api.telegram.org/bot{token}/
 */
@Service
public class TelegramChatAdapter {

    private static final Logger log = LoggerFactory.getLogger(TelegramChatAdapter.class);
    private static final String API_BASE = "https://api.telegram.org/bot";
    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 500L;

    private final TelegramProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public TelegramChatAdapter(TelegramProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    // ─── Text Message ─────────────────────────────────────────────────────────

    /**
     * Sends a plain text message to a Telegram chat.
     *
     * @param chatId  Telegram chat ID
     * @param text    Message body (supports Markdown)
     */
    public boolean sendText(Long chatId, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "Markdown");

        return sendWithRetry("sendMessage", body, "text", chatId.toString());
    }

    /**
     * Sends a text message to a destination identified by the FSM key format
     * "telegram:{chatId}". This bridges the FSM (which uses String keys) with the
     * Telegram adapter (which uses Long chatId).
     *
     * @param fsmKey  FSM key in format "telegram:{chatId}"
     * @param text    Message body
     */
    public boolean sendTextByFsmKey(String fsmKey, String text) {
        Long chatId = parseChatId(fsmKey);
        if (chatId == null) {
            log.error("Invalid Telegram FSM key: {}", fsmKey);
            return false;
        }
        return sendText(chatId, text);
    }

    /**
     * Fetches the current bot webhook registration details from Telegram.
     */
    public JsonNode getWebhookInfo() {
        try {
            ResponseEntity<String> response = sendRaw("getWebhookInfo", Collections.emptyMap());
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Failed to fetch webhook info: {}", response.getStatusCode());
                return null;
            }
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.warn("Exception while fetching webhook info: {}", e.getMessage());
            return null;
        }
    }

    public boolean registerWebhook(String webhookUrl, String secretToken) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("url", webhookUrl);
            body.put("secret_token", secretToken);

            ResponseEntity<String> response = sendRaw("setWebhook", body);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Telegram setWebhook failed: {}", response.getStatusCode());
                return false;
            }

            JsonNode json = objectMapper.readTree(response.getBody());
            boolean ok = json.path("ok").asBoolean(false);
            if (!ok) {
                log.error("Telegram setWebhook returned error: {}",
                        json.path("description").asText("unknown error"));
                return false;
            }

            log.info("Telegram webhook registered successfully at {}", webhookUrl);
            return true;
        } catch (Exception e) {
            log.error("Failed to register Telegram webhook: {}", e.getMessage(), e);
            return false;
        }
    }

    // ─── Inline Keyboard ──────────────────────────────────────────────────────

    /**
     * Sends a message with an inline keyboard.
     *
     * @param chatId   Telegram chat ID
     * @param text     Message body
     * @param keyboard List of rows; each row is a list of button maps with "text" and "callback_data"
     */
    public boolean sendWithInlineKeyboard(Long chatId, String text,
                                           List<List<Map<String, String>>> keyboard) {
        Map<String, Object> replyMarkup = Map.of("inline_keyboard", keyboard);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "Markdown");
        body.put("reply_markup", replyMarkup);

        return sendWithRetry("sendMessage", body, "keyboard", chatId.toString());
    }

    // ─── Convenience: build inline keyboard button ─────────────────────────────

    /**
     * Creates a single inline keyboard button.
     */
    public static Map<String, String> button(String text, String callbackData) {
        Map<String, String> btn = new LinkedHashMap<>();
        btn.put("text", text);
        btn.put("callback_data", callbackData);
        return btn;
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    /**
     * Extracts the numeric chat ID from a FSM key of the form "telegram:{chatId}".
     */
    public static Long parseChatId(String fsmKey) {
        if (fsmKey != null && fsmKey.startsWith("telegram:")) {
            try {
                return Long.parseLong(fsmKey.substring("telegram:".length()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Creates the FSM key from a Telegram chat ID.
     */
    public static String toFsmKey(Long chatId) {
        return "telegram:" + chatId;
    }

    // ─── No-op markAsRead (Telegram has no equivalent) ────────────────────────

    /**
     * No-op: Telegram does not support "mark as read" like WhatsApp.
     * Kept for interface parity so the FSM can call it without branching.
     */
    public void markAsRead(String messageId) {
        // Telegram does not support this operation
    }

    /**
     * Answers a callback query from an inline keyboard.
     *
     * @param callbackQueryId  The ID of the callback query
     * @param text             Optional text to display to the user
     */
    public boolean answerCallbackQuery(String callbackQueryId, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("callback_query_id", callbackQueryId);
        if (text != null) {
            body.put("text", text);
        }
        return sendWithRetry("answerCallbackQuery", body, "answerCallbackQuery", callbackQueryId);
    }

    /**
     * Validates Telegram Mini App initData using HMAC-SHA256.
     * Returns true if valid.
     */
    public boolean validateInitData(String initData) {
        if (initData == null || initData.isBlank()) return false;
        try {
            // Parse query parameters
            Map<String, String> params = new LinkedHashMap<>();
            String[] pairs = initData.split("&");
            String hash = null;
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx == -1) continue;
                String key = java.net.URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                String value = java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                if ("hash".equals(key)) {
                    hash = value;
                } else {
                    params.put(key, value);
                }
            }
            if (hash == null) return false;

            // Sort keys alphabetically
            List<String> keys = new ArrayList<>(params.keySet());
            Collections.sort(keys);

            // Construct data check string
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                sb.append(key).append("=").append(params.get(key));
                if (i < keys.size() - 1) {
                    sb.append("\n");
                }
            }

            // Derive key: HMAC-SHA256("WebAppData", botToken)
            byte[] secretKey = hmacSha256("WebAppData", props.getBotToken().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // Compute hash: HMAC-SHA256(dataCheckString, secretKey)
            byte[] computedHashBytes = hmacSha256(sb.toString(), secretKey);

            // Hex encode
            StringBuilder hexString = new StringBuilder();
            for (byte b : computedHashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString().equals(hash);
        } catch (Exception e) {
            log.error("Failed to validate initData", e);
            return false;
        }
    }

    private byte[] hmacSha256(String data, byte[] key) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256");
        mac.init(secretKeySpec);
        return mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private boolean sendWithRetry(String method, Map<String, Object> body,
                                   String type, String to) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<String> response = sendRaw(method, body);
                if (response.getStatusCode().is2xxSuccessful()) {
                    log.debug("Sent {} message to {}", type, to);
                    return true;
                }
                log.warn("Non-2xx sending {} to {} (attempt {}): {} {}",
                        type, to, attempt, response.getStatusCode(), response.getBody());
            } catch (Exception e) {
                log.warn("Exception sending {} to {} (attempt {}): {}", type, to, attempt, e.getMessage());
            }

            if (attempt < MAX_RETRIES) {
                long backoff = BASE_BACKOFF_MS * (1L << (attempt - 1));
                try { Thread.sleep(backoff); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        log.error("Failed to send {} message to {} after {} retries", type, to, MAX_RETRIES);
        return false;
    }

    private ResponseEntity<String> sendRaw(String method, Map<String, Object> body) {
        String url = API_BASE + props.getBotToken() + "/" + method;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            String json = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(json, headers);
            return restTemplate.postForEntity(url, entity, String.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize or send Telegram message", e);
        }
    }
}
