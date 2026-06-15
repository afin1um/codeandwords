package com.example.codeandwords.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

// Модель пользователя: хранится локально и синхронизируется с Supabase
@Entity(
        tableName = "users",
        indices = {
                @Index(value = "total_xp", name = "idx_user_total_xp"),
                @Index(value = "email", unique = true, name = "idx_user_email")
        }
)
public class User implements Serializable {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    @SerializedName("id")
    private Integer id;

    @ColumnInfo(name = "username")
    @SerializedName("username")
    private String username;

    @ColumnInfo(name = "email")
    @SerializedName("email")
    private String email;

    @ColumnInfo(name = "password_hash")
    @SerializedName("password_hash")
    private String passwordHash;

    @ColumnInfo(name = "current_level")
    @SerializedName("current_level")
    private Integer currentLevel = 1;

    @ColumnInfo(name = "total_xp")
    @SerializedName("total_xp")
    private Integer totalXp = 0;

    @ColumnInfo(name = "role")
    @SerializedName("role")
    private String role = "user";

    @ColumnInfo(name = "created_at")
    @SerializedName("created_at")
    private String createdAt;

    @ColumnInfo(name = "avatar_config")
    @SerializedName("avatar_config")
    private String avatarConfig;

    @ColumnInfo(name = "gender")
    @SerializedName("gender")
    private String gender = "female";

    public User() {}

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.currentLevel = 1;
        this.totalXp = 0;
        this.role = "user";
        this.gender = "female";
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Integer getCurrentLevel() { return currentLevel; }
    public void setCurrentLevel(Integer currentLevel) { this.currentLevel = currentLevel; }
    public Integer getTotalXp() { return totalXp; }
    public void setTotalXp(Integer totalXp) { this.totalXp = totalXp; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getAvatarConfig() { return avatarConfig; }
    public void setAvatarConfig(String avatarConfig) { this.avatarConfig = avatarConfig; }
    public String getGender() { return gender == null ? "female" : gender; }
    public void setGender(String gender) { this.gender = gender; }
}