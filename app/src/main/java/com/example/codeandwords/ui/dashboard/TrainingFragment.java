package com.example.codeandwords.ui.dashboard;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.codeandwords.databinding.FragmentTrainingBinding;
import com.example.codeandwords.ui.game.LearnedWordsActivity;
import com.example.codeandwords.ui.game.ListeningGameActivity;
import com.example.codeandwords.ui.game.MistakesTrainingActivity;
import com.example.codeandwords.ui.game.WriteWordGameActivity;

public class TrainingFragment extends Fragment {

    private static final float CARD_PRESSED_SCALE = 0.965f;
    private static final float CARD_NORMAL_SCALE = 1.0f;

    private static final float CARD_PRESSED_ALPHA = 0.88f;
    private static final float CARD_NORMAL_ALPHA = 1.0f;

    private static final long CARD_PRESS_ANIMATION_DURATION = 90L;
    private static final long CARD_RELEASE_ANIMATION_DURATION = 140L;

    private FragmentTrainingBinding binding;

    public TrainingFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentTrainingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupClicks();
        setupCardAnimations();
    }

    private void setupClicks() {
        binding.cardListening.setOnClickListener(v -> openListeningTraining());

        binding.cardMistakes.setOnClickListener(v -> openMistakesTraining());

        binding.cardWords.setOnClickListener(v -> openWordsTraining());

        binding.cardLearnedWords.setOnClickListener(v -> openLearnedWords());
    }

    private void setupCardAnimations() {
        applyPressAnimation(binding.cardListening);
        applyPressAnimation(binding.cardMistakes);
        applyPressAnimation(binding.cardWords);
        applyPressAnimation(binding.cardLearnedWords);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void applyPressAnimation(View card) {
        if (card == null) return;

        card.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    animateCardPressed(view);
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    animateCardReleased(view);
                    break;

                default:
                    break;
            }

            return false;
        });
    }

    private void animateCardPressed(View view) {
        view.animate()
                .scaleX(CARD_PRESSED_SCALE)
                .scaleY(CARD_PRESSED_SCALE)
                .alpha(CARD_PRESSED_ALPHA)
                .setDuration(CARD_PRESS_ANIMATION_DURATION)
                .start();
    }

    private void animateCardReleased(View view) {
        view.animate()
                .scaleX(CARD_NORMAL_SCALE)
                .scaleY(CARD_NORMAL_SCALE)
                .alpha(CARD_NORMAL_ALPHA)
                .setDuration(CARD_RELEASE_ANIMATION_DURATION)
                .start();
    }

    private void openListeningTraining() {
        Intent intent = new Intent(requireContext(), ListeningGameActivity.class);
        startActivity(intent);
    }

    private void openMistakesTraining() {
        Intent intent = new Intent(requireContext(), MistakesTrainingActivity.class);
        startActivity(intent);
    }

    private void openWordsTraining() {
        Intent intent = new Intent(requireContext(), WriteWordGameActivity.class);
        intent.putExtra("TRAINING_MODE", "LEARNED_WORDS");
        startActivity(intent);
    }

    private void openLearnedWords() {
        Intent intent = new Intent(requireContext(), LearnedWordsActivity.class);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}