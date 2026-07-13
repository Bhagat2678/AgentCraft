package com.contextcraft.portal.controller;

import com.contextcraft.portal.entity.TelegramUser;
import com.contextcraft.portal.entity.User;
import com.contextcraft.portal.repository.TelegramUserRepository;
import com.contextcraft.portal.security.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 6 — Telegram Mini App Authentication
 *
 * Validates Telegram WebApp initData HMAC signature, then issues a JWT
 * for the React dashboard to use against all REST endpoints.
 *
 * Flow:
 *   1. Frontend calls POST /api/tma/auth with { initData: "..." }
 *   2. This controller validates the HMAC using the bot token
 *   3. Resolves the Telegram chatId → User → Business
 *   4. Returns a signed JWT with userId + businessId claims
 */
@RestController
@RequestMapping("/api/tma")
public class TelegramMiniAppController {

    private static final Logger log = LoggerFactory.getLogger(TelegramMiniAppController.class);

    @Value("${telegram.bot.token}")
    private String botToken;

    private final TelegramUserRepository telegramUserRepo;
    private final JwtUtils jwtUtils;

    public TelegramMiniAppController(TelegramUserRepository telegramUserRepo,
                                     JwtUtils jwtUtils) {
        this.telegramUserRepo = telegramUserRepo;
        this.jwtUtils = jwtUtils;
    }

    /**
     * Authenticate a Telegram Mini App session.
     * Validates initData HMAC, resolves user, returns JWT.
     */
    @PostMapping("/auth")
    public ResponseEntity<?> auth(@RequestBody Map<String, String> body) {
        String initData = body.get("initData");
        if (initData == null || initData.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "initData is required"));
        }

        try {
            // 1. Parse initData into a sorted map
            Map<String, String> params = parseInitData(initData);

            // 2. Validate HMAC
            if (!validateInitData(params, botToken)) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid Telegram initData signature"));
            }

            // 3. Extract chatId from user JSON
            String userJson = params.get("user");
            if (userJson == null) {
                return ResponseEntity.status(401).body(Map.of("error", "No user data in initData"));
            }
            Long chatId = extractChatId(userJson);
            if (chatId == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Could not parse chatId"));
            }

            // 4. Resolve Telegram user
            Optional<TelegramUser> tgUser = telegramUserRepo.findByChatId(chatId);
            if (tgUser.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found. Please start the bot first."));
            }
            User user = tgUser.get().getUser();
            if (!"ACTIVE".equals(user.getStatus())) {
                return ResponseEntity.status(403).body(Map.of("error", "Account is not active."));
            }

            // 5. Issue JWT
            String token = jwtUtils.generateToken(user.getId(), user.getBusiness().getId());

            return ResponseEntity.ok(Map.of(
                "token", token,
                "userId", user.getId().toString(),
                "businessId", user.getBusiness().getId().toString(),
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : "User",
                "businessName", user.getBusiness().getName()
            ));

        } catch (Exception e) {
            log.error("TMA auth error: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Authentication failed"));
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Map<String, String> parseInitData(String initData) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String part : initData.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    /**
     * Validates the Telegram WebApp initData HMAC-SHA256 signature.
     * https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app
     */
    private boolean validateInitData(Map<String, String> params, String botToken) throws Exception {
        String hash = params.get("hash");
        if (hash == null) return false;

        // Build data-check string: sorted key=value pairs (excluding hash), joined by \n
        String dataCheckString = params.entrySet().stream()
            .filter(e -> !e.getKey().equals("hash"))
            .sorted(Map.Entry.comparingByKey())
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining("\n"));

        // Secret key = HMAC-SHA256("WebAppData", botToken)
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] secretKey = mac.doFinal(botToken.getBytes(StandardCharsets.UTF_8));

        // Compute HMAC of data-check string using secret key
        mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
        byte[] computedHash = mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));

        String computedHex = HexFormat.of().formatHex(computedHash);
        return computedHex.equalsIgnoreCase(hash);
    }

    /** Extracts the Telegram user id from the user JSON string in initData. */
    private Long extractChatId(String userJson) {
        // Simple regex parse to avoid adding a JSON library dependency
        // Expected format: {"id":123456789,"first_name":"...","username":"..."}
        int idIdx = userJson.indexOf("\"id\":");
        if (idIdx < 0) return null;
        String rest = userJson.substring(idIdx + 5).trim();
        StringBuilder sb = new StringBuilder();
        for (char c : rest.toCharArray()) {
            if (Character.isDigit(c)) sb.append(c);
            else if (!sb.isEmpty()) break;
        }
        return sb.isEmpty() ? null : Long.parseLong(sb.toString());
    }
}
