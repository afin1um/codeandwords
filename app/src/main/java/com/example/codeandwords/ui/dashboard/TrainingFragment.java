package com.example.codeandwords.ui.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.codeandwords.databinding.FragmentTrainingBinding;
import com.example.codeandwords.ui.PersonalDictionaryActivity;

public class TrainingFragment extends Fragment {

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
    }

    private void setupClicks() {
        binding.btnStartTraining.setOnClickListener(v ->
                Toast.makeText(requireContext(), "Запуск тренировки", Toast.LENGTH_SHORT).show()
        );

        binding.cardSpeech.setOnClickListener(v ->
                Toast.makeText(requireContext(), "Раздел: Речь", Toast.LENGTH_SHORT).show()
        );

        binding.cardListening.setOnClickListener(v ->
                Toast.makeText(requireContext(), "Раздел: Аудирование", Toast.LENGTH_SHORT).show()
        );

        binding.cardMistakes.setOnClickListener(v ->
                Toast.makeText(requireContext(), "Раздел: Ошибки", Toast.LENGTH_SHORT).show()
        );

        binding.cardWords.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), PersonalDictionaryActivity.class);
            startActivity(intent);
        });

        binding.cardStories.setOnClickListener(v ->
                Toast.makeText(requireContext(), "Раздел: Истории", Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}