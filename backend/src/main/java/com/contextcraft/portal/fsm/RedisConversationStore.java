package com.contextcraft.portal.fsm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed store for per-phone FSM conversation contexts.
 *
 * Key format: conversation_state:{phoneNumber}
 * TTL:        48 hours (reset on every write)
 *
 * On Redis miss, falls back to PostgreSQL via ConversationStateRepository
 * to survive Redis restarts without losing in-flight conversations.
 */
@Service
public class RedisConversationStore {

    private static final Logger log = LoggerFactory.getLogger(RedisConversationStore.class);
    private static final String KEY_PREFIX = "conversation_state:";
    private static final Duration TTL = Duration.ofHours(48);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final com.contextcraft.portal.repository.ConversationStateRepository dbRepo;

    public RedisConversationStore(StringRedisTemplate redisTemplate,
                                  ObjectMapper objectMapper,
                                  com.contextcraft.portal.repository.ConversationStateRepository dbRepo) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.dbRepo = dbRepo;
    }

    /**
     * Loads the FSM context for a phone number.
     * Tries Redis first; falls back to PostgreSQL for recovery after Redis restart.
     */
    public Optional<FsmContext> load(String phoneNumber) {
        String key = key(phoneNumber);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return Optional.of(objectMapper.readValue(json, FsmContext.class));
            }
        } catch (Exception e) {
            log.warn("Redis read failed for {}, trying DB fallback: {}", phoneNumber, e.getMessage());
        }

        // Fallback: load from PostgreSQL conversation_states table
        return dbRepo.findById(phoneNumber).map(dbState -> {
            FsmContext ctx = new FsmContext();
            ctx.setPhoneNumber(phoneNumber);
            ctx.setState(FsmState.valueOf(dbState.getCurrentState()));
            ctx.setBusinessId(dbState.getBusinessId());
            ctx.setUserId(dbState.getUserId());
            if (dbState.getContext() != null) {
                try {
                    String ctxJson = objectMapper.writeValueAsString(dbState.getContext());
                    FsmContext full = objectMapper.readValue(ctxJson, FsmContext.class);
                    return full;
                } catch (Exception ex) {
                    log.warn("Could not deserialize DB context for {}", phoneNumber);
                }
            }
            return ctx;
        });
    }

    /**
     * Saves context to Redis (primary) and PostgreSQL (durable backup).
     */
    public void save(FsmContext ctx) {
        String key = key(ctx.getPhoneNumber());
        try {
            String json = objectMapper.writeValueAsString(ctx);
            redisTemplate.opsForValue().set(key, json, TTL);
        } catch (Exception e) {
            log.error("Failed to save FSM context to Redis for {}: {}", ctx.getPhoneNumber(), e.getMessage());
        }

        // Persist to DB for recovery
        try {
            com.contextcraft.portal.entity.ConversationState dbState =
                    dbRepo.findById(ctx.getPhoneNumber())
                          .orElse(new com.contextcraft.portal.entity.ConversationState());
            dbState.setPhoneNumber(ctx.getPhoneNumber());
            dbState.setCurrentState(ctx.getState().name());
            dbState.setBusinessId(ctx.getBusinessId());
            dbState.setUserId(ctx.getUserId());
            dbRepo.save(dbState);
        } catch (Exception e) {
            log.warn("Failed to persist FSM context to DB for {}: {}", ctx.getPhoneNumber(), e.getMessage());
        }
    }

    /**
     * Deletes the conversation context (e.g., after portal deletion or test reset).
     */
    public void delete(String phoneNumber) {
        redisTemplate.delete(key(phoneNumber));
        dbRepo.deleteById(phoneNumber);
    }

    private String key(String phoneNumber) {
        return KEY_PREFIX + phoneNumber;
    }
}
