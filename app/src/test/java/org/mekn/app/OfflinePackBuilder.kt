package org.mekn.app

import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds the offline research pack on GitHub (not on the phone).
 *
 * It runs the app's own search code for every topic in app/offline_topics.txt. Each answer is
 * saved exactly as the app saves it, so the app finds it offline under the same key.
 * Only runs when BUILD_OFFLINE_PACK=1 (set by the "Build offline research pack" workflow).
 */
class OfflinePackBuilder {

    @Test
    fun build() {
        if (System.getenv("BUILD_OFFLINE_PACK") != "1") return

        val work = File("build/offline-pack-cache").apply { deleteRecursively(); mkdirs() }
        Api.cacheDir = work
        Api.apiKey = System.getenv("OPENFDA_API_KEY")?.takeIf { it.isNotBlank() }

        fun read(name: String) = File(name).takeIf { it.exists() }?.readLines().orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        // Medicine topics: everything, including FDA records. Study topics: research only (no FDA).
        val medicine = read("offline_topics.txt").distinctBy { it.lowercase() }
        val study = read("study_topics.txt").distinctBy { it.lowercase() }
            .filter { s -> medicine.none { it.equals(s, ignoreCase = true) } }
        val topics = medicine + study

        val packed = mutableListOf<String>()
        topics.forEachIndexed { i, t ->
            try {
                Api.evidenceMap(t)
                Api.papers(t)
                Api.papers(t, reviews = true)      // open-access review articles to study
                Api.trials(t)
                if (i < medicine.size) {
                    Api.resolveCompounds(t)?.first?.forEach { Api.structurePng(it.cid) }
                    Api.approvals(t)
                    Api.label(t)
                    Api.sideEffects(t)
                    Api.market(t)
                    Api.recalls(t)
                } else {
                    // plant compounds and nutrients still get their chemistry, from PubChem only
                    Api.compound(t)?.let { Api.structurePng(it.cid) }
                }
                packed += t
                println("[${i + 1}/${topics.size}] packed: $t")
            } catch (e: Exception) {
                println("[${i + 1}/${topics.size}] skipped: $t ($e)")
            }
            Thread.sleep(400)   // be polite to the public servers
        }

        File(work, "pack-topics.txt").writeText(packed.joinToString("\n"))

        val out = File("build/offline-pack.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            work.listFiles()?.forEach { f ->
                zip.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        println("Offline pack: ${packed.size} topics, ${work.listFiles()?.size ?: 0} records, ${out.length() / 1_000_000} MB")
    }
}
