package com.example.codeandwords.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

@Entity(tableName = "users")
public class User implements Serializable {

    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    @Expose(serialize = false)
    private Integer id;

    @ColumnInfo(name = "username")
    @SerializedName("username")
    @Expose
    private String username;

    @ColumnInfo(name = "email")
    @SerializedName("email")
    @Expose
    private String email;

    @ColumnInfo(name = "password_hash")
    @SerializedName("password_hash")
    @Expose
    private String passwordHash;

    @ColumnInfo(name = "current_level")
    @SerializedName("current_level")
    @Expose
    private Integer currentLevel = 1;

    @ColumnInfo(name = "total_xp")
    @SerializedName("total_xp")
    @Expose
    private Integer totalXp = 0;

    @ColumnInfo(name = "role")
    @SerializedName("role")
    @Expose
    private String role = "user";

    @ColumnInfo(name = "created_at")
    @SerializedName("created_at")
    @Expose(serialize = false)
    private String createdAt;

    @ColumnInfo(name = "avatar_config")
    @SerializedName("avatar_config")
    @Expose
    private String avatarConfig;

    @ColumnInfo(name = "gender")
    @SerializedName("gender")
    @Expose
    private String gender = "female";

    public User() {
    }

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.currentLevel = 1;
        this.totalXp = 0;
        this.role = "user";
        this.gender = "female";
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Integer getCurrentLevel() {
        return currentLevel;
    }

    public void setCurrentLevel(Integer currentLevel) {
        this.currentLevel = currentLevel;
    }

    public Integer getTotalXp() {
        return totalXp;
    }

    public void setTotalXp(Integer totalXp) {
        this.totalXp = totalXp;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getAvatarConfig() {
        return avatarConfig;
    }

    public void setAvatarConfig(String avatarConfig) {
        this.avatarConfig = avatarConfig;
    }

    public String getGender() {
        return gender == null ? "female" : gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }
}