package com.wevlo.amohir;

public class StatusModel {
    private String id;
    private String uid;
    private String userName;
    private String text;
    private String imageBase64;
    private long timestamp;

    public StatusModel() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
