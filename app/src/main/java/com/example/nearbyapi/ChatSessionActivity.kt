package com.example.nearbyapi

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Bundle
import android.os.IBinder
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.nearbyapi.Utils.BG_NOTIFICATION_CHANNEL_ID
import com.example.nearbyapi.databinding.ActivityChatSessionBinding

class ChatSessionActivity : AppCompatActivity() {

    private var userName = ""
    private lateinit var binding: ActivityChatSessionBinding
    private val availableDevices = ArrayList<Pair<String, String>>()
    private var selectedOpponent: Pair<String, String>? = null
    private lateinit var usersAdapter: UsersAdapter
    private lateinit var viewModel: ChatViewModel
    private lateinit var loadingDialog: AlertDialog
    private var connectionService: NearbyConnectService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as NearbyConnectService.NearbyBinder
            connectionService = binder.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connectionService = null
        }
    }

    private val connectionStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getBooleanExtra("IS_CONNECTED", false) == true) {
                showChatScreen("1-")
            } else {
                removeChatScreen()
            }
        }
    }

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("MESSAGE")?.let { message ->
                val list = ArrayList<Pair<Boolean, String>>()
                list.add((Pair(false, message)))
                viewModel.addSessionMessages(list)
            }

            intent?.getStringExtra("TOAST")?.let { message ->
                showMessage(message)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        createNotificationChannel()
        createLoadingDialog()
        userName = intent.getStringExtra(KEY_NAME) ?: ""

        binding.scOnline.isChecked = NearbyConnectService.isServiceActive
        binding.scOnline.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.scOnline.text = "Online"
                NearbyConnectService.userName = userName
                startService(Intent(this, NearbyConnectService::class.java))
                bindService(
                    Intent(this, NearbyConnectService::class.java),
                    serviceConnection,
                    Context.BIND_AUTO_CREATE
                )
                LocalBroadcastManager.getInstance(this).run {
                    registerReceiver(connectionStatusReceiver, IntentFilter("CONNECTION_STATUS"))
                    registerReceiver(messageReceiver, IntentFilter("MESSAGE_RECEIVED"))
                }
            } else {
                binding.scOnline.text = "Offline"
                NearbyConnectService.userName = ""
                availableDevices.clear()
                stopService((Intent(this, NearbyConnectService::class.java)))
                unbindService(serviceConnection)
                LocalBroadcastManager.getInstance(this).run {
                    unregisterReceiver(connectionStatusReceiver)
                    unregisterReceiver(messageReceiver)
                }
            }
        }
        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]

        viewModel.sendMessage.observe(this) { msg ->
            connectionService?.sendMessage(msg)
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
        if (NearbyConnectService.userName.isNotEmpty()) {
            showChatScreen("1-")
            connectionService?.run { viewModel.addSessionMessages(getMessages()) }
            bindService(
                Intent(this, NearbyConnectService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
            LocalBroadcastManager.getInstance(this).run {
                registerReceiver(connectionStatusReceiver, IntentFilter("CONNECTION_STATUS"))
                registerReceiver(messageReceiver, IntentFilter("MESSAGE_RECEIVED"))
            }
        }
    }

    private fun createNotificationChannel() {
        if (SDK_INT >= O) {
            val channel = NotificationChannel(
                BG_NOTIFICATION_CHANNEL_ID,
                "Connect Service Channel",
                IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun dismissChatFragment() {
        AlertDialog.Builder(this@ChatSessionActivity)
            .setTitle("Do you want to end this chat?")
            .setPositiveButton(
                "Yes"
            ) { _: DialogInterface?, _: Int ->  // The user confirmed, so we can accept the connection.
                connectionService?.endChat()
                removeChatScreen()
            }
            .setNegativeButton(
                "No"
            ) { _: DialogInterface?, _: Int ->  // The user canceled, so we should reject the connection.
            }
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun removeChatScreen() {
        viewModel.onSessionEnded()
        binding.flCategoriesContainer.visibility = GONE
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

    private fun createLoadingDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setView(R.layout.progress_dialog)
        builder.setCancelable(false)

        loadingDialog = builder.create()
    }

    companion object {
        const val KEY_NAME = "KEY_USER_NAME"
        fun newInstance(context: Context, userName: String): Intent {
            return Intent(context, ChatSessionActivity::class.java).apply {
                putExtra(KEY_NAME, userName)
            }
        }
    }
}