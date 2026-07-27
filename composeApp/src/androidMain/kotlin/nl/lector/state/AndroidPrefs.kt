package nl.lector.state

import android.content.Context

class AndroidPrefs(context: Context) : Prefs {
    private val sp = context.getSharedPreferences("lector.v1", Context.MODE_PRIVATE)
    override fun get(key: String): String? = sp.getString(key, null)
    override fun put(key: String, value: String) = sp.edit().putString(key, value).apply()
    override fun clear() = sp.edit().clear().apply()
}
