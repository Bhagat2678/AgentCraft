package com.contextcraft.portal.telegram;

import com.contextcraft.portal.config.TelegramProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers the Telegram webhook automatically on application startup when a
 * public webhook URL is configured.
 */
@Component
public class TelegramWebhookRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookRegistrar.class);

    private final TelegramProperties props;
    private final TelegramChatAdapter telegramChatAdapter;
    private final Environment env;

    public TelegramWebhookRegistrar(TelegramProperties props,
                                    TelegramChatAdapter telegramChatAdapter,
                                    Environment env) {
        this.props = props;
        this.telegramChatAdapter = telegramChatAdapter;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        String publicWebhookUrl = resolveProperty(props.getPublicWebhookUrl(), "TELEGRAM_PUBLIC_WEBHOOK_URL");
        String botToken = resolveProperty(props.getBotToken(), "TELEGRAM_BOT_TOKEN");
        String webhookSecret = resolveProperty(props.getWebhookSecret(), "TELEGRAM_WEBHOOK_SECRET");

        props.setPublicWebhookUrl(publicWebhookUrl);
        props.setBotToken(botToken);
        props.setWebhookSecret(webhookSecret);

        if (!StringUtils.hasText(publicWebhookUrl)) {
            log.info("Telegram webhook auto-registration disabled. Set TELEGRAM_PUBLIC_WEBHOOK_URL to enable it.");
            return;
        }

        if (!StringUtils.hasText(botToken) || "REPLACE_ME".equals(botToken)) {
            log.warn("Telegram webhook registration skipped because app.telegram.bot-token is not configured.");
            return;
        }

        if (!StringUtils.hasText(webhookSecret) || "REPLACE_ME".equals(webhookSecret)) {
            log.warn("Telegram webhook registration skipped because app.telegram.webhook-secret is not configured.");
            return;
        }

        if (!publicWebhookUrl.startsWith("https://")) {
            log.warn("Telegram public webhook URL must use HTTPS. Skipping registration: {}", publicWebhookUrl);
            return;
        }

        if (publicWebhookUrl.endsWith("/")) {
            publicWebhookUrl = publicWebhookUrl.substring(0, publicWebhookUrl.length() - 1);
        }
        if (!publicWebhookUrl.endsWith("/api/v1/telegram/webhook")) {
            publicWebhookUrl = publicWebhookUrl + "/api/v1/telegram/webhook";
        }

        props.setPublicWebhookUrl(publicWebhookUrl);

        log.info("Checking Telegram webhook registration for {}", publicWebhookUrl);

        JsonNode webhookInfo = telegramChatAdapter.getWebhookInfo();
        if (webhookInfo == null) {
            log.warn("Could not fetch Telegram webhook info. Attempting registration anyway.");
            telegramChatAdapter.registerWebhook(publicWebhookUrl, webhookSecret);
            return;
        }

        if (!webhookInfo.path("ok").asBoolean(false)) {
            log.warn("Telegram webhook info returned non-ok response: {}", webhookInfo.toString());
            telegramChatAdapter.registerWebhook(publicWebhookUrl, webhookSecret);
            return;
        }

        String currentUrl = webhookInfo.path("result").path("url").asText("");
        if (currentUrl.isBlank()) {
            log.info("No existing Telegram webhook is configured. Registering {}", publicWebhookUrl);
            telegramChatAdapter.registerWebhook(publicWebhookUrl, webhookSecret);
            return;
        }

        if (publicWebhookUrl.equals(currentUrl)) {
            log.info("Telegram webhook is already registered at {}", publicWebhookUrl);
            return;
        }

        log.info("Telegram webhook currently registered at {}. Re-registering to {}", currentUrl, publicWebhookUrl);
        telegramChatAdapter.registerWebhook(publicWebhookUrl, webhookSecret);
    }

    private String resolveProperty(String configuredValue, String envName) {
        String cwd = Paths.get("").toAbsolutePath().toString();
        log.info("Resolving Telegram property {} from configured='{}' env='{}' cwd={}",
                envName,
                StringUtils.hasText(configuredValue) ? configuredValue : "<blank>",
                env.getProperty(envName),
                cwd);

        if (StringUtils.hasText(configuredValue) && !"REPLACE_ME".equals(configuredValue)) {
            return configuredValue.trim();
        }

        String envValue = env.getProperty(envName);
        if (StringUtils.hasText(envValue)) {
            log.info("Loaded {} from Spring Environment", envName);
            return envValue.trim();
        }

        String dotenvValue = resolveDotenvProperty(envName);
        if (StringUtils.hasText(dotenvValue)) {
            log.info("Loaded {} from dotenv file", envName);
            return dotenvValue.trim();
        }

        return configuredValue;
    }

    private String resolveDotenvProperty(String envName) {
        List<Path> candidatePaths = List.of(
                Paths.get("env"),
                Paths.get(".env"),
                Paths.get("../env"),
                Paths.get("../.env")
        );

        for (Path candidate : candidatePaths) {
            if (!Files.exists(candidate) || !Files.isRegularFile(candidate)) {
                continue;
            }

            try {
                Map<String, String> values = loadDotenv(candidate);
                if (values.containsKey(envName)) {
                    return values.get(envName);
                }
            } catch (IOException e) {
                log.warn("Failed to read dotenv file {}: {}", candidate, e.getMessage());
            }
        }

        return null;
    }

    private Map<String, String> loadDotenv(Path filePath) throws IOException {
        Map<String, String> values = new HashMap<>();
        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            int separatorIndex = trimmed.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }

            String key = trimmed.substring(0, separatorIndex).trim();
            String value = trimmed.substring(separatorIndex + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key, value);
        }
        return values;
    }
}
