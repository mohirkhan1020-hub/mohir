package com.wevlo.amohir;

public class MessageModel {
    private String id, text, type, mediaUrl, senderId, senderName;
    private String replyToSender, replyToText, reaction, voiceDuration, fileName, latLng;
    private long timestamp, expireAt;
    private boolean seen, edited, deleted, forwarded;
    private boolean viewOnce, opened;
    private boolean starred;

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

    public long getExpireAt() { return expireAt; }
    public void setExpireAt(long expireAt) { this.expireAt = expireAt; }

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

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getLatLng() { return latLng; }
    public void setLatLng(String latLng) { this.latLng = latLng; }

    public boolean isEdited() { return edited; }
    public void setEdited(boolean edited) { this.edited = edited; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public boolean isForwarded() { return forwarded; }
    public void setForwarded(boolean forwarded) { this.forwarded = forwarded; }

    public boolean isViewOnce() { return viewOnce; }
    public void setViewOnce(boolean viewOnce) { this.viewOnce = viewOnce; }

    public boolean isOpened() { return opened; }
    public void setOpened(boolean opened) { this.opened = opened; }

    public boolean isStarred() { return starred; }
    public void setStarred(boolean starred) { this.starred = starred; }
}