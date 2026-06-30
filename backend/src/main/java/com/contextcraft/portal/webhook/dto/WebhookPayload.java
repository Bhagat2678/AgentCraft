package com.contextcraft.portal.webhook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Deserializes the top-level Meta WhatsApp Cloud API webhook POST body.
 *
 * Full payload shape (abbreviated):
 * {
 *   "object": "whatsapp_business_account",
 *   "entry": [{
 *     "id": "WABA_ID",
 *     "changes": [{
 *       "value": {
 *         "messaging_product": "whatsapp",
 *         "metadata": { "phone_number_id": "...", "display_phone_number": "..." },
 *         "contacts": [{ "profile": { "name": "..." }, "wa_id": "..." }],
 *         "messages": [{
 *           "from": "+15550000001",
 *           "id": "wamid.xxx",
 *           "timestamp": "1234567890",
 *           "type": "text",
 *           "text": { "body": "Hello" }
 *         }],
 *         "statuses": [{ "id": "...", "status": "delivered", ... }]
 *       },
 *       "field": "messages"
 *     }]
 *   }]
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookPayload {

    private String object;
    private List<Entry> entry;

    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }
    public List<Entry> getEntry() { return entry; }
    public void setEntry(List<Entry> entry) { this.entry = entry; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Entry {
        private String id;
        private List<Change> changes;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public List<Change> getChanges() { return changes; }
        public void setChanges(List<Change> changes) { this.changes = changes; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Change {
        private ChangeValue value;
        private String field;

        public ChangeValue getValue() { return value; }
        public void setValue(ChangeValue value) { this.value = value; }
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChangeValue {
        @JsonProperty("messaging_product")
        private String messagingProduct;

        private Metadata metadata;
        private List<Contact> contacts;
        private List<Message> messages;
        private List<Status> statuses;

        public String getMessagingProduct() { return messagingProduct; }
        public void setMessagingProduct(String messagingProduct) { this.messagingProduct = messagingProduct; }
        public Metadata getMetadata() { return metadata; }
        public void setMetadata(Metadata metadata) { this.metadata = metadata; }
        public List<Contact> getContacts() { return contacts; }
        public void setContacts(List<Contact> contacts) { this.contacts = contacts; }
        public List<Message> getMessages() { return messages; }
        public void setMessages(List<Message> messages) { this.messages = messages; }
        public List<Status> getStatuses() { return statuses; }
        public void setStatuses(List<Status> statuses) { this.statuses = statuses; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {
        @JsonProperty("phone_number_id")
        private String phoneNumberId;

        @JsonProperty("display_phone_number")
        private String displayPhoneNumber;

        public String getPhoneNumberId() { return phoneNumberId; }
        public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }
        public String getDisplayPhoneNumber() { return displayPhoneNumber; }
        public void setDisplayPhoneNumber(String displayPhoneNumber) { this.displayPhoneNumber = displayPhoneNumber; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Contact {
        private Profile profile;
        @JsonProperty("wa_id")
        private String waId;

        public Profile getProfile() { return profile; }
        public void setProfile(Profile profile) { this.profile = profile; }
        public String getWaId() { return waId; }
        public void setWaId(String waId) { this.waId = waId; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Profile {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private String from;
        private String id;
        private String timestamp;
        private String type; // text, image, video, audio, document, interactive, etc.
        private TextContent text;
        private MediaContent image;
        private MediaContent video;
        private MediaContent audio;
        private MediaContent document;
        private InteractiveContent interactive;

        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public TextContent getText() { return text; }
        public void setText(TextContent text) { this.text = text; }
        public MediaContent getImage() { return image; }
        public void setImage(MediaContent image) { this.image = image; }
        public MediaContent getVideo() { return video; }
        public void setVideo(MediaContent video) { this.video = video; }
        public MediaContent getAudio() { return audio; }
        public void setAudio(MediaContent audio) { this.audio = audio; }
        public MediaContent getDocument() { return document; }
        public void setDocument(MediaContent document) { this.document = document; }
        public InteractiveContent getInteractive() { return interactive; }
        public void setInteractive(InteractiveContent interactive) { this.interactive = interactive; }

        /** Returns the plain text body regardless of message type. */
        public String extractTextBody() {
            if ("text".equals(type) && text != null) return text.getBody();
            if ("interactive".equals(type) && interactive != null) return interactive.extractReply();
            return "";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TextContent {
        private String body;
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MediaContent {
        private String id;
        @JsonProperty("mime_type")
        private String mimeType;
        private String sha256;
        private String caption;
        private String filename;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
        public String getSha256() { return sha256; }
        public void setSha256(String sha256) { this.sha256 = sha256; }
        public String getCaption() { return caption; }
        public void setCaption(String caption) { this.caption = caption; }
        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InteractiveContent {
        private String type;
        @JsonProperty("button_reply")
        private ButtonReply buttonReply;
        @JsonProperty("list_reply")
        private ListReply listReply;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public ButtonReply getButtonReply() { return buttonReply; }
        public void setButtonReply(ButtonReply buttonReply) { this.buttonReply = buttonReply; }
        public ListReply getListReply() { return listReply; }
        public void setListReply(ListReply listReply) { this.listReply = listReply; }

        public String extractReply() {
            if ("button_reply".equals(type) && buttonReply != null) return buttonReply.getId();
            if ("list_reply".equals(type) && listReply != null) return listReply.getId();
            return "";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ButtonReply {
        private String id;
        private String title;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListReply {
        private String id;
        private String title;
        private String description;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Status {
        private String id;
        private String status;
        private String timestamp;
        @JsonProperty("recipient_id")
        private String recipientId;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        public String getRecipientId() { return recipientId; }
        public void setRecipientId(String recipientId) { this.recipientId = recipientId; }
    }
}
