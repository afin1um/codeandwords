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
    @Expose(serialize = false) // Сервер сам генерирует ID, не отправляем его при регистрации
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
    @SerializedName("password_hash") // Проверь, чтобы было именно так!
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
    @Expose(serialize = false) // ВАЖНО: не отправляем, база сама поставит timestamp
    private String createdAt;

    // Пустой конструктор для Room и Retrofit
    public User() {}

    // Конструктор для регистрации
    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.currentLevel = 1;
        this.totalXp = 0;
        this.role = "user";
    }

    // Геттеры и сеттеры
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    // В файле User.java должно быть ТОЛЬКО так:
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Integer getCurrentLevel() { return currentLevel; }
    public void setCurrentLevel(Integer currentLevel) { this.currentLevel = currentLevel; }

    public Integer getTotalXp() { return totalXp; }
    public void setTotalXp(Integer totalXp) { this.totalXp = totalXp; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}