package com.contextcraft.portal.telegram;

import com.contextcraft.portal.config.TelegramProperties;
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
