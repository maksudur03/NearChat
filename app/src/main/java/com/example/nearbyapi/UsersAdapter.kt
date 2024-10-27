package com.example.nearbyapi

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.example.nearbyapi.databinding.ItemUserBinding

/**
 * @author Munif
 * @since 26/10/24.
 */
class UsersAdapter(
    private val users: ArrayList<Pair<String, String>>,
    private val listener: OnUserSelectListener
) : RecyclerView.Adapter<UsersAdapter.UserViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding.root)
    }

    override fun getItemCount() = users.size

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.tvName.text = users[position].second
        holder.itemView.setOnClickListener {
            listener.onUserSelected(users[position].first)
        }
    }

    inner class UserViewHolder(itemView: View) : ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvName)
    }

    interface OnUserSelectListener {
        fun onUserSelected(userId: String)
    }
}