package com.contextcraft.portal.scheduler;

import com.contextcraft.portal.fsm.FsmContext;
import com.contextcraft.portal.fsm.FsmState;
import com.contextcraft.portal.fsm.RedisConversationStore;
import com.contextcraft.portal.telegram.TelegramChatAdapter;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Phase 5 — Session Timeout Job
 *
 * Runs every 30 minutes. Scans Redis for FSM sessions that have been
 * idle (in a mid-flow state) for > 30 minutes and gently prompts the user
 * to resume or type /start.
 *
 * Key convention: conversation_state:{fsmKey}
 * We rely on Redis TTL being 48h (set by RedisConversationStore).
 * This job only nudges mid-flow sessions — IDLE and NEW states are ignored.
 */
@Component
public class SessionTimeoutJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(SessionTimeoutJob.class);

    // States that indicate an incomplete multi-step wizard — nudge these
    private static final Set<String> MID_FLOW_STATES = Set.of(
        "SETUP_NAME", "SETUP_USERNAME", "SETUP_BNAME", "SETUP_BBUSINESS",
        "SETUP_BDESC", "SETUP_EMP_COUNT", "SETUP_EMAIL", "SETUP_DEPT_COUNT",
        "SETUP_DEPT_NAMES", "SETUP_PASSWORD", "SETUP_PASSWORD_CONFIRM",
        "SETUP_BTYPE", "SETUP_CONFIRM",
        "LOGIN_USERNAME", "LOGIN_BNAME", "LOGIN_EMAIL", "LOGIN_PASSWORD",
        "TASK_TITLE", "TASK_DESC", "TASK_DUE", "TASK_PRIORITY", "TASK_ASSIGN", "TASK_CONFIRM",
        "INVITE_EMP_NAME", "INVITE_EMP_EMAIL", "INVITE_PHONE", "INVITE_ROLE",
        "INVITE_DEPT", "INVITE_CONFIRM",
        "EMAIL_MEETING_CHOICE", "EMAIL_MEETING_MANUAL_BODY", "EMAIL_MEETING_DATE",
        "EMAIL_MEETING_TIME", "EMAIL_MEETING_SUBJECT",
        "EMAIL_MGR_BODY", "EMAIL_MGR_CONFIRM", "EMAIL_MGR_ADDRESS"
    );

    private final StringRedisTemplate redisTemplate;
    private final RedisConversationStore store;
    private final TelegramChatAdapter telegram;

    public SessionTimeoutJob(StringRedisTemplate redisTemplate,
                             RedisConversationStore store,
                             TelegramChatAdapter telegram) {
        this.redisTemplate = redisTemplate;
        this.store = store;
        this.telegram = telegram;
    }

    @Override
    public void execute(JobExecutionContext context) {
        log.debug("SessionTimeoutJob running — scanning mid-flow sessions...");
        try {
            Set<String> keys = redisTemplate.keys("conversation_state:telegram:*");
            if (keys == null) return;

            int nudged = 0;
            for (String key : keys) {
                try {
                    // Extract fsmKey from Redis key
                    String fsmKey = key.replace("conversation_state:", "");
                    store.load(fsmKey).ifPresent(ctx -> {
                        if (ctx.getState() != null && MID_FLOW_STATES.contains(ctx.getState().name())) {
                            telegram.sendTextByFsmKey(fsmKey,
                                "⏰ *Your session has been idle for a while.*\n\n" +
                                "You were in the middle of something. Type /start to resume, or /cancel to start fresh.");
                        }
                    });
                    nudged++;
                } catch (Exception e) {
                    log.warn("Error checking session for key {}: {}", key, e.getMessage());
                }
            }
            log.info("SessionTimeoutJob nudged {} mid-flow sessions", nudged);
        } catch (Exception e) {
            log.error("SessionTimeoutJob error: {}", e.getMessage(), e);
        }
    }
}
