package com.example.nearbyapi

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * @author Munif
 * @since 27/10/24.
 */
class ChatViewModel : ViewModel() {
    private val _receivedMessage = MutableLiveData<String>()
    val receivedMessage: LiveData<String> get() = _receivedMessage

    fun onReceivedMessage(msg: String) {
        _receivedMessage.value = msg
    }

    private val _sendMessage = MutableLiveData<String>()
    val sendMessage: LiveData<String> get() = _sendMessage

    fun sendMessage(msg: String) {
        _sendMessage.value = msg
    }

    private val _onBackPressed = MutableLiveData<Unit>()
    val onBackPressed: LiveData<Unit> get() = _onBackPressed

    fun onBackNavigationClicked() {
        _onBackPressed.value = Unit
    }

    private val _onSessionEnd = MutableLiveData<Unit>()
    val onSessionEnd: LiveData<Unit> get() = _onSessionEnd

    fun onSessionEnded() {
        _onSessionEnd.value = Unit
    }

    private val _sessionMessages = MutableLiveData<ArrayList<Pair<Boolean, String>>>()
    val sessionMessages: LiveData<ArrayList<Pair<Boolean, String>>> get() = _sessionMessages

    fun addSessionMessages(msgList: ArrayList<Pair<Boolean, String>>) {
        _sessionMessages.value = msgList
    }
}