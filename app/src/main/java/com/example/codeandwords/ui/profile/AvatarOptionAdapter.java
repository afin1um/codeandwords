package com.example.codeandwords.ui.profile;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;

import java.util.ArrayList;
import java.util.List;

public class AvatarOptionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnOptionClickListener {
        void onColorSelected(int color);
        void onStyleSelected(int index);
    }

    private static final int MODE_COLOR = 0;
    private static final int MODE_STYLE = 1;

    private static final int VIEW_TYPE_COLOR = 10;
    private static final int VIEW_TYPE_STYLE = 11;

    private int mode = MODE_COLOR;
    private final List<Integer> items = new ArrayList<>();

    private AvatarConfig baseConfig;
    private String category = "skin";
    private int selectedValue = 0;

    private final OnOptionClickListener listener;

    public AvatarOptionAdapter(OnOptionClickListener listener) {
        this.listener = listener;
    }

    public void setColorMode(List<Integer> colors, int selectedColor) {
        mode = MODE_COLOR;
        items.clear();

        if (colors != null) {
            items.addAll(colors);
        }

        selectedValue = selectedColor;
        notifyDataSetChanged();
    }

    public void setStyleMode(String category, List<Integer> styleIndexes, int selectedIndex, AvatarConfig baseConfig) {
        mode = MODE_STYLE;
        this.category = category != null ? category : "skin";
        this.baseConfig = baseConfig != null ? baseConfig.copy() : new AvatarConfig();

        items.clear();

        if (styleIndexes != null) {
            items.addAll(styleIndexes);
        }

        selectedValue = selectedIndex;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return mode == MODE_COLOR ? VIEW_TYPE_COLOR : VIEW_TYPE_STYLE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == VIEW_TYPE_COLOR) {
            View view = inflater.inflate(R.layout.item_avatar_color_option, parent, false);
            return new ColorViewHolder(view);
        }

        View view = inflater.inflate(R.layout.item_avatar_option, parent, false);
        return new StyleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int value = items.get(position);

        if (holder instanceof ColorViewHolder) {
            bindColor((ColorViewHolder) holder, value);
        } else if (holder instanceof StyleViewHolder) {
            bindStyle((StyleViewHolder) holder, value);
        }
    }

    private void bindColor(@NonNull ColorViewHolder holder, int value) {
        holder.root.setBackgroundResource(
                value == selectedValue
                        ? R.drawable.bg_avatar_color_option_selected
                        : R.drawable.bg_avatar_color_option
        );

        if (holder.colorSwatch.getBackground() instanceof GradientDrawable) {
            GradientDrawable bg = (GradientDrawable) holder.colorSwatch.getBackground().mutate();
            bg.setColor(value);
        }

        holder.itemView.setOnClickListener(v -> {
            selectedValue = value;
            notifyDataSetChanged();

            if (listener != null) {
                listener.onColorSelected(value);
            }
        });
    }

    private void bindStyle(@NonNull StyleViewHolder holder, int value) {
        holder.root.setBackgroundResource(
                value == selectedValue
                        ? R.drawable.bg_avatar_color_option_selected
                        : R.drawable.bg_avatar_option
        );

        AvatarConfig previewConfig = baseConfig != null ? baseConfig.copy() : new AvatarConfig();

        previewConfig.facialHairStyle = 0;

        switch (category) {
            case "body":
                previewConfig.bodyStyle = value;
                break;

            case "face":
                previewConfig.faceShape = value;
                break;

            case "hair":
                previewConfig.hairStyle = value;
                break;

            case "glasses":
                previewConfig.glassesStyle = value;
                break;

            case "hat":
                previewConfig.hatStyle = value;
                break;

            case "earrings":
            case "accessories":
                previewConfig.earringsStyle = value;
                break;

            case "facial":
                previewConfig.facialHairStyle = 0;
                break;
        }

        holder.avatarPreview.setAvatarConfig(previewConfig);

        holder.itemView.setOnClickListener(v -> {
            selectedValue = value;
            notifyDataSetChanged();

            if (listener != null) {
                listener.onStyleSelected(value);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ColorViewHolder extends RecyclerView.ViewHolder {
        View root;
        View colorSwatch;

        public ColorViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.colorOptionRoot);
            colorSwatch = itemView.findViewById(R.id.viewColorSquare);
        }
    }

    static class StyleViewHolder extends RecyclerView.ViewHolder {
        View root;
        AvatarPreviewView avatarPreview;

        public StyleViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.optionRoot);
            avatarPreview = itemView.findViewById(R.id.avatarOptionPreview);
        }
    }
}