package com.example.nearbyapi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat.getDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import com.example.nearbyapi.databinding.FragmentChatBinding

/**
 * @author Munif
 * @since 27/10/24.
 */
class ChatFragment : Fragment() {

    private var binding: FragmentChatBinding? = null
    private var messages = ArrayList<Pair<Boolean, String>>()
    private val adapter: ChatAdapter = ChatAdapter(messages)
    private lateinit var viewModel: ChatViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun init() {
        viewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]
        viewModel.receivedMessage.observe(requireActivity()) { msg ->
            messages.add(0, Pair(false, msg))
            adapter.notifyDataSetChanged()
        }

        viewModel.onSessionEnd.observe(requireActivity()) {
            messages.clear()
            adapter.notifyDataSetChanged()
        }

        binding?.run {
            toolbar.title = arguments?.getString(KEY_OPPONENT_NAME) ?: ""
            toolbar.navigationIcon = context?.let { getDrawable(it, R.drawable.ic_chat_back) }
            toolbar.setNavigationOnClickListener {
                viewModel.onBackNavigationClicked()
            }
            rvChat.layoutManager = LinearLayoutManager(requireContext(), VERTICAL, true)
            rvChat.adapter = adapter
            ivSendMessage.setOnClickListener {
                val message = etMessage.text?.trim().toString()
                if (message.isEmpty()) {
                    return@setOnClickListener
                }
                etMessage.setText("")
                viewModel.sendMessage(message)
                messages.add(0, Pair(true, message))
                adapter.notifyDataSetChanged()
            }
        }
    }

    companion object {
        private const val KEY_OPPONENT_NAME = "KEY_OPPONENT_NAME"
        fun newInstance(opponentName: String): ChatFragment {
            return ChatFragment().apply {
                arguments = Bundle().apply {
                    putString(KEY_OPPONENT_NAME, opponentName)
                }
            }
        }
    }
}