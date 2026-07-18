package com.contextcraft.portal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds all app.telegram.* properties from application.properties.
 */
@Component
@ConfigurationProperties(prefix = "app.telegram")
public class TelegramProperties {

    private String botToken;
    private String webhookSecret;
    private String publicWebhookUrl;

    // Getters and Setters
    public String getBotToken() { return botToken; }
    public void setBotToken(String botToken) { this.botToken = botToken; }
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    public String getPublicWebhookUrl() { return publicWebhookUrl; }
    public void setPublicWebhookUrl(String publicWebhookUrl) { this.publicWebhookUrl = publicWebhookUrl; }
}
