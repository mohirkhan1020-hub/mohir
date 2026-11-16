package com.wevlo.amohir;

public class UserModel {
    private String uid, name, about, avatarBase64, typingTo, lastMessage;
    private boolean online, globalRoom;
    private long lastSeen, lastMsgTime;
    private int unreadCount;

    public UserModel() {}

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about; }

    public String getAvatarBase64() { return avatarBase64; }
    public void setAvatarBase64(String avatarBase64) { this.avatarBase64 = avatarBase64; }

    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }

    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }

    public String getTypingTo() { return typingTo; }
    public void setTypingTo(String typingTo) { this.typingTo = typingTo; }

    public boolean isGlobalRoom() { return globalRoom; }
    public void setGlobalRoom(boolean globalRoom) { this.globalRoom = globalRoom; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public long getLastMsgTime() { return lastMsgTime; }
    public void setLastMsgTime(long lastMsgTime) { this.lastMsgTime = lastMsgTime; }

    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
}