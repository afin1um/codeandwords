package com.example.codeandwords.ui.profile;

import android.graphics.Color;

import com.google.gson.Gson;

// Конфигурация визуального вида аватара и сериализация в JSON.
public class AvatarConfig {

    public int skinColor = Color.parseColor("#F2B39A");
    public int hairColor = Color.parseColor("#6B412F");
    public int eyebrowColor = Color.parseColor("#6B412F");
    public int clothesColor = Color.parseColor("#424242");
    public int backgroundColor = Color.parseColor("#87CEFA");

    public int eyeColor = Color.parseColor("#4B4B4B");
    public int glassesColor = Color.parseColor("#2A63B8");
    public int facialHairColor = Color.parseColor("#4B4B4B");
    public int hatColor = Color.parseColor("#9270CF");

    public String gender = "female";
    public int earringsStyle = 1;

    public int bodyStyle = 0;
    public int hairStyle = 0;
    public int glassesStyle = 0;
    public int facialHairStyle = 0;
    public int hatStyle = 0;
    public int faceShape = 0;

    // Брови всегда синхронизируются с цветом волос.
    public void syncEyebrowsWithHair() {
        eyebrowColor = hairColor;
    }

    public String toJson() {
        syncEyebrowsWithHair();
        return new Gson().toJson(this);
    }

    // Безопасное восстановление конфигурации из JSON с fallback на значения по умолчанию.
    public static AvatarConfig fromJson(String json) {
        try {
            if (json == null || json.trim().isEmpty() || json.equals("null")) {
                AvatarConfig config = new AvatarConfig();
                config.syncEyebrowsWithHair();
                return config;
            }

            AvatarConfig config = new Gson().fromJson(json, AvatarConfig.class);
            if (config == null) {
                config = new AvatarConfig();
            }

            config.syncEyebrowsWithHair();
            return config;
        } catch (Exception e) {
            AvatarConfig config = new AvatarConfig();
            config.syncEyebrowsWithHair();
            return config;
        }
    }

    // Создаёт независимую копию текущей конфигурации.
    public AvatarConfig copy() {
        syncEyebrowsWithHair();

        AvatarConfig c = new AvatarConfig();

        c.skinColor = this.skinColor;
        c.hairColor = this.hairColor;
        c.eyebrowColor = this.hairColor;
        c.clothesColor = this.clothesColor;
        c.backgroundColor = this.backgroundColor;

        c.eyeColor = this.eyeColor;
        c.glassesColor = this.glassesColor;
        c.facialHairColor = this.facialHairColor;
        c.hatColor = this.hatColor;

        c.bodyStyle = this.bodyStyle;
        c.hairStyle = this.hairStyle;
        c.glassesStyle = this.glassesStyle;
        c.facialHairStyle = 0;
        c.hatStyle = this.hatStyle;
        c.faceShape = this.faceShape;

        c.gender = this.gender;
        c.earringsStyle = this.earringsStyle;

        return c;
    }
}