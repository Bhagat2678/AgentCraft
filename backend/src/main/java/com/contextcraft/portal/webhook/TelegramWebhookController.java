package com.contextcraft.portal.webhook;

import com.contextcraft.portal.config.TelegramProperties;
import com.contextcraft.portal.fsm.ConversationFsm;
import com.contextcraft.portal.telegram.TelegramChatAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Handles Telegram Bot API webhook events.
 *
 * POST /api/v1/telegram/webhook — Incoming Telegram Update stream
 *
 * Security:
 *  - Validates the X-Telegram-Bot-Api-Secret-Token header against
 *    the configured webhook secret.
 */
@RestController
@RequestMapping("/api/v1/telegram/webhook")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    private final TelegramProperties props;
    private final ConversationFsm conversationFsm;
    private final ObjectMapper objectMapper;

    public TelegramWebhookController(TelegramProperties props,
                                      ConversationFsm conversationFsm,
                                      ObjectMapper objectMapper) {
        this.props = props;
        this.conversationFsm = conversationFsm;
        this.objectMapper = objectMapper;
    }

    /**
     * Receives all Telegram updates. Telegram expects a 200 OK quickly.
     *
     * @param secretToken X-Telegram-Bot-Api-Secret-Token header for verification
     * @param rawBody     Raw UTF-8 request body
     */
    @PostMapping(consumes = "application/json")
    public ResponseEntity<String> receiveUpdate(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
            @RequestBody byte[] rawBody) {

        // 1. Validate secret token
        if (!isValidSecret(secretToken)) {
            log.warn("❌ Invalid X-Telegram-Bot-Api-Secret-Token. Rejecting update.");
            return ResponseEntity.status(401).body("Unauthorized");
        }

        // 2. Parse the update JSON
        JsonNode update;
        try {
            update = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.error("Failed to parse Telegram update: {}", e.getMessage());
            return ResponseEntity.ok("OK");
        }

        // 3. Handle text messages
        if (update.has("message")) {
            JsonNode message = update.get("message");
            processMessage(message);
        }

        // 4. Handle callback queries (inline keyboard button presses)
        if (update.has("callback_query")) {
            JsonNode callbackQuery = update.get("callback_query");
            processCallbackQuery(callbackQuery);
        }

        return ResponseEntity.ok("OK");
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void processMessage(JsonNode message) {
        try {
            Long chatId = message.get("chat").get("id").asLong();
            String text = "";

            if (message.has("text")) {
                text = message.get("text").asText();
            }

            // Extract username if available
            String username = null;
            if (message.has("from") && message.get("from").has("username")) {
                username = message.get("from").get("username").asText();
            }

            String messageId = message.has("message_id")
                    ? message.get("message_id").asText()
                    : "unknown";

            String fsmKey = TelegramChatAdapter.toFsmKey(chatId);

            log.info("Incoming Telegram message chatId={} user={} text={}",
                    chatId, username,
                    text.length() > 50 ? text.substring(0, 50) + "…" : text);

            // Strip the /start command prefix for cleaner FSM input
            if (text.startsWith("/start")) {
                text = text.length() > 7 ? text.substring(7).trim() : "";
            }

            conversationFsm.process(fsmKey, text, messageId);

        } catch (Exception e) {
            log.error("Error processing Telegram message: {}", e.getMessage(), e);
        }
    }

    private void processCallbackQuery(JsonNode callbackQuery) {
        try {
            Long chatId = callbackQuery.get("message").get("chat").get("id").asLong();
            String data = callbackQuery.has("data") ? callbackQuery.get("data").asText() : "";
            String callbackId = callbackQuery.get("id").asText();

            String fsmKey = TelegramChatAdapter.toFsmKey(chatId);

            log.info("Incoming Telegram callback chatId={} data={}", chatId, data);

            conversationFsm.process(fsmKey, data, callbackId);

        } catch (Exception e) {
            log.error("Error processing Telegram callback query: {}", e.getMessage(), e);
        }
    }

    private boolean isValidSecret(String secretToken) {
        String expected = props.getWebhookSecret();
        if (expected == null || "REPLACE_ME".equals(expected)) {
            log.warn("Telegram webhook secret not configured — skipping validation (dev mode only!)");
            return true;
        }
        return expected.equals(secretToken);
    }
}
