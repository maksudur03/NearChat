package com.example.nearbyapi

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_ADVERTISE
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.Manifest.permission.NEARBY_WIFI_DEVICES
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.nearbyapi.databinding.ActivityAvailableDevicesBinding
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes.STATUS_OK
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy.P2P_STAR


class AvailableDevicesActivity : AppCompatActivity() {

    private lateinit var connectionsClient: ConnectionsClient
    private lateinit var binding: ActivityAvailableDevicesBinding

    private val strategy = P2P_STAR
    private val availableDevices = ArrayList<String>()

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ ->
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAvailableDevicesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        connectionsClient = Nearby.getConnectionsClient(this)

        binding.findDevices.setOnClickListener {
            startDiscovery()
        }
        binding.btnGoOnline.setOnClickListener {
            startAdvertising()
        }
    }

    override fun onStop() {
        connectionsClient.apply {
            stopAdvertising()
            stopDiscovery()
            stopAllEndpoints()
        }
        super.onStop()
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let {
                binding.tvReceivedMessage.text = String(it)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {

        }
    }

    private val advertiseCallback = object :
        ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            binding.tvStatus.text = "Status onConnectionInitiated"
            AlertDialog.Builder(this@AvailableDevicesActivity)
                .setTitle("Accept connection to " + info.endpointName)
                .setMessage("Confirm the code matches on both devices: " + info.authenticationDigits)
                .setPositiveButton(
                    "Accept"
                ) { _: DialogInterface?, _: Int ->  // The user confirmed, so we can accept the connection.
                    Nearby.getConnectionsClient(this@AvailableDevicesActivity)
                        .acceptConnection(endpointId, payloadCallback)
                }
                .setNegativeButton(
                    android.R.string.cancel
                ) { _: DialogInterface?, _: Int ->  // The user canceled, so we should reject the connection.
                    Nearby.getConnectionsClient(this@AvailableDevicesActivity).rejectConnection(endpointId)
                }
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show()
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            binding.tvStatus.text = "Status onConnectionResult"
            if (result.status.statusCode == STATUS_OK) {
                binding.sendData.setOnClickListener {
                    val msg = Payload.fromBytes("Hello".toByteArray())
                    connectionsClient.sendPayload(endpointId, msg)
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            binding.tvStatus.text = "Status onDisconnected"
        }
    }

    private fun startAdvertising() {
        binding.tvStatus.text = "Status startAdvertising"
        val options = AdvertisingOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startAdvertising(Build.MODEL, packageName, advertiseCallback, options)
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            binding.tvStatus.text = "Status onEndpointFound"
            availableDevices.add(endpointId)
            binding.tvDevices.text = availableDevices.toString()
            binding.connectFirst.setOnClickListener {
                connectionsClient.requestConnection(Build.MODEL, endpointId, advertiseCallback)
                    .addOnSuccessListener {
                        showMessage("Connection Requested")
                    }.addOnFailureListener {
                        showMessage("Connection Request Failed")
                    }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            binding.tvStatus.text = "Status onEndpointLost"
            availableDevices.remove(endpointId)
            binding.tvDevices.text = availableDevices.toString()
        }
    }

    private fun startDiscovery() {
        binding.tvStatus.text = "Status startDiscovery"
        val options = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(packageName, discoveryCallback, options)
    }

    override fun onStart() {
        super.onStart()
        if (checkSelfPermission(ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(
                arrayOf(
                    ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION,
                    BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE, BLUETOOTH_SCAN, NEARBY_WIFI_DEVICES
                )
            )
        }
    }

    private fun showMessage(msg: String) {
        Toast.makeText(
            this@AvailableDevicesActivity,
            msg,
            Toast.LENGTH_LONG
        ).show()
    }
}