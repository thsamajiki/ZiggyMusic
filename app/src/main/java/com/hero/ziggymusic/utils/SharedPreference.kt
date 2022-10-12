package com.hero.ziggymusic.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import java.util.*

class SharedPreference {

    private val PREF_NAME = "myapp.pref"

    companion object {
        private var sharedPreference: SharedPreference? = null

        fun getInstance(): SharedPreference {
            return sharedPreference ?: synchronized(this) {
                sharedPreference ?: SharedPreference().also {
                    sharedPreference = it
                }
            }
        }
    }

    fun put(context: Context, key: String?, value: String?) {
        val pref = context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
        val editor = pref.edit()
        editor.putString(key, value)
        editor.apply()
    }

    fun putToken(context: Context, value: String?) {
        val pref = context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
        val editor = pref.edit()
        editor.putString("fcmToken", value)
        editor.apply()
    }

    fun put(context: Context, key: String?, value: Long) {
        val pref = context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
        val editor = pref.edit()
        editor.putLong(key, value)
        editor.apply()
    }

    fun put(context: Context, key: String?, value: HashSet<String?>?) {
        val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = pref.edit()
        editor.putStringSet(key, value)
        editor.apply()
    }

    fun put(context: Context, key: String?, value: Boolean) {
        val pref = context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
        val editor = pref.edit()
        editor.putBoolean(key, value)
        editor.apply()
    }

    fun putFirstOpen(context: Context, key: String?, value: Boolean) {
        val pref = context.getSharedPreferences(
            "firstopen",
            Context.MODE_PRIVATE
        )
        val editor = pref.edit()
        editor.putBoolean(key, value)
        editor.apply()
    }

    fun getFirstOpen(context: Context, key: String?, dftValue: Boolean): Boolean {
        val pref = context.getSharedPreferences(
            "firstopen",
            Context.MODE_PRIVATE
        )
        return try {
            pref.getBoolean(key, dftValue)
        } catch (e: Exception) {
            dftValue
        }
    }

    fun put(context: Context, key: String?, value: Int) {
        val pref = context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
        val editor = pref.edit()
        editor.putInt(key, value)
        editor.apply()
    }

    fun remove(context: Context) {
        val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = pref.edit()
        editor.clear()
        editor.apply()
    }


    fun getValue(context: Context, key: String?, dftValue: String?): String? {
        val pref = context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
        return try {
            pref.getString(key, dftValue)
        } catch (e: Exception) {
            dftValue
        }
    }

    fun getValueToken(context: Context): String? {
        val pref = context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
        return try {
            pref.getString("fcmToken", "")
        } catch (e: Exception) {
            ""
        }
    }

    fun getValue(context: Context, key: String?, value: HashSet<String?>?): Set<String?>? {
        val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return try {
            pref.getStringSet(key, value)
        } catch (e: Exception) {
            value
        }
    }

    fun getValue(context: Context, key: String?, dftValue: Int): Int {
        val pref = context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
        return try {
            pref.getInt(key, dftValue)
        } catch (e: Exception) {
            dftValue
        }
    }

    fun getValue(context: Context, key: String?, dftValue: Long): Long {
        val pref = context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
        return try {
            pref.getLong(key, dftValue)
        } catch (e: Exception) {
            dftValue
        }
    }

    fun getValue(context: Context, key: String?, dftValue: Boolean): Boolean {
        val pref = context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
        return try {
            pref.getBoolean(key, dftValue)
        } catch (e: Exception) {
            dftValue
        }
    }

    fun storeList(context: Context, key: String?, countries: List<String>?) {
        val settings: SharedPreferences
        val editor: SharedPreferences.Editor
        settings = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        editor = settings.edit()
        val gson = Gson()
        val jsonFavorites: String = gson.toJson(countries)
        editor.putString(key, jsonFavorites)
        editor.apply()
    }

    fun loadList(context: Context, key: String?): MutableList<String>? {
        val pref: SharedPreferences
        var favorites: MutableList<String>?
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (pref.contains(key)) {
            val jsonFavorites = pref.getString(key, null)
            val gson = Gson()
            val favoriteItems: Array<String> = gson.fromJson(
                jsonFavorites,
                Array<String>::class.java
            )
            favorites = Arrays.asList(*favoriteItems)
            favorites = ArrayList(favorites)
        } else return null
        return favorites
    }

    fun addList(context: Context, key: String?, country: String) {
        var favorites = loadList(context, key)
        if (favorites == null) favorites = ArrayList()
        if (favorites.contains(country)) {
            favorites.remove(country)
        }
        favorites.add(country)
        storeList(context, key, favorites)
    }

    fun removeList(context: Context, key: String?, country: String) {
        val favorites = loadList(context, key)
        if (favorites != null) {
            favorites.remove(country)
            storeList(context, key, favorites)
        }
    }

    fun deleteList(context: Context, key: String?) {
        val list = loadList(context, key)
        list?.clear()
        storeList(context, key, list)
    }
}