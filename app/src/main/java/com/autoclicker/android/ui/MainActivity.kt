package com.autoclicker.android.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.autoclicker.android.databinding.ActivityMainBinding

/**
 * Main activity for the AutoClicker application.
 * Hosts the configuration fragment and handles navigation.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Load the configuration fragment if not already loaded
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.fragmentContainer.id, ConfigFragment())
                .commit()
        }
    }
}