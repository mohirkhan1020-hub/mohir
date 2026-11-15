package com.wevlo.amohir;

public class MessageModel {
    private String id;
    private String text;
    private String type; // text, image, voice
    private String mediaUrl;
    private String senderId;
    private String senderName;
    private long timestamp;
    private boolean seen;
    private String replyToSender;
    private String replyToText;
    private String reaction;
    private String voiceDuration;

    public MessageModel() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isSeen() { return seen; }
    public void setSeen(boolean seen) { this.seen = seen; }

    public String getReplyToSender() { return replyToSender; }
    public void setReplyToSender(String replyToSender) { this.replyToSender = replyToSender; }

    public String getReplyToText() { return replyToText; }
    public void setReplyToText(String replyToText) { this.replyToText = replyToText; }

    public String getReaction() { return reaction; }
    public void setReaction(String reaction) { this.reaction = reaction; }

    public String getVoiceDuration() { return voiceDuration; }
    public void setVoiceDuration(String voiceDuration) { this.voiceDuration = voiceDuration; }
}