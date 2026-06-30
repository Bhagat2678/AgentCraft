package com.contextcraft.portal.webhook;

import com.contextcraft.portal.config.WhatsAppProperties;
import com.contextcraft.portal.fsm.ConversationFsm;
import com.contextcraft.portal.webhook.dto.WebhookPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

/**
 * Handles WhatsApp Business Cloud API webhook events from Meta.
 *
 * GET  /api/v1/webhook  — Hub challenge verification (initial subscription setup)
 * POST /api/v1/webhook  — Incoming event stream (messages, statuses, etc.)
 *
 * Security:
 *  - GET uses the verify_token challenge handshake
 *  - POST validates X-Hub-Signature-256 HMAC-SHA256 using the app secret
 *    (computed over the raw request body — must use raw bytes, not parsed JSON)
 */
@RestController
@RequestMapping("/api/v1/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WhatsAppProperties props;
    private final ConversationFsm conversationFsm;
    private final ObjectMapper objectMapper;

    public WebhookController(WhatsAppProperties props,
                             ConversationFsm conversationFsm,
                             ObjectMapper objectMapper) {
        this.props = props;
        this.conversationFsm = conversationFsm;
        this.objectMapper = objectMapper;
    }

    // ─── GET: Hub Challenge Verification ─────────────────────────────────────

    /**
     * Meta sends a GET request when you first subscribe the webhook.
     * We verify the token and echo back the challenge.
     */
    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && props.getVerifyToken().equals(token)) {
            log.info("✅ Webhook verified successfully.");
            return ResponseEntity.ok(challenge);
        }

        log.warn("❌ Webhook verification failed. mode={} token={}", mode, token);
        return ResponseEntity.status(403).body("Forbidden");
    }

    // ─── POST: Event Handler ──────────────────────────────────────────────────

    /**
     * Receives all WhatsApp events. Meta expects a 200 OK quickly;
     * heavy processing is done synchronously here but can be moved to
     * async (@Async / a queue) for production scaling.
     *
     * @param signatureHeader X-Hub-Signature-256 header value (sha256=<hex>)
     * @param rawBody         Raw UTF-8 request body bytes (required for HMAC)
     */
    @PostMapping(consumes = "application/json")
    public ResponseEntity<String> receiveEvent(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader,
            @RequestBody byte[] rawBody) {

        // 1. Validate HMAC signature
        if (!isValidSignature(rawBody, signatureHeader)) {
            log.warn("❌ Invalid X-Hub-Signature-256. Possible replay or forged request.");
            return ResponseEntity.status(401).body("Unauthorized");
        }

        // 2. Parse payload
        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, WebhookPayload.class);
        } catch (Exception e) {
            log.error("Failed to parse webhook payload: {}", e.getMessage());
            return ResponseEntity.ok("OK"); // Always ACK to avoid Meta retries for parse errors
        }

        // 3. Guard: only process whatsapp_business_account events
        if (!"whatsapp_business_account".equals(payload.getObject())) {
            return ResponseEntity.ok("OK");
        }

        // 4. Route each message to the FSM
        if (payload.getEntry() != null) {
            for (WebhookPayload.Entry entry : payload.getEntry()) {
                if (entry.getChanges() == null) continue;
                for (WebhookPayload.Change change : entry.getChanges()) {
                    if (!"messages".equals(change.getField())) continue;
                    WebhookPayload.ChangeValue value = change.getValue();
                    if (value == null || value.getMessages() == null) continue;

                    for (WebhookPayload.Message message : value.getMessages()) {
                        processMessage(message);
                    }
                }
            }
        }

        // Always return 200 quickly — Meta will retry if we return non-200
        return ResponseEntity.ok("OK");
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void processMessage(WebhookPayload.Message message) {
        try {
            String fromPhone = "+" + message.getFrom(); // Meta strips leading "+"
            String body = message.extractTextBody();
            String messageId = message.getId();

            log.info("Incoming message from={} type={} body={}",
                    fromPhone, message.getType(), body.length() > 50 ? body.substring(0, 50) + "…" : body);

            conversationFsm.process(fromPhone, body, messageId);

        } catch (Exception e) {
            log.error("Error processing message id={}: {}", message.getId(), e.getMessage(), e);
        }
    }

    /**
     * Validates the X-Hub-Signature-256 header against the raw body.
     * Header format: "sha256=<lowercase-hex-digest>"
     */
    private boolean isValidSignature(byte[] rawBody, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            // Allow missing header in dev/test mode when app secret is placeholder
            if ("REPLACE_ME".equals(props.getAppSecret())) {
                log.warn("App secret not configured — skipping HMAC validation (dev mode only!)");
                return true;
            }
            return false;
        }

        try {
            String expectedHex = signatureHeader.substring(7);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    props.getAppSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(rawBody);
            String computedHex = HexFormat.of().formatHex(computed);
            return constantTimeEquals(computedHex, expectedHex);
        } catch (Exception e) {
            log.error("HMAC validation error: {}", e.getMessage());
            return false;
        }
    }

    /** Constant-time string comparison to prevent timing attacks. */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
