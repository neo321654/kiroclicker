package com.autoclicker.android.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.TextView
import com.autoclicker.android.R

/**
 * Adapter for displaying saved configurations in a list with load and delete actions
 */
class ConfigListAdapter(
    private val configNames: List<String>,
    private val onActionClick: (String, Action) -> Unit
) : BaseAdapter() {
    
    enum class Action {
        LOAD, DELETE
    }
    
    override fun getCount(): Int = configNames.size
    
    override fun getItem(position: Int): String = configNames[position]
    
    override fun getItemId(position: Int): Long = position.toLong()
    
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(parent?.context)
            .inflate(R.layout.item_config, parent, false)
        
        val configName = configNames[position]
        
        val tvConfigName = view.findViewById<TextView>(R.id.tv_config_name)
        val tvConfigDetails = view.findViewById<TextView>(R.id.tv_config_details)
        val btnDelete = view.findViewById<ImageButton>(R.id.btn_delete_config)
        
        tvConfigName.text = configName
        tvConfigDetails.text = "Tap to load this configuration"
        
        // Set click listener for the entire item (load action)
        view.setOnClickListener {
            onActionClick(configName, Action.LOAD)
        }
        
        // Set click listener for delete button
        btnDelete.setOnClickListener {
            onActionClick(configName, Action.DELETE)
        }
        
        return view
    }
}