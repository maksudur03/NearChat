package com.example.nearbyapi

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import com.example.nearbyapi.databinding.ActivityChatSessionBinding
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
import com.google.android.gms.nearby.connection.Strategy.P2P_POINT_TO_POINT
import com.google.android.gms.nearby.connection.Strategy.P2P_STAR

class ChatSessionActivity : AppCompatActivity() {

    private var userName = ""
    private lateinit var connectionsClient: ConnectionsClient
    private lateinit var binding: ActivityChatSessionBinding
    private val strategy = P2P_STAR
    private val availableDevices = ArrayList<Pair<String, String>>()
    private var selectedOpponent: Pair<String, String>? = null
    private lateinit var usersAdapter: UsersAdapter
    private lateinit var viewModel: ChatViewModel

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (availableDevices.contains(Pair(endpointId, info.endpointName))) {
                return
            }
            availableDevices.add(Pair(endpointId, info.endpointName))
            usersAdapter.notifyDataSetChanged()
        }

        override fun onEndpointLost(endpointId: String) {
            availableDevices.remove(availableDevices.find { pair -> pair.first == endpointId })
            usersAdapter.notifyDataSetChanged()
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let {
                viewModel.onReceivedMessage(String(it))
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {

        }
    }

    private val advertiseCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            AlertDialog.Builder(this@ChatSessionActivity)
                .setTitle("Accept connection to ${info.endpointName}")
                .setMessage("Confirm the code matches on both devices: " + info.authenticationDigits)
                .setPositiveButton(
                    "Accept"
                ) { _: DialogInterface?, _: Int ->
                    connectionsClient.acceptConnection(endpointId, payloadCallback).
                            addOnFailureListener {
                                showMessage("acceptConnection failed")
                            }
                }
                .setNegativeButton(
                    android.R.string.cancel
                ) { _: DialogInterface?, _: Int ->
                    connectionsClient.rejectConnection(endpointId)
                }
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show()
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.statusCode == STATUS_OK) {
                selectedOpponent = availableDevices.find { pair -> pair.first == endpointId }
                selectedOpponent?.let { opponent ->
                    showChatScreen(opponent.second)
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            resetConnections()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        userName = intent.getStringExtra(KEY_NAME) ?: ""
        connectionsClient = Nearby.getConnectionsClient(this)
        binding.scOnline.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.scOnline.text = "Online"
                startAdvertising()
                startDiscovery()
            } else {
                binding.scOnline.text = "Offline"
                availableDevices.clear()
                usersAdapter.notifyDataSetChanged()
                connectionsClient.apply {
                    stopAdvertising()
                    stopDiscovery()
                    stopAllEndpoints()
                }
            }
        }
        binding.rvUsers.apply {
            layoutManager = LinearLayoutManager(this@ChatSessionActivity, VERTICAL, false)
            usersAdapter =
                UsersAdapter(availableDevices, object : UsersAdapter.OnUserSelectListener {
                    override fun onUserSelected(userId: String) {
                        connectionsClient.requestConnection(
                            userName,
                            userId,
                            advertiseCallback
                        ).addOnFailureListener {
                            showMessage("Connection Request Failed")
                        }
                    }
                })
            adapter = usersAdapter
        }
        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]

        viewModel.sendMessage.observe(this) { msg ->
            selectedOpponent?.let { opponent ->
                connectionsClient.sendPayload(opponent.first, Payload.fromBytes(msg.toByteArray()))
            }
        }
        viewModel.onBackPressed.observe(this) {
            dismissChatFragment()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.flCategoriesContainer.isVisible) {
                    dismissChatFragment()
                }
            }
        })
    }

    private fun dismissChatFragment() {
        AlertDialog.Builder(this@ChatSessionActivity)
            .setTitle("Do you want to end this chat?")
            .setPositiveButton(
                "Yes"
            ) { _: DialogInterface?, _: Int ->  // The user confirmed, so we can accept the connection.
                selectedOpponent?.let { opponent ->
                    connectionsClient.disconnectFromEndpoint(opponent.first)
                }
                resetConnections()
            }
            .setNegativeButton(
                "No"
            ) { _: DialogInterface?, _: Int ->  // The user canceled, so we should reject the connection.
            }
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }


    override fun onStop() {
        connectionsClient.apply {
            stopAdvertising()
            stopDiscovery()
            stopAllEndpoints()
        }
        binding.scOnline.isChecked = false
        super.onStop()
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(packageName, discoveryCallback, options)
            .addOnFailureListener { exception -> showMessage("Discovery Failed ${exception.message}") }
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startAdvertising(userName, packageName, advertiseCallback, options)
            .addOnFailureListener { showMessage("Advertising Failed") }
    }

    private fun resetConnections() {
        viewModel.onSessionEnded()
        binding.flCategoriesContainer.visibility = GONE
        availableDevices.clear()
        usersAdapter.notifyDataSetChanged()
        connectionsClient.apply {
            stopAdvertising()
            stopDiscovery()
            stopAllEndpoints()
            startAdvertising()
            startDiscovery()
        }
    }

    private fun showMessage(msg: String) {
        Toast.makeText(
            this@ChatSessionActivity,
            msg,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showChatScreen(opponentName: String) {
        binding.flCategoriesContainer.visibility = VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(R.id.flCategoriesContainer, ChatFragment.newInstance(opponentName))
            .commit()
    }

    companion object {
        private const val KEY_NAME = "KEY_USER_NAME"
        fun newInstance(context: Context, userName: String): Intent {
            return Intent(context, ChatSessionActivity::class.java).apply {
                putExtra(KEY_NAME, userName)
            }
        }
    }
}