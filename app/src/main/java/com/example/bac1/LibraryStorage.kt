package com.example.bac1

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object LibraryStorage {
    private const val PREFS_NAME = "library_prefs"
    private const val KEY_ITEMS = "library_items"

    fun saveItems(context: Context, items: List<LibraryItem>) {
        val jsonArray = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("fileName", item.fileName)
            obj.put("filePath", item.filePath)
            obj.put("fileType", item.fileType)
            obj.put("subject", item.subject)
            obj.put("noteText", item.noteText)
            obj.put("dateAdded", item.dateAdded)
            jsonArray.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ITEMS, jsonArray.toString()).apply()
    }

    fun loadItems(context: Context): MutableList<LibraryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_ITEMS, null) ?: return mutableListOf()
        val list = mutableListOf<LibraryItem>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    LibraryItem(
                        id = obj.getString("id"),
                        fileName = obj.getString("fileName"),
                        filePath = obj.getString("filePath"),
                        fileType = obj.getString("fileType"),
                        subject = obj.getString("subject"),
                        noteText = obj.getString("noteText"),
                        dateAdded = obj.getLong("dateAdded")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addItem(context: Context, item: LibraryItem) {
        val items = loadItems(context)
        items.add(item)
        saveItems(context, items)
    }

    fun deleteItem(context: Context, itemId: String) {
        val items = loadItems(context)
        items.removeAll { it.id == itemId }
        saveItems(context, items)
    }
}
