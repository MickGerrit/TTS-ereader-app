package nl.lector.state

/**
 * Flat key/value persistence. SharedPreferences on Android, `NSUserDefaults` when
 * iOS arrives — an interface rather than expect/actual, so `commonMain` stays
 * buildable on its own and the platform impl is just a constructor argument.
 */
interface Prefs {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun clear()
}

/** Used by previews and tests; forgets everything on the way out. */
class MemoryPrefs : Prefs {
    private val map = mutableMapOf<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun clear() = map.clear()
}
