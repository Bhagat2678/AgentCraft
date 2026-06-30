package com.contextcraft.portal.controller;

import com.contextcraft.portal.entity.User;
import com.contextcraft.portal.repository.UserPhoneRepository;
import com.contextcraft.portal.security.JwtUtils;
import com.contextcraft.portal.security.PortalUserDetails;
import com.contextcraft.portal.service.UserService;
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
 * POST /api/v1/auth/token  — Accepts phone+invite-token, returns JWT
 * GET  /api/v1/auth/me     — Returns current principal info
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserPhoneRepository phoneRepository;
    private final UserService userService;
    private final JwtUtils jwtUtils;

    public AuthController(UserPhoneRepository phoneRepository,
                          UserService userService,
                          JwtUtils jwtUtils) {
        this.phoneRepository = phoneRepository;
        this.userService = userService;
        this.jwtUtils = jwtUtils;
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
}
