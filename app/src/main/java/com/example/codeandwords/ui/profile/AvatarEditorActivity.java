package com.example.codeandwords.ui.profile;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.ui.dashboard.MainActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Экран редактора аватара: выбор цветов и стилей по категориям.
public class AvatarEditorActivity extends AppCompatActivity {

    private View headerContainer;
    private View previewContainer;

    private AvatarPreviewView avatarPreview;

    private RecyclerView recyclerPrimary;
    private RecyclerView recyclerSecondary;

    private TextView tvPrimaryTitle;
    private TextView tvSecondaryTitle;
    private TextView btnDone;

    private ImageView ivSkin;
    private ImageView ivFace;
    private ImageView ivHair;
    private ImageView ivGlasses;
    private ImageView ivAccessories;
    private ImageView ivHat;
    private ImageView ivClothes;
    private ImageView ivBg;

    private View indicatorSkin;
    private View indicatorFace;
    private View indicatorHair;
    private View indicatorGlasses;
    private View indicatorAccessories;
    private View indicatorHat;
    private View indicatorClothes;
    private View indicatorBg;

    private AvatarOptionAdapter primaryAdapter;
    private AvatarOptionAdapter secondaryAdapter;

    private AvatarConfig currentConfig;
    private String currentCategory = "skin";

    private static final List<Integer> MALE_HAIR_STYLE_INDEXES = Arrays.asList(
            1, 3, 4, 5
    );

    private static final List<Integer> FEMALE_HAIR_STYLE_INDEXES = Arrays.asList(
            0,
            1, 2, 3, 4, 5,
            10, 11, 12, 13, 14, 15,
            16, 17, 18, 19, 20, 21, 22,
            23, 24, 25,
            26, 27, 28,
            29, 30, 31, 32, 33, 34
    );

    private static final List<Integer> ALL_HAIR_STYLE_INDEXES = Arrays.asList(
            0,
            1, 2, 3, 4, 5,
            10, 11, 12, 13, 14, 15,
            16, 17, 18, 19, 20, 21, 22,
            23, 24, 25,
            26, 27, 28,
            29, 30, 31, 32, 33, 34
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avatar_editor);

        currentConfig = AvatarPrefs.load(this);
        clampConfigValues();

        initViews();
        applyWindowInsets();
        setupAdapters();
        setupClicks();

        avatarPreview.setAvatarConfig(currentConfig.copy());
        selectCategory("skin");
    }

    // Приводит значения конфигурации к допустимым границам.
    private void clampConfigValues() {
        if (currentConfig == null) {
            currentConfig = new AvatarConfig();
        }

        currentConfig.gender = normalizeGender(currentConfig.gender);
        currentConfig.facialHairStyle = 0;

        if (currentConfig.bodyStyle < 0 || currentConfig.bodyStyle > 2) {
            currentConfig.bodyStyle = 0;
        }

        if (currentConfig.faceShape < 0 || currentConfig.faceShape > 11) {
            currentConfig.faceShape = 0;
        }

        if (currentConfig.glassesStyle < 0 || currentConfig.glassesStyle > 3) {
            currentConfig.glassesStyle = 0;
        }

        if (currentConfig.hatStyle < 0 || currentConfig.hatStyle > 5) {
            currentConfig.hatStyle = 0;
        }

        if (currentConfig.earringsStyle < 0 || currentConfig.earringsStyle > 2) {
            currentConfig.earringsStyle = 0;
        }

        List<Integer> allowedHair = getHairStyleIndexes();
        if (!ALL_HAIR_STYLE_INDEXES.contains(currentConfig.hairStyle)
                || !allowedHair.contains(currentConfig.hairStyle)) {
            currentConfig.hairStyle = "male".equals(currentConfig.gender) ? 1 : 0;
        }
    }

    private void initViews() {
        headerContainer = findViewById(R.id.headerContainerAvatarEditor);
        previewContainer = findViewById(R.id.previewContainerAvatarEditor);

        avatarPreview = findViewById(R.id.avatarEditorPreview);

        recyclerPrimary = findViewById(R.id.recyclerPrimaryOptions);
        recyclerSecondary = findViewById(R.id.recyclerSecondaryOptions);

        tvPrimaryTitle = findViewById(R.id.tvPrimaryTitle);
        tvSecondaryTitle = findViewById(R.id.tvSecondaryTitle);
        btnDone = findViewById(R.id.btnDoneAvatarEditor);

        ivSkin = findViewById(R.id.ivCategorySkin);
        ivFace = findViewById(R.id.ivCategoryFace);
        ivHair = findViewById(R.id.ivCategoryHair);
        ivGlasses = findViewById(R.id.ivCategoryGlasses);
        ivAccessories = findViewById(R.id.ivCategoryAccessories);
        ivHat = findViewById(R.id.ivCategoryHat);
        ivClothes = findViewById(R.id.ivCategoryClothes);
        ivBg = findViewById(R.id.ivCategoryBg);

        indicatorSkin = findViewById(R.id.indicatorSkin);
        indicatorFace = findViewById(R.id.indicatorFace);
        indicatorHair = findViewById(R.id.indicatorHair);
        indicatorGlasses = findViewById(R.id.indicatorGlasses);
        indicatorAccessories = findViewById(R.id.indicatorAccessories);
        indicatorHat = findViewById(R.id.indicatorHat);
        indicatorClothes = findViewById(R.id.indicatorClothes);
        indicatorBg = findViewById(R.id.indicatorBg);

        ImageButton btnClose = findViewById(R.id.btnCloseAvatarEditor);
        btnClose.setOnClickListener(v -> finish());

        btnDone.setOnClickListener(v -> saveAvatar());
    }

    // Сохраняет аватар локально и отправляет на сервер, затем открывает главный экран.
    private void saveAvatar() {
        currentConfig.gender = normalizeGender(currentConfig.gender);
        currentConfig.facialHairStyle = 0;

        AvatarPrefs.save(AvatarEditorActivity.this, currentConfig);
        AvatarPrefs.setAvatarCreated(AvatarEditorActivity.this, true);
        AvatarPrefs.setNeedsAvatarSetup(AvatarEditorActivity.this, false);

        Repository repository = Repository.getInstance(AvatarEditorActivity.this);

        // Прогрев данных тем в фоне.
        repository.getThemes(new Repository.DataCallback<java.util.List<com.example.codeandwords.model.Theme>>() {
            @Override
            public void onSuccess(java.util.List<com.example.codeandwords.model.Theme> data) {
            }
            @Override
            public void onError(String error) {
            }
        });

        repository.updateAvatarConfig(currentConfig, new Repository.DataCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                openMainActivity();
            }

            @Override
            public void onError(String error) {
                openMainActivity();
            }
        });
    }

    private void openMainActivity() {
        Intent intent = new Intent(AvatarEditorActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // Настраивает отступы с учётом системных баров.
    private void applyWindowInsets() {
        final int headerBaseHeight = dp(64);
        final int previewBaseHeight = dp(310);

        final int headerPaddingStart = dp(12);
        final int headerPaddingEnd = dp(12);
        final int headerPaddingBottom = dp(4);
        final int extraTopOffset = dp(4);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            if (headerContainer != null) {
                headerContainer.setPadding(
                        headerPaddingStart,
                        systemBars.top + extraTopOffset,
                        headerPaddingEnd,
                        headerPaddingBottom
                );
                headerContainer.getLayoutParams().height =
                        headerBaseHeight + systemBars.top + extraTopOffset;
                headerContainer.requestLayout();
            }

            if (previewContainer != null) {
                previewContainer.getLayoutParams().height = previewBaseHeight;
                previewContainer.setBackgroundColor(currentConfig.backgroundColor);
                previewContainer.requestLayout();
            }

            v.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // Настраивает два адаптера: для цветов и для стилей.
    private void setupAdapters() {
        primaryAdapter = new AvatarOptionAdapter(new AvatarOptionAdapter.OnOptionClickListener() {
            @Override
            public void onColorSelected(int color) {
                switch (currentCategory) {
                    case "skin":
                        currentConfig.skinColor = color;
                        break;
                    case "face":
                        currentConfig.eyeColor = color;
                        break;
                    case "hair":
                        currentConfig.hairColor = color;
                        break;
                    case "glasses":
                        currentConfig.glassesColor = color;
                        break;
                    case "hat":
                        currentConfig.hatColor = color;
                        break;
                    case "clothes":
                        currentConfig.clothesColor = color;
                        break;
                    case "bg":
                        currentConfig.backgroundColor = color;
                        if (previewContainer != null) {
                            previewContainer.setBackgroundColor(color);
                        }
                        break;
                }

                currentConfig.facialHairStyle = 0;
                avatarPreview.setAvatarConfig(currentConfig.copy());
                refreshSecondaryAdapter();
            }

            @Override
            public void onStyleSelected(int index) {
                avatarPreview.setAvatarConfig(currentConfig.copy());
            }
        });

        secondaryAdapter = new AvatarOptionAdapter(new AvatarOptionAdapter.OnOptionClickListener() {
            @Override
            public void onColorSelected(int color) {
            }

            @Override
            public void onStyleSelected(int index) {
                switch (currentCategory) {
                    case "skin":
                        currentConfig.bodyStyle = index;
                        break;
                    case "face":
                        currentConfig.faceShape = index;
                        break;
                    case "hair":
                        currentConfig.hairStyle = index;
                        break;
                    case "glasses":
                        currentConfig.glassesStyle = index;
                        break;
                    case "hat":
                        currentConfig.hatStyle = index;
                        break;
                    case "accessories":
                        currentConfig.earringsStyle = index;
                        break;
                }

                currentConfig.facialHairStyle = 0;
                avatarPreview.setAvatarConfig(currentConfig.copy());
                refreshSecondaryAdapter();
            }
        });

        recyclerPrimary.setAdapter(primaryAdapter);
        recyclerSecondary.setAdapter(secondaryAdapter);
    }

    // Обновляет вторичный список стилей для текущей категории.
    private void refreshSecondaryAdapter() {
        switch (currentCategory) {
            case "skin":
                secondaryAdapter.setStyleMode(
                        "body",
                        createIndexes(3),
                        currentConfig.bodyStyle,
                        currentConfig
                );
                break;

            case "face":
                secondaryAdapter.setStyleMode(
                        "face",
                        createIndexes(12),
                        currentConfig.faceShape,
                        currentConfig
                );
                break;

            case "hair":
                secondaryAdapter.setStyleMode(
                        "hair",
                        new ArrayList<>(getHairStyleIndexes()),
                        currentConfig.hairStyle,
                        currentConfig
                );
                break;

            case "glasses":
                secondaryAdapter.setStyleMode(
                        "glasses",
                        createIndexes(4),
                        currentConfig.glassesStyle,
                        currentConfig
                );
                break;

            case "hat":
                secondaryAdapter.setStyleMode(
                        "hat",
                        createIndexes(6),
                        currentConfig.hatStyle,
                        currentConfig
                );
                break;

            case "accessories":
                secondaryAdapter.setStyleMode(
                        "earrings",
                        createIndexes(3),
                        currentConfig.earringsStyle,
                        currentConfig
                );
                break;
        }
    }

    private void setupClicks() {
        findViewById(R.id.tabSkin).setOnClickListener(v -> selectCategory("skin"));
        findViewById(R.id.tabFace).setOnClickListener(v -> selectCategory("face"));
        findViewById(R.id.tabHair).setOnClickListener(v -> selectCategory("hair"));
        findViewById(R.id.tabGlasses).setOnClickListener(v -> selectCategory("glasses"));
        findViewById(R.id.tabAccessories).setOnClickListener(v -> selectCategory("accessories"));
        findViewById(R.id.tabHat).setOnClickListener(v -> selectCategory("hat"));
        findViewById(R.id.tabClothes).setOnClickListener(v -> selectCategory("clothes"));
        findViewById(R.id.tabBg).setOnClickListener(v -> selectCategory("bg"));
    }

    private void setupHorizontalColorList(List<Integer> colors, int selectedColor) {
        recyclerPrimary.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        primaryAdapter.setColorMode(colors, selectedColor);
    }

    private void setupGridStyleList(String category, List<Integer> indexes, int selectedIndex, AvatarConfig config) {
        recyclerSecondary.setLayoutManager(new GridLayoutManager(this, 3));
        secondaryAdapter.setStyleMode(category, indexes, selectedIndex, config);
    }

    // Переключает текущую категорию настроек аватара.
    private void selectCategory(String category) {
        currentCategory = category;
        resetCategoryState();

        recyclerPrimary.setVisibility(View.VISIBLE);
        tvPrimaryTitle.setVisibility(View.VISIBLE);

        recyclerSecondary.setVisibility(View.GONE);
        tvSecondaryTitle.setVisibility(View.GONE);

        switch (category) {
            case "skin":
                setSelected(ivSkin, indicatorSkin);

                tvPrimaryTitle.setText("Цвет кожи");
                setupHorizontalColorList(getSkinColors(), currentConfig.skinColor);

                tvSecondaryTitle.setVisibility(View.VISIBLE);
                recyclerSecondary.setVisibility(View.VISIBLE);
                tvSecondaryTitle.setText("Тело");
                setupGridStyleList("body", createIndexes(3), currentConfig.bodyStyle, currentConfig);
                break;

            case "face":
                setSelected(ivFace, indicatorFace);

                tvPrimaryTitle.setText("Цвет глаз");
                setupHorizontalColorList(getEyeColors(), currentConfig.eyeColor);

                tvSecondaryTitle.setVisibility(View.VISIBLE);
                recyclerSecondary.setVisibility(View.VISIBLE);
                tvSecondaryTitle.setText("Выражение лица");
                setupGridStyleList("face", createIndexes(12), currentConfig.faceShape, currentConfig);
                break;

            case "hair":
                setSelected(ivHair, indicatorHair);

                tvPrimaryTitle.setText("Цвет волос");
                setupHorizontalColorList(getHairColors(), currentConfig.hairColor);

                tvSecondaryTitle.setVisibility(View.VISIBLE);
                recyclerSecondary.setVisibility(View.VISIBLE);
                tvSecondaryTitle.setText("Причёска");
                setupGridStyleList(
                        "hair",
                        new ArrayList<>(getHairStyleIndexes()),
                        currentConfig.hairStyle,
                        currentConfig
                );
                break;

            case "glasses":
                setSelected(ivGlasses, indicatorGlasses);

                tvPrimaryTitle.setText("Цвет очков");
                setupHorizontalColorList(getGlassesColors(), currentConfig.glassesColor);

                tvSecondaryTitle.setVisibility(View.VISIBLE);
                recyclerSecondary.setVisibility(View.VISIBLE);
                tvSecondaryTitle.setText("Очки");
                setupGridStyleList("glasses", createIndexes(4), currentConfig.glassesStyle, currentConfig);
                break;

            case "accessories":
                setSelected(ivAccessories, indicatorAccessories);

                tvPrimaryTitle.setVisibility(View.GONE);
                recyclerPrimary.setVisibility(View.GONE);

                tvSecondaryTitle.setVisibility(View.VISIBLE);
                recyclerSecondary.setVisibility(View.VISIBLE);
                tvSecondaryTitle.setText("Аксессуары");
                setupGridStyleList("earrings", createIndexes(3), currentConfig.earringsStyle, currentConfig);
                break;

            case "hat":
                setSelected(ivHat, indicatorHat);

                tvPrimaryTitle.setText("Цвет головного убора");
                setupHorizontalColorList(getHatColors(), currentConfig.hatColor);

                tvSecondaryTitle.setVisibility(View.VISIBLE);
                recyclerSecondary.setVisibility(View.VISIBLE);
                tvSecondaryTitle.setText("Головной убор");
                setupGridStyleList("hat", createIndexes(6), currentConfig.hatStyle, currentConfig);
                break;

            case "clothes":
                setSelected(ivClothes, indicatorClothes);

                tvPrimaryTitle.setText("Цвет одежды");
                setupHorizontalColorList(getClothesColors(), currentConfig.clothesColor);
                break;

            case "bg":
                setSelected(ivBg, indicatorBg);

                tvPrimaryTitle.setText("Цвет фона");
                setupHorizontalColorList(getBackgroundColors(), currentConfig.backgroundColor);
                break;
        }
    }

    // Сбрасывает выделение всех вкладок категорий.
    private void resetCategoryState() {
        List<ImageView> icons = Arrays.asList(
                ivSkin,
                ivFace,
                ivHair,
                ivGlasses,
                ivAccessories,
                ivHat,
                ivClothes,
                ivBg
        );

        for (ImageView imageView : icons) {
            if (imageView != null) {
                imageView.setColorFilter(ContextCompat.getColor(this, R.color.avatar_tab_text));
                imageView.setAlpha(0.65f);
            }
        }

        List<View> indicators = Arrays.asList(
                indicatorSkin,
                indicatorFace,
                indicatorHair,
                indicatorGlasses,
                indicatorAccessories,
                indicatorHat,
                indicatorClothes,
                indicatorBg
        );

        for (View indicator : indicators) {
            if (indicator != null) {
                indicator.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void setSelected(ImageView icon, View indicator) {
        if (icon != null) {
            icon.setColorFilter(ContextCompat.getColor(this, R.color.avatar_tab_selected_bg));
            icon.setAlpha(1f);
        }

        if (indicator != null) {
            indicator.setVisibility(View.VISIBLE);
        }
    }

    private List<Integer> getHairStyleIndexes() {
        return "male".equals(normalizeGender(currentConfig.gender))
                ? MALE_HAIR_STYLE_INDEXES
                : FEMALE_HAIR_STYLE_INDEXES;
    }

    private String normalizeGender(String gender) {
        return "male".equals(gender) ? "male" : "female";
    }

    private List<Integer> createIndexes(int count) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(i);
        }
        return list;
    }

    private List<Integer> getSkinColors() {
        return Arrays.asList(
                Color.parseColor("#8B4F45"),
                Color.parseColor("#9A5D4E"),
                Color.parseColor("#A35E2C"),
                Color.parseColor("#A3604B"),
                Color.parseColor("#AE6D3A"),
                Color.parseColor("#B27652"),
                Color.parseColor("#BD7A53"),
                Color.parseColor("#C57D59"),
                Color.parseColor("#CB8463"),
                Color.parseColor("#D88D6B"),
                Color.parseColor("#E09B63"),
                Color.parseColor("#E99D79"),
                Color.parseColor("#F2B39A"),
                Color.parseColor("#E9B7A9"),
                Color.parseColor("#EDC095"),
                Color.parseColor("#E5CDBF")
        );
    }

    private List<Integer> getEyeColors() {
        return Arrays.asList(
                Color.parseColor("#4B4B4B"),
                Color.parseColor("#B95500"),
                Color.parseColor("#A88500"),
                Color.parseColor("#66A300"),
                Color.parseColor("#1A929D"),
                Color.parseColor("#1982D1"),
                Color.parseColor("#7E93B4"),
                Color.parseColor("#8F5AE8"),
                Color.parseColor("#C726CD"),
                Color.parseColor("#E843B1"),
                Color.parseColor("#F53333"),
                Color.parseColor("#F28C00"),
                Color.parseColor("#F2B800")
        );
    }

    private List<Integer> getHairColors() {
        return Arrays.asList(
                Color.parseColor("#4B4B4B"),
                Color.parseColor("#6B412F"),
                Color.parseColor("#7B2E2E"),
                Color.parseColor("#8E4A24"),
                Color.parseColor("#B04848"),
                Color.parseColor("#A8928D"),
                Color.parseColor("#C77422"),
                Color.parseColor("#CF6B4C"),
                Color.parseColor("#EAAA59"),
                Color.parseColor("#EFD1A5"),
                Color.parseColor("#D9D4CE"),
                Color.parseColor("#ECEFF1"),
                Color.parseColor("#E289C2"),
                Color.parseColor("#915BF3"),
                Color.parseColor("#4B84D3"),
                Color.parseColor("#2F9C78")
        );
    }

    private List<Integer> getGlassesColors() {
        return Arrays.asList(
                Color.parseColor("#9270CF"),
                Color.parseColor("#2A63B8"),
                Color.parseColor("#6A9226"),
                Color.parseColor("#F39200"),
                Color.parseColor("#FF4A4A"),
                Color.parseColor("#E38BC9"),
                Color.parseColor("#5E5E5E"),
                Color.parseColor("#FFFFFF")
        );
    }

    private List<Integer> getHatColors() {
        return Arrays.asList(
                Color.parseColor("#9270CF"),
                Color.parseColor("#178BC4"),
                Color.parseColor("#63B800"),
                Color.parseColor("#F4C400"),
                Color.parseColor("#FF9F00"),
                Color.parseColor("#D40047"),
                Color.parseColor("#5E5E5E"),
                Color.parseColor("#FFFFFF")
        );
    }

    private List<Integer> getClothesColors() {
        return Arrays.asList(
                Color.parseColor("#B185C8"),
                Color.parseColor("#4DA0CF"),
                Color.parseColor("#80B93F"),
                Color.parseColor("#E9C83E"),
                Color.parseColor("#EDA13E"),
                Color.parseColor("#C33E6D"),
                Color.parseColor("#E2B7CF"),
                Color.parseColor("#E2E5E8"),
                Color.parseColor("#4A4A4A"),
                Color.parseColor("#2F2F2F"),
                Color.parseColor("#7A3E2A"),
                Color.parseColor("#1D7A70")
        );
    }

    private List<Integer> getBackgroundColors() {
        return Arrays.asList(
                Color.parseColor("#E4E4E4"),
                Color.parseColor("#BCBCBC"),
                Color.parseColor("#5C5C5C"),
                Color.parseColor("#D9C8E7"),
                Color.parseColor("#C19BE2"),
                Color.parseColor("#9270CF"),
                Color.parseColor("#ADD4EC"),
                Color.parseColor("#87CEFA"),
                Color.parseColor("#336AB7"),
                Color.parseColor("#AFDDC7"),
                Color.parseColor("#5ED8AE"),
                Color.parseColor("#4BB38B"),
                Color.parseColor("#BEE3A0"),
                Color.parseColor("#A9DD7A"),
                Color.parseColor("#84BC4E"),
                Color.parseColor("#E9DF9C"),
                Color.parseColor("#F0C575"),
                Color.parseColor("#DFA354"),
                Color.parseColor("#E7CBCD"),
                Color.parseColor("#E9A4A7"),
                Color.parseColor("#E56C74"),
                Color.parseColor("#E8C2D7"),
                Color.parseColor("#E7A7D4"),
                Color.parseColor("#D467B5")
        );
    }
}