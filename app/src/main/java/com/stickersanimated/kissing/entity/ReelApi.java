package com.stickersanimated.kissing.entity;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/** One reel as the feed endpoints return it. */
public class ReelApi implements Serializable {

    public static final String TYPE_VIDEO = "video";

    @SerializedName("id")
    @Expose
    private String id;
    @SerializedName("type")
    @Expose
    private String type;
    @SerializedName("url")
    @Expose
    private String url;
    @SerializedName("thumb")
    @Expose
    private String thumb;
    @SerializedName("caption")
    @Expose
    private String caption;
    @SerializedName("width")
    @Expose
    private Integer width;
    @SerializedName("height")
    @Expose
    private Integer height;
    @SerializedName("duration")
    @Expose
    private Integer duration;
    @SerializedName("likes")
    @Expose
    private Integer likes;
    @SerializedName("views")
    @Expose
    private Integer views;
    @SerializedName("liked")
    @Expose
    private String liked;
    @SerializedName("created")
    @Expose
    private String created;
    @SerializedName("userid")
    @Expose
    private String userid;
    @SerializedName("user")
    @Expose
    private String user;
    @SerializedName("userimage")
    @Expose
    private String userimage;
    @SerializedName("trusted")
    @Expose
    private String trusted;
    @SerializedName("following")
    @Expose
    private String following;

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public boolean isVideo() {
        return TYPE_VIDEO.equals(type);
    }

    public String getUrl() {
        return url;
    }

    public String getThumb() {
        return thumb;
    }

    public String getCaption() {
        return caption == null ? "" : caption;
    }

    public Integer getWidth() {
        return width == null ? 0 : width;
    }

    public Integer getHeight() {
        return height == null ? 0 : height;
    }

    public Integer getDuration() {
        return duration == null ? 0 : duration;
    }

    public int getLikes() {
        return likes == null ? 0 : likes;
    }

    public void setLikes(int likes) {
        this.likes = Math.max(0, likes);
    }

    public int getViews() {
        return views == null ? 0 : views;
    }

    public boolean isLiked() {
        return "true".equals(liked);
    }

    public void setLiked(boolean value) {
        this.liked = value ? "true" : "false";
    }

    public String getCreated() {
        return created == null ? "" : created;
    }

    public String getUserid() {
        return userid == null ? "0" : userid;
    }

    public String getUser() {
        return user == null ? "" : user;
    }

    public String getUserimage() {
        return userimage;
    }

    public boolean isTrusted() {
        return "true".equals(trusted);
    }

    public boolean isFollowing() {
        return "true".equals(following);
    }

    public void setFollowing(boolean value) {
        this.following = value ? "true" : "false";
    }
}
