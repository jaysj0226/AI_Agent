package com.cookandroid.ai_agent.ui.health;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.cookandroid.ai_agent.R;
import com.cookandroid.ai_agent.databinding.ActivityHealthConnectRationaleBinding;

public class HealthConnectPermissionsRationaleActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityHealthConnectRationaleBinding binding =
                ActivityHealthConnectRationaleBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.buttonCloseRationale.setOnClickListener(view -> finish());
    }
}
