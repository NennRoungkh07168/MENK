package org.mekn.app

import android.content.Context
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One block of a report: a heading and its text. */
data class ReportSection(val heading: String, val body: String)

/**
 * Writes a simple A4 PDF report with Android's built-in PdfDocument (no extra libraries).
 * The user picks where to save it, so no storage permission is needed.
 */
object PdfExport {

    private const val PAGE_W = 595   // A4 in points
    private const val PAGE_H = 842
    private const val MARGIN = 48

    /** Writes the report to any output stream. */
    fun writeStream(out: OutputStream, title: String, sections: List<ReportSection>): Boolean = try {
        val doc = PdfDocument()
        val width = PAGE_W - 2 * MARGIN

        val titlePaint = TextPaint().apply { isAntiAlias = true; textSize = 20f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
        val headPaint = TextPaint().apply { isAntiAlias = true; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; color = 0xFF1F4E8C.toInt() }
        val bodyPaint = TextPaint().apply { isAntiAlias = true; textSize = 10.5f; color = 0xFF16202E.toInt() }
        val smallPaint = TextPaint().apply { isAntiAlias = true; textSize = 8.5f; color = 0xFF4A5566.toInt() }

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            y = MARGIN.toFloat()
            val c = page!!.canvas
            c.drawText("RENK · Research Evidence Network of Knowledge", MARGIN.toFloat(), (PAGE_H - 24).toFloat(), smallPaint)
            c.drawText("Page $pageNo", (PAGE_W - MARGIN - 40).toFloat(), (PAGE_H - 24).toFloat(), smallPaint)
        }

        /** Draws text, splitting it across pages line by line when needed. */
        fun draw(text: String, paint: TextPaint, gapAfter: Float) {
            if (text.isBlank()) return
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1f)
                .build()
            var line = 0
            while (line < layout.lineCount) {
                val bottom = PAGE_H - MARGIN - 20
                if (y + (layout.getLineBottom(line) - layout.getLineTop(line)) > bottom) newPage()
                // draw as many lines as fit on this page
                val startTop = layout.getLineTop(line)
                var end = line
                while (end < layout.lineCount && y + (layout.getLineBottom(end) - startTop) <= bottom) end++
                if (end == line) end = line + 1   // always make progress
                val c = page!!.canvas
                c.save()
                c.translate(MARGIN.toFloat(), y - startTop)
                c.clipRect(0f, startTop.toFloat(), width.toFloat(), layout.getLineBottom(end - 1).toFloat())
                layout.draw(c)
                c.restore()
                y += (layout.getLineBottom(end - 1) - startTop).toFloat()
                line = end
            }
            y += gapAfter
        }

        newPage()
        draw(title, titlePaint, 4f)
        val date = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        draw("Created $date", smallPaint, 14f)
        for (s in sections) {
            draw(s.heading, headPaint, 4f)
            draw(s.body, bodyPaint, 12f)
        }
        draw(
            "RENK summarises published and official sources. It is not medical advice and does not replace a " +
                "doctor or pharmacist. Check the original sources before relying on any detail.",
            smallPaint, 0f
        )
        page?.let { doc.finishPage(it) }

        doc.writeTo(out)
        doc.close()
        true
    } catch (e: Exception) {
        false
    }

    /** Writes the report to a file the user chose (system "Save as" screen). */
    fun write(context: Context, uri: Uri, title: String, sections: List<ReportSection>): Boolean = try {
        context.contentResolver.openOutputStream(uri)?.use { writeStream(it, title, sections) } ?: false
    } catch (e: Exception) {
        false
    }

    // ----- Evidence PDF library: reports kept inside the app, readable offline -----

    fun libraryDir(context: Context): File = File(context.filesDir, "evidence-pdfs").apply { mkdirs() }

    /** Saves a report into the app's Evidence library. Returns the file, or null on failure. */
    fun saveToLibrary(context: Context, topic: String, title: String, sections: List<ReportSection>): File? = try {
        val file = File(libraryDir(context), fileName(topic))
        val ok = FileOutputStream(file).use { writeStream(it, title, sections) }
        if (ok) file else { file.delete(); null }
    } catch (e: Exception) {
        null
    }

    /** Saved reports, newest first. */
    fun savedReports(context: Context): List<File> =
        libraryDir(context).listFiles { f -> f.name.endsWith(".pdf") }?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** Copies a saved report to a place the user chose, e.g. Downloads. */
    fun exportCopy(context: Context, file: File, uri: Uri): Boolean = try {
        context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        true
    } catch (e: Exception) {
        false
    }

    /** Safe default file name, e.g. "RENK-aspirin-2026-10-02.pdf". */
    fun fileName(topic: String): String {
        val clean = topic.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifBlank { "report" }
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "RENK-$clean-$day.pdf"
    }
}
