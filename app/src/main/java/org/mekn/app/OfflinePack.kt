package org.mekn.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.zip.ZipInputStream

/**
 * The offline research pack: real search results (papers, trials, FDA records, side effects,
 * chemistry) for hundreds of topics, downloaded on GitHub when the app is built and shipped
 * inside the APK as assets/offline-pack.zip. On first launch it is unpacked into the same
 * cache the app uses for saved searches, so those topics work with no internet at all.
 */
object OfflinePack {

    /** Topics available offline (shown in Explore). */
    var topics by mutableStateOf<List<String>>(emptyList())
        private set

    /** True while the pack is being unpacked on first launch. */
    var installing by mutableStateOf(false)
        private set

    fun install(context: Context) {
        val dir = Api.cacheDir ?: return
        val version = try {
            context.assets.open("offline-pack.version").bufferedReader().use { it.readText().trim() }
        } catch (e: Exception) {
            null   // this build has no offline pack
        }
        val prefs = context.getSharedPreferences("renk", Context.MODE_PRIVATE)
        if (version != null && prefs.getString("packVersion", null) != version) {
            installing = true
            try {
                ZipInputStream(context.assets.open("offline-pack.zip").buffered()).use { zin ->
                    var entry = zin.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val target = File(dir, File(entry.name).name)   // flat folder, no paths
                            target.outputStream().use { zin.copyTo(it) }
                        }
                        entry = zin.nextEntry
                    }
                }
                prefs.edit().putString("packVersion", version).apply()
            } catch (e: Exception) {
                // a damaged pack must never stop the app; live search still works
            } finally {
                installing = false
            }
        }
        topics = File(dir, "pack-topics.txt").takeIf { it.exists() }
            ?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    }
}
