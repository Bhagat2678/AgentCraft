package com.contextcraft.portal.controller;

import com.contextcraft.portal.entity.User;
import com.contextcraft.portal.repository.UserPhoneRepository;
import com.contextcraft.portal.security.JwtUtils;
import com.contextcraft.portal.security.PortalUserDetails;
import com.contextcraft.portal.service.UserService;
import com.contextcraft.portal.telegram.TelegramChatAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Minimal auth controller.
 *
 * In this WhatsApp-first system, JWT tokens are issued after the FSM
 * completes the portal setup or the user sends a one-time login request.
 * The REST token endpoint here supports the React dashboard flow where
 * a user who already went through WhatsApp onboarding can get a fresh JWT.
 *
 * POST /api/v1/auth/token    — Accepts phone+invite-token, returns JWT
 * POST /api/v1/auth/telegram — Accepts initData, validates, returns JWT
 * GET  /api/v1/auth/me       — Returns current principal info
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserPhoneRepository phoneRepository;
    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final TelegramChatAdapter telegramChatAdapter;
    private final ObjectMapper objectMapper;

    public AuthController(UserPhoneRepository phoneRepository,
                          UserService userService,
                          JwtUtils jwtUtils,
                          TelegramChatAdapter telegramChatAdapter,
                          ObjectMapper objectMapper) {
        this.phoneRepository = phoneRepository;
        this.userService = userService;
        this.jwtUtils = jwtUtils;
        this.telegramChatAdapter = telegramChatAdapter;
        this.objectMapper = objectMapper;
    }

    /**
     * POST /api/v1/auth/token
     * Exchanges a phone number + invite token for a JWT.
     *
     * Request: { "phoneNumber": "+15550001234", "token": "abc..." }
     * Response: { "accessToken": "eyJ..." }
     */
    @PostMapping("/token")
    public ResponseEntity<?> getToken(@RequestBody Map<String, String> body) {
        String phoneNumber = body.get("phoneNumber");
        String inviteToken = body.get("token");

        if (phoneNumber == null && inviteToken == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "phoneNumber or token is required"));
        }

        try {
            User user;
            if (inviteToken != null) {
                user = userService.acceptInvite(inviteToken);
            } else {
                user = userService.findByPhone(phoneNumber);
                if (!"ACTIVE".equals(user.getStatus())) {
                    return ResponseEntity.status(403)
                            .body(Map.of("error", "Account is not active. Check your invite."));
                }
            }

            String jwt = jwtUtils.generateToken(user.getId(), user.getBusiness().getId());
            return ResponseEntity.ok(Map.of("accessToken", jwt));

        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Authentication failed: " + e.getMessage()));
        }
    }

    /**
     * POST /api/v1/auth/login
     * Authenticates a user by email, business name, and portal password.
     *
     * Request: { "email": "...", "businessName": "...", "password": "..." }
     * Response: { "accessToken": "eyJ..." }
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String businessName = body.get("businessName");
        String password = body.get("password");

        if (email == null || businessName == null || password == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "email, businessName, and password are required"));
        }

        try {
            User user = userService.loginByEmailAndPortalName(email, businessName, password);
            String jwt = jwtUtils.generateToken(user.getId(), user.getBusiness().getId());
            return ResponseEntity.ok(Map.of("accessToken", jwt));
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Authentication failed: " + e.getMessage()));
        }
    }

    /**
     * POST /api/v1/auth/telegram
     * Authenticates a Telegram Mini App session using WebApp initData.
     *
     * Request: { "initData": "query_string..." }
     * Response: { "accessToken": "eyJ..." }
     */
    @PostMapping("/telegram")
    public ResponseEntity<?> authenticateTelegram(@RequestBody Map<String, String> body) {
        String initData = body.get("initData");
        if (initData == null || initData.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "initData is required"));
        }

        if (!telegramChatAdapter.validateInitData(initData)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid initData signature"));
        }

        try {
            Long chatId = extractChatIdFromInitData(initData);
            if (chatId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Could not extract user chat ID from initData"));
            }

            User user = userService.findByTelegramChatId(chatId)
                    .orElseThrow(() -> new RuntimeException("No registered portal user found for Telegram Chat ID: " + chatId));

            if (!"ACTIVE".equals(user.getStatus())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Account is not active. Check your invite."));
            }

            String jwt = jwtUtils.generateToken(user.getId(), user.getBusiness().getId());
            return ResponseEntity.ok(Map.of("accessToken", jwt));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Authentication failed: " + e.getMessage()));
        }
    }

    /**
     * GET /api/v1/auth/me
     * Returns the currently authenticated user's principal info.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMe(@AuthenticationPrincipal PortalUserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        return ResponseEntity.ok(Map.of(
                "userId", principal.getUserId(),
                "businessId", principal.getBusinessId(),
                "phoneNumber", principal.getPhoneNumber() != null ? principal.getPhoneNumber() : ""
        ));
    }

    private Long extractChatIdFromInitData(String initData) {
        try {
            String[] pairs = initData.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx == -1) continue;
                String key = java.net.URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                if ("user".equals(key)) {
                    String userJson = java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    JsonNode node = objectMapper.readTree(userJson);
                    if (node.has("id")) {
                        return node.get("id").asLong();
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
}
