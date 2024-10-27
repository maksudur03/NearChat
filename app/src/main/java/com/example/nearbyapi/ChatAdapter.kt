package com.example.nearbyapi

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.example.nearbyapi.ChatAdapter.ChatViewHolder
import com.example.nearbyapi.databinding.ItemMyMessageBinding
import com.example.nearbyapi.databinding.ItemOpponentMessageBinding

/**
 * @author Munif
 * @since 27/10/24.
 */
private const val VIEW_TYPE_MY_MESSAGE = 0
private const val VIEW_TYPE_OPPONENT_MESSAGE = 1

class ChatAdapter(
    private val messages: ArrayList<Pair<Boolean, String>>,
) : RecyclerView.Adapter<ChatViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = if (viewType == VIEW_TYPE_MY_MESSAGE) {
            ItemMyMessageBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        } else {
            ItemOpponentMessageBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        }
        return ChatViewHolder(binding.root)
    }

    override fun getItemCount() = messages.size

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.tvMessage.text = messages[position].second

    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].first) {
            VIEW_TYPE_MY_MESSAGE
        } else {
            VIEW_TYPE_OPPONENT_MESSAGE
        }
    }

    inner class ChatViewHolder(itemView: View) : ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
    }
}