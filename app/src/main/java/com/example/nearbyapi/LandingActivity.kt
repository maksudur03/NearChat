package com.example.nearbyapi

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_ADVERTISE
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.Manifest.permission.NEARBY_WIFI_DEVICES
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import com.example.nearbyapi.databinding.ActivityLandingBinding
import com.google.android.material.snackbar.Snackbar

class LandingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLandingBinding

    private val requiredPermissions = arrayOf(
        ACCESS_FINE_LOCATION,
        ACCESS_COARSE_LOCATION,
        BLUETOOTH_CONNECT,
        BLUETOOTH_ADVERTISE,
        BLUETOOTH_SCAN,
        NEARBY_WIFI_DEVICES
    )

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allPermissionsGranted = if (SDK_INT > Build.VERSION_CODES.P) {
                permissions.entries.all { entry ->
                    entry.value
                }
            } else {
                checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
            }
            if (allPermissionsGranted) {
                showCreateNameSegment()
            } else {
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "Some permissions are denied",
                    Snackbar.LENGTH_SHORT
                ).show()

            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    private fun initView() {
        if (NearbyConnectService.isServiceActive) {
            startActivity(ChatSessionActivity.newInstance(this, NearbyConnectService.userName))
        } else {
            if (arePermissionsGranted()) {
                showCreateNameSegment()
            }
            binding.btnAction.setOnClickListener {
                if (binding.llCreateName.isVisible) {
                    val name = binding.etName.text.toString().trim()
                    if (name.isEmpty()) {
                        showMessage("Please type your name")
                        binding.etName.requestFocus()
                        return@setOnClickListener
                    }
                    finish()
                    startActivity(ChatSessionActivity.newInstance(this, name))
                } else {
                    requestPermissionLauncher.launch(requiredPermissions)
                }
            }
        }
    }

    private fun arePermissionsGranted(): Boolean {
        return if (SDK_INT > Build.VERSION_CODES.P) {
            requiredPermissions.all { permission ->
                checkSelfPermission(this, permission) == PERMISSION_GRANTED
            }
        } else {
            checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
        }
    }

    private fun showMessage(msg: String) {
        Toast.makeText(
            this,
            msg,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showCreateNameSegment() {
        binding.run {
            btnAction.text = "Get Started"
            llPermission.visibility = GONE
            llCreateName.visibility = VISIBLE
        }
    }
}