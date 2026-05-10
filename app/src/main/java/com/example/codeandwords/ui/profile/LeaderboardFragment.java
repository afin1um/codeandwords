package com.example.codeandwords.ui.profile;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.codeandwords.R;
import com.example.codeandwords.data.Repository;
import com.example.codeandwords.model.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LeaderboardFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private ProgressBar progressLeague;

    private LinearLayout leagueFilterContainer;

    private TextView tvLeagueTitle;
    private TextView tvLeagueSubtitle;
    private TextView tvLeagueIcon;
    private TextView tvSelectedLeague;
    private TextView tvLeagueUsersCount;
    private TextView tvMyLeagueInfo;

    private FrameLayout topFirstAvatarContainer;
    private AvatarPreviewView topFirstAvatarView;
    private TextView topFirstAvatarFallback;
    private TextView tvTopFirstName;
    private TextView tvTopFirstXp;

    private FrameLayout topSecondAvatarContainer;
    private AvatarPreviewView topSecondAvatarView;
    private TextView topSecondAvatarFallback;
    private TextView tvTopSecondName;
    private TextView tvTopSecondXp;

    private FrameLayout topThirdAvatarContainer;
    private AvatarPreviewView topThirdAvatarView;
    private TextView topThirdAvatarFallback;
    private TextView tvTopThirdName;
    private TextView tvTopThirdXp;

    private LeaderboardAdapter adapter;
    private Repository repository;

    private final List<User> allUsers = new ArrayList<>();
    private final List<League> leagues = new ArrayList<>();

    private League selectedLeague;
    private long myUserId = -1;
    private User currentUser;

    // ✅ Адаптивные цвета (день/ночь)
    private int colorChipUnselectedBg;
    private int colorChipUnselectedText;
    private int colorChipSelectedText;

    private int colorCardBg;
    private int colorCardMeBg;
    private int colorCardMeStroke;

    private int colorAvatarPlaceholderFill;

    private int colorRankDefault;
    private int colorRankMe;
    private int colorXpPlaceholder;

    private int colorMyMarker;

    private int colorUsername;       // ✅ Цвет имени
    private int colorUsernameMe;     // ✅ Цвет имени для "моя"
    private int colorLevelText;      // ✅ Цвет описания уровня
    private int colorTextPrimary;    // ✅ Основной текст (для топ-3)

    public LeaderboardFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_leaderboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        loadThemeColors();
        initLeagues();
        initViews(view);
        setupRecycler();

        repository = new Repository(requireContext());

        loadData();
    }

    private void loadThemeColors() {
        colorChipUnselectedBg   = ContextCompat.getColor(requireContext(), R.color.leaderboard_chip_unselected_bg);
        colorChipUnselectedText = ContextCompat.getColor(requireContext(), R.color.leaderboard_chip_unselected_text);
        colorChipSelectedText   = ContextCompat.getColor(requireContext(), R.color.leaderboard_chip_selected_text);

        colorCardBg       = ContextCompat.getColor(requireContext(), R.color.leaderboard_card_bg);
        colorCardMeBg     = ContextCompat.getColor(requireContext(), R.color.leaderboard_card_me_bg);
        colorCardMeStroke = ContextCompat.getColor(requireContext(), R.color.leaderboard_card_me_stroke);

        colorAvatarPlaceholderFill = ContextCompat.getColor(requireContext(), R.color.leaderboard_avatar_placeholder_fill);

        colorRankDefault   = ContextCompat.getColor(requireContext(), R.color.leaderboard_rank_default);
        colorRankMe        = ContextCompat.getColor(requireContext(), R.color.leaderboard_rank_me);
        colorXpPlaceholder = ContextCompat.getColor(requireContext(), R.color.leaderboard_xp_placeholder);

        colorMyMarker = ContextCompat.getColor(requireContext(), R.color.leaderboard_my_marker);

        // ✅ Имена и уровни
        colorUsername     = ContextCompat.getColor(requireContext(), R.color.leaderboard_username);
        colorUsernameMe   = ContextCompat.getColor(requireContext(), R.color.leaderboard_username_me);
        colorLevelText    = ContextCompat.getColor(requireContext(), R.color.leaderboard_level_text);
        colorTextPrimary  = ContextCompat.getColor(requireContext(), R.color.leaderboard_text_primary);
    }

    private void initLeagues() {
        leagues.clear();

        leagues.add(new League("Все лиги", "Все участники рейтинга", "🌍",
                0, Integer.MAX_VALUE, Color.parseColor("#53B8E8"), false));

        leagues.add(new League("Бронзовая лига", "Начало пути", "🥉",
                0, 249, Color.parseColor("#CD7F32"), true));

        leagues.add(new League("Серебряная лига", "Стабильный прогресс", "🥈",
                250, 599, Color.parseColor("#B0BEC5"), true));

        leagues.add(new League("Золотая лига", "Путь к сильным игрокам", "🥇",
                600, 1199, Color.parseColor("#FFC107"), true));

        leagues.add(new League("Изумрудная лига", "Сильные и стабильные", "💚",
                1200, 2499, Color.parseColor("#58CC02"), true));

        leagues.add(new League("Алмазная лига", "Элита Code & Words", "💎",
                2500, Integer.MAX_VALUE, Color.parseColor("#61D9FF"), true));

        selectedLeague = leagues.get(0);
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.rvLeaderboard);
        progressBar = view.findViewById(R.id.pbLeaderboard);
        progressLeague = view.findViewById(R.id.progressLeague);

        leagueFilterContainer = view.findViewById(R.id.leagueFilterContainer);

        tvLeagueTitle = view.findViewById(R.id.tvLeagueTitle);
        tvLeagueSubtitle = view.findViewById(R.id.tvLeagueSubtitle);
        tvLeagueIcon = view.findViewById(R.id.tvLeagueIcon);
        tvSelectedLeague = view.findViewById(R.id.tvSelectedLeague);
        tvLeagueUsersCount = view.findViewById(R.id.tvLeagueUsersCount);
        tvMyLeagueInfo = view.findViewById(R.id.tvMyLeagueInfo);

        topFirstAvatarContainer = view.findViewById(R.id.topFirstAvatarContainer);
        topFirstAvatarView = view.findViewById(R.id.topFirstAvatarView);
        topFirstAvatarFallback = view.findViewById(R.id.topFirstAvatarFallback);
        tvTopFirstName = view.findViewById(R.id.tvTopFirstName);
        tvTopFirstXp = view.findViewById(R.id.tvTopFirstXp);

        topSecondAvatarContainer = view.findViewById(R.id.topSecondAvatarContainer);
        topSecondAvatarView = view.findViewById(R.id.topSecondAvatarView);
        topSecondAvatarFallback = view.findViewById(R.id.topSecondAvatarFallback);
        tvTopSecondName = view.findViewById(R.id.tvTopSecondName);
        tvTopSecondXp = view.findViewById(R.id.tvTopSecondXp);

        topThirdAvatarContainer = view.findViewById(R.id.topThirdAvatarContainer);
        topThirdAvatarView = view.findViewById(R.id.topThirdAvatarView);
        topThirdAvatarFallback = view.findViewById(R.id.topThirdAvatarFallback);
        tvTopThirdName = view.findViewById(R.id.tvTopThirdName);
        tvTopThirdXp = view.findViewById(R.id.tvTopThirdXp);
    }

    private void setupRecycler() {
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setNestedScrollingEnabled(false);

        adapter = new LeaderboardAdapter();
        recyclerView.setAdapter(adapter);
    }

    private void loadData() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        repository.getCurrentUser(new Repository.DataCallback<User>() {
            @Override
            public void onSuccess(User user) {
                currentUser = user;
                if (user != null && user.getId() != null) myUserId = user.getId();
                fetchLeaderboard();
            }

            @Override
            public void onError(String error) {
                fetchLeaderboard();
            }
        });
    }

    private void fetchLeaderboard() {
        repository.getLeaderboard(new Repository.DataCallback<List<User>>() {
            @Override
            public void onSuccess(List<User> users) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);

                allUsers.clear();
                allUsers.addAll(prepareUsers(users));

                User me = findCurrentUser(allUsers);
                League myLeague = getLeagueByXp(getXp(me));

                selectedLeague = myLeague != null ? myLeague : leagues.get(0);

                renderLeagueFilters();
                applyLeagueFilter();
            }

            @Override
            public void onError(String error) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                Toast.makeText(requireContext(),
                        "Ошибка загрузки рейтинга: " + error,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private List<User> prepareUsers(List<User> users) {
        List<User> result = new ArrayList<>();
        if (users != null) {
            for (User user : users) {
                if (user != null) result.add(user);
            }
        }
        Collections.sort(result, (u1, u2) -> Integer.compare(getXp(u2), getXp(u1)));
        return result;
    }

    private void renderLeagueFilters() {
        if (leagueFilterContainer == null) return;

        leagueFilterContainer.removeAllViews();

        for (League league : leagues) {
            MaterialButton chip = new MaterialButton(requireContext());

            int count = getUsersByLeague(league).size();
            boolean selected = league.title.equals(selectedLeague.title);

            chip.setAllCaps(false);
            chip.setText(league.icon + " " + league.title + " (" + count + ")");
            chip.setTextSize(13f);
            chip.setMinHeight(dp(42));
            chip.setMinimumHeight(dp(42));
            chip.setCornerRadius(dp(18));
            chip.setInsetTop(0);
            chip.setInsetBottom(0);

            if (selected) {
                chip.setTextColor(colorChipSelectedText);
                chip.setBackgroundTintList(ColorStateList.valueOf(league.color));
            } else {
                chip.setTextColor(colorChipUnselectedText);
                chip.setBackgroundTintList(ColorStateList.valueOf(colorChipUnselectedBg));
            }

            chip.setStrokeColor(ColorStateList.valueOf(league.color));
            chip.setStrokeWidth(dp(1));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(44));
            params.setMargins(0, 0, dp(10), 0);
            chip.setLayoutParams(params);

            chip.setOnClickListener(v -> {
                selectedLeague = league;
                renderLeagueFilters();
                applyLeagueFilter();
            });

            leagueFilterContainer.addView(chip);
        }
    }

    private void applyLeagueFilter() {
        List<User> filteredUsers = getUsersByLeague(selectedLeague);

        tvLeagueTitle.setText(selectedLeague.title);
        tvLeagueSubtitle.setText(selectedLeague.subtitle);
        tvLeagueIcon.setText(selectedLeague.icon);
        tvSelectedLeague.setText(selectedLeague.title);
        tvLeagueUsersCount.setText(filteredUsers.size() + " " + getUserCountLabel(filteredUsers.size()));

        User me = findCurrentUser(allUsers);
        League myLeague = getLeagueByXp(getXp(me));

        if (myLeague != null) {
            tvMyLeagueInfo.setText("Ваша лига: " + myLeague.icon + " " + myLeague.title + " • " + getXp(me) + " XP");
            updateProgressForMyLeague(me, myLeague);
        } else {
            tvMyLeagueInfo.setText("Ваша лига: —");
            progressLeague.setProgress(0);
        }

        updateTopThree(filteredUsers);
        adapter.setData(filteredUsers, myUserId);
    }

    private List<User> getUsersByLeague(League league) {
        List<User> result = new ArrayList<>();
        if (league == null || !league.isRealLeague) {
            result.addAll(allUsers);
            return result;
        }
        for (User user : allUsers) {
            int xp = getXp(user);
            if (xp >= league.minXp && xp <= league.maxXp) result.add(user);
        }
        return result;
    }

    private void updateProgressForMyLeague(User me, League myLeague) {
        if (progressLeague == null) return;
        if (me == null || myLeague == null) {
            progressLeague.setProgress(0);
            return;
        }
        if (myLeague.maxXp == Integer.MAX_VALUE) {
            progressLeague.setProgress(100);
            return;
        }
        int xp = getXp(me);
        int range = myLeague.maxXp - myLeague.minXp + 1;
        int progress = Math.max(0, Math.min(100, ((xp - myLeague.minXp) * 100) / range));
        progressLeague.setProgress(progress);
    }

    private void updateTopThree(List<User> users) {
        bindTopUser(1, users.size() > 0 ? users.get(0) : null,
                topFirstAvatarContainer, topFirstAvatarView, topFirstAvatarFallback,
                tvTopFirstName, tvTopFirstXp);

        bindTopUser(2, users.size() > 1 ? users.get(1) : null,
                topSecondAvatarContainer, topSecondAvatarView, topSecondAvatarFallback,
                tvTopSecondName, tvTopSecondXp);

        bindTopUser(3, users.size() > 2 ? users.get(2) : null,
                topThirdAvatarContainer, topThirdAvatarView, topThirdAvatarFallback,
                tvTopThirdName, tvTopThirdXp);
    }

    private void bindTopUser(int place,
                             User user,
                             FrameLayout avatarContainer,
                             AvatarPreviewView avatarView,
                             TextView fallback,
                             TextView name,
                             TextView xpView) {
        if (user == null) {
            League placeholderLeague = selectedLeague != null ? selectedLeague : leagues.get(0);

            applyAvatarCircleBackground(avatarContainer,
                    colorAvatarPlaceholderFill, placeholderLeague.color, 2);

            avatarView.setVisibility(View.GONE);

            fallback.setVisibility(View.VISIBLE);
            fallback.setText(String.valueOf(place));
            fallback.setBackgroundTintList(ColorStateList.valueOf(colorAvatarPlaceholderFill));

            name.setText("—");
            // ✅ Цвет имени для пустого слота
            name.setTextColor(colorTextPrimary);
            xpView.setText("0 XP");
            xpView.setTextColor(colorXpPlaceholder);
            return;
        }

        League league = getLeagueByXp(getXp(user));
        if (league == null) league = leagues.get(0);

        bindAvatarToViews(user, league, avatarContainer, avatarView, fallback);

        name.setText(getSafeUsername(user));
        // ✅ Имя в топ-3 — основной цвет (черный/белый)
        name.setTextColor(colorTextPrimary);
        xpView.setText(getXp(user) + " XP");
        xpView.setTextColor(league.color);
    }

    private void bindAvatarToViews(User user,
                                   League league,
                                   FrameLayout avatarContainer,
                                   AvatarPreviewView avatarView,
                                   TextView fallback) {
        if (league == null) league = leagues.get(0);

        String avatarJson = user != null ? user.getAvatarConfig() : null;

        if (avatarJson != null
                && !avatarJson.trim().isEmpty()
                && !"null".equalsIgnoreCase(avatarJson.trim())) {
            try {
                AvatarConfig config = AvatarConfig.fromJson(avatarJson);

                int avatarBackgroundColor = config.backgroundColor;
                if (!isValidAvatarBackgroundColor(avatarBackgroundColor)) {
                    avatarBackgroundColor = Color.parseColor("#B58AE0");
                }

                applyAvatarCircleBackground(avatarContainer,
                        avatarBackgroundColor, league.color, 3);

                avatarView.setShowBackground(false);
                avatarView.setAvatarConfig(config);
                avatarView.setVisibility(View.VISIBLE);
                avatarView.setScaleX(0.92f);
                avatarView.setScaleY(0.92f);

                fallback.setVisibility(View.GONE);
                fallback.setBackgroundTintList(null);
                return;
            } catch (Exception ignored) {
            }
        }

        applyAvatarCircleBackground(avatarContainer, league.color, league.color, 2);

        avatarView.setVisibility(View.GONE);
        fallback.setVisibility(View.VISIBLE);
        fallback.setText(getInitial(user));
        fallback.setBackgroundTintList(ColorStateList.valueOf(league.color));
    }

    private User findCurrentUser(List<User> users) {
        if (users == null || myUserId <= 0) return currentUser;
        for (User user : users) {
            if (user != null && user.getId() != null && user.getId() == myUserId) return user;
        }
        return currentUser;
    }

    private int getXp(User user) {
        if (user == null || user.getTotalXp() == null) return 0;
        return user.getTotalXp();
    }

    private int getLevel(User user) {
        if (user == null) return 1;
        if (user.getCurrentLevel() != null && user.getCurrentLevel() > 0) return user.getCurrentLevel();
        return (getXp(user) / 100) + 1;
    }

    private String getSafeUsername(User user) {
        if (user == null || user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return "Пользователь";
        }
        return user.getUsername().trim();
    }

    private String getInitial(User user) {
        String username = getSafeUsername(user);
        if (username.isEmpty()) return "?";
        return username.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    private League getLeagueByXp(int xp) {
        for (League league : leagues) {
            if (!league.isRealLeague) continue;
            if (xp >= league.minXp && xp <= league.maxXp) return league;
        }
        return leagues.size() > 1 ? leagues.get(1) : null;
    }

    private String getUserCountLabel(int count) {
        int lastDigit = count % 10;
        int lastTwoDigits = count % 100;
        if (lastTwoDigits >= 11 && lastTwoDigits <= 14) return "участников";
        if (lastDigit == 1) return "участник";
        if (lastDigit >= 2 && lastDigit <= 4) return "участника";
        return "участников";
    }

    private class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {

        private final List<User> users = new ArrayList<>();
        private long currentUserId = -1;

        void setData(List<User> newUsers, long currentUserId) {
            users.clear();
            if (newUsers != null) users.addAll(newUsers);
            this.currentUserId = currentUserId;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_leaderboard, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            User user = users.get(position);
            int rank = position + 1;
            int xp = getXp(user);
            League league = getLeagueByXp(xp);
            if (league == null) league = leagues.get(0);

            holder.tvRank.setText(String.valueOf(rank));
            holder.tvUsername.setText(getSafeUsername(user));
            holder.tvLevel.setText(league.icon + " " + league.title + " • уровень " + getLevel(user));
            holder.tvXp.setText(xp + " XP");

            boolean isMe = user.getId() != null && user.getId() == currentUserId;

            if (isMe) {
                holder.cardRoot.setCardBackgroundColor(colorCardMeBg);
                holder.cardRoot.setStrokeColor(colorCardMeStroke);
                holder.cardRoot.setStrokeWidth(dp(3));

                // ✅ Имя для "моя" — всегда контрастное
                holder.tvUsername.setTextColor(colorUsernameMe);
            } else {
                holder.cardRoot.setCardBackgroundColor(colorCardBg);
                holder.cardRoot.setStrokeColor(league.color);
                holder.cardRoot.setStrokeWidth(dp(1));

                // ✅ Имя для остальных — адаптивный цвет
                holder.tvUsername.setTextColor(colorUsername);
            }

            // ✅ Описание уровня — адаптивный цвет
            holder.tvLevel.setTextColor(colorLevelText);

            bindAvatarToViews(user, league,
                    holder.avatarContainer, holder.avatarView, holder.tvAvatarFallback);

            if (rank <= 3) {
                holder.tvRank.setTextColor(league.color);
            } else if (isMe) {
                holder.tvRank.setTextColor(colorRankMe);
            } else {
                holder.tvRank.setTextColor(colorRankDefault);
            }

            holder.tvXp.setTextColor(league.color);
        }

        @Override
        public int getItemCount() {
            return users.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            MaterialCardView cardRoot;
            TextView tvRank;
            TextView tvUsername;
            TextView tvLevel;
            TextView tvXp;
            TextView tvAvatarFallback;
            FrameLayout avatarContainer;
            AvatarPreviewView avatarView;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                cardRoot = itemView.findViewById(R.id.cardRoot);
                tvRank = itemView.findViewById(R.id.tvRank);
                tvUsername = itemView.findViewById(R.id.tvUsername);
                tvLevel = itemView.findViewById(R.id.tvLevel);
                tvXp = itemView.findViewById(R.id.tvXp);
                tvAvatarFallback = itemView.findViewById(R.id.tvAvatarFallback);
                avatarContainer = itemView.findViewById(R.id.avatarContainer);
                avatarView = itemView.findViewById(R.id.avatarView);
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class League {
        String title;
        String subtitle;
        String icon;
        int minXp;
        int maxXp;
        int color;
        boolean isRealLeague;

        League(String title, String subtitle, String icon,
               int minXp, int maxXp, int color, boolean isRealLeague) {
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
            this.minXp = minXp;
            this.maxXp = maxXp;
            this.color = color;
            this.isRealLeague = isRealLeague;
        }
    }

    private void applyAvatarCircleBackground(FrameLayout container,
                                             int fillColor,
                                             int strokeColor,
                                             int strokeWidthDp) {
        if (container == null) return;
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fillColor);
        drawable.setStroke(dp(strokeWidthDp), strokeColor);
        container.setBackground(drawable);
        container.setClipToOutline(true);
    }

    private boolean isValidAvatarBackgroundColor(int color) {
        return Color.alpha(color) > 0;
    }
}