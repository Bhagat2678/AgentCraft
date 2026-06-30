package com.contextcraft.portal.whatsapp;

import com.contextcraft.portal.config.WhatsAppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Adapter for the Meta WhatsApp Cloud API.
 *
 * Handles:
 *  - Sending plain text messages (in-session, within 24h window)
 *  - Sending approved template messages (out-of-session notifications)
 *  - Marking messages as read
 *  - Exponential backoff retry on transient failures
 *
 * Meta API base: https://graph.facebook.com/v19.0/{phone-number-id}/messages
 */
@Service
public class WhatsAppChatAdapter {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppChatAdapter.class);
    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 500L;

    private final WhatsAppProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WhatsAppChatAdapter(WhatsAppProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    // ─── Text Message ─────────────────────────────────────────────────────────

    /**
     * Sends a plain text message to a recipient (in-session use).
     *
     * @param toPhoneNumber E.164 format, e.g. "+15550000001"
     * @param text          Message body (max 4096 chars)
     */
    public boolean sendText(String toPhoneNumber, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", toPhoneNumber);
        body.put("type", "text");
        body.put("text", Map.of("preview_url", false, "body", text));

        return sendWithRetry(body, "text", toPhoneNumber);
    }

    // ─── Template Message ──────────────────────────────────────────────────────

    /**
     * Sends an approved WhatsApp template message (out-of-session or notification).
     *
     * @param toPhoneNumber  Recipient phone
     * @param templateName   Approved template name registered in Meta Business Manager
     * @param languageCode   e.g. "en_US"
     * @param components     List of component objects (header, body, button params)
     */
    public boolean sendTemplate(String toPhoneNumber, String templateName,
                                String languageCode, List<Map<String, Object>> components) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", languageCode));
        if (components != null && !components.isEmpty()) {
            template.put("components", components);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", toPhoneNumber);
        body.put("type", "template");
        body.put("template", template);

        return sendWithRetry(body, "template:" + templateName, toPhoneNumber);
    }

    // ─── Convenience template helpers ─────────────────────────────────────────

    /**
     * Builds a body component with positional text parameters for a template.
     * Usage: sendTemplate(phone, "task_assigned", "en_US",
     *            List.of(buildBodyComponent("Alice", "Deploy v2.0", "2025-08-01")))
     */
    public Map<String, Object> buildBodyComponent(String... paramValues) {
        List<Map<String, String>> params = new ArrayList<>();
        for (String val : paramValues) {
            params.add(Map.of("type", "text", "text", val));
        }
        return Map.of("type", "body", "parameters", params);
    }

    // ─── Mark as Read ─────────────────────────────────────────────────────────

    /**
     * Marks an incoming message as read (displays blue ticks to sender).
     *
     * @param messageId WhatsApp message ID (wamid.xxx) from the webhook payload
     */
    public void markAsRead(String messageId) {
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "status", "read",
                "message_id", messageId
        );
        try {
            sendRaw(body);
        } catch (Exception e) {
            log.warn("Failed to mark message {} as read: {}", messageId, e.getMessage());
        }
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private boolean sendWithRetry(Map<String, Object> body, String type, String to) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<String> response = sendRaw(body);
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
                long backoff = BASE_BACKOFF_MS * (1L << (attempt - 1)); // 500ms, 1000ms
                try { Thread.sleep(backoff); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        log.error("Failed to send {} message to {} after {} retries", type, to, MAX_RETRIES);
        return false;
    }

    private ResponseEntity<String> sendRaw(Map<String, Object> body) {
        String url = props.getApiUrl() + "/" + props.getPhoneNumberId() + "/messages";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getAccessToken());

        try {
            String json = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(json, headers);
            return restTemplate.postForEntity(url, entity, String.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize or send WhatsApp message", e);
        }
    }
}
