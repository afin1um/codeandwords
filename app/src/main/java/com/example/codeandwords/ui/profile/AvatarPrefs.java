package com.example.codeandwords.ui.profile;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

public class AvatarPrefs {

    private static final String PREFS = "avatar_prefs";

    private static final String KEY_SKIN = "skin";
    private static final String KEY_HAIR_COLOR = "hair_color";
    private static final String KEY_CLOTHES = "clothes";
    private static final String KEY_BG = "bg";

    private static final String KEY_EYE_COLOR = "eye_color";
    private static final String KEY_GLASSES_COLOR = "glasses_color";
    private static final String KEY_HAT_COLOR = "hat_color";

    private static final String KEY_BODY = "body";
    private static final String KEY_HAIR_STYLE = "hair_style";
    private static final String KEY_GLASSES = "glasses";
    private static final String KEY_HAT = "hat";
    private static final String KEY_FACE_SHAPE = "face_shape";
    private static final String KEY_EARRINGS = "earrings";
    private static final String KEY_GENDER = "gender";

    private static final String KEY_AVATAR_CREATED = "avatar_created";
    private static final String KEY_NEEDS_AVATAR_SETUP = "needs_avatar_setup";

    private AvatarPrefs() {
    }

    public static AvatarConfig load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        AvatarConfig config = new AvatarConfig();

        config.skinColor = prefs.getInt(KEY_SKIN, Color.parseColor("#F2B39A"));
        config.hairColor = prefs.getInt(KEY_HAIR_COLOR, Color.parseColor("#6B412F"));
        config.clothesColor = prefs.getInt(KEY_CLOTHES, Color.parseColor("#424242"));
        config.backgroundColor = prefs.getInt(KEY_BG, Color.parseColor("#87CEFA"));

        config.eyeColor = prefs.getInt(KEY_EYE_COLOR, Color.parseColor("#4B4B4B"));
        config.glassesColor = prefs.getInt(KEY_GLASSES_COLOR, Color.parseColor("#2A63B8"));
        config.hatColor = prefs.getInt(KEY_HAT_COLOR, Color.parseColor("#9270CF"));

        config.bodyStyle = prefs.getInt(KEY_BODY, 0);
        config.hairStyle = prefs.getInt(KEY_HAIR_STYLE, 0);
        config.glassesStyle = prefs.getInt(KEY_GLASSES, 0);
        config.hatStyle = prefs.getInt(KEY_HAT, 0);
        config.faceShape = prefs.getInt(KEY_FACE_SHAPE, 0);
        config.earringsStyle = prefs.getInt(KEY_EARRINGS, 0);
        config.gender = prefs.getString(KEY_GENDER, "female");

        config.facialHairStyle = 0;

        return config;
    }

    public static void save(Context context, AvatarConfig config) {
        if (config == null) {
            config = new AvatarConfig();
        }

        config.facialHairStyle = 0;

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        prefs.edit()
                .putInt(KEY_SKIN, config.skinColor)
                .putInt(KEY_HAIR_COLOR, config.hairColor)
                .putInt(KEY_CLOTHES, config.clothesColor)
                .putInt(KEY_BG, config.backgroundColor)

                .putInt(KEY_EYE_COLOR, config.eyeColor)
                .putInt(KEY_GLASSES_COLOR, config.glassesColor)
                .putInt(KEY_HAT_COLOR, config.hatColor)

                .putInt(KEY_BODY, config.bodyStyle)
                .putInt(KEY_HAIR_STYLE, config.hairStyle)
                .putInt(KEY_GLASSES, config.glassesStyle)
                .putInt(KEY_HAT, config.hatStyle)
                .putInt(KEY_FACE_SHAPE, config.faceShape)
                .putInt(KEY_EARRINGS, config.earringsStyle)
                .putString(KEY_GENDER, normalizeGender(config.gender))

                .putBoolean(KEY_AVATAR_CREATED, true)
                .putBoolean(KEY_NEEDS_AVATAR_SETUP, false)
                .apply();
    }

    public static void saveDraft(Context context, AvatarConfig config) {
        if (config == null) {
            config = new AvatarConfig();
        }

        config.facialHairStyle = 0;

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        prefs.edit()
                .putInt(KEY_SKIN, config.skinColor)
                .putInt(KEY_HAIR_COLOR, config.hairColor)
                .putInt(KEY_CLOTHES, config.clothesColor)
                .putInt(KEY_BG, config.backgroundColor)

                .putInt(KEY_EYE_COLOR, config.eyeColor)
                .putInt(KEY_GLASSES_COLOR, config.glassesColor)
                .putInt(KEY_HAT_COLOR, config.hatColor)

                .putInt(KEY_BODY, config.bodyStyle)
                .putInt(KEY_HAIR_STYLE, config.hairStyle)
                .putInt(KEY_GLASSES, config.glassesStyle)
                .putInt(KEY_HAT, config.hatStyle)
                .putInt(KEY_FACE_SHAPE, config.faceShape)
                .putInt(KEY_EARRINGS, config.earringsStyle)
                .putString(KEY_GENDER, normalizeGender(config.gender))
                .apply();
    }

    public static void setAvatarCreated(Context context, boolean created) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_AVATAR_CREATED, created)
                .putBoolean(KEY_NEEDS_AVATAR_SETUP, !created)
                .apply();
    }

    public static boolean isAvatarCreated(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_AVATAR_CREATED, false);
    }

    public static void setNeedsAvatarSetup(Context context, boolean needsSetup) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_NEEDS_AVATAR_SETUP, needsSetup)
                .apply();
    }

    public static boolean needsAvatarSetup(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_NEEDS_AVATAR_SETUP, false);
    }

    private static String normalizeGender(String gender) {
        if ("male".equals(gender)) {
            return "male";
        }

        return "female";
    }
}