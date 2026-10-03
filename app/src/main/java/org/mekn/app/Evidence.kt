package org.mekn.app

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Evidence tab: the saved-PDF library (offline) and the guide to evidence levels. */
@Composable
fun EvidenceScreen() {
    var tab by rememberSaveable { mutableStateOf(0) }
    var viewing by remember { mutableStateOf<File?>(null) }

    BackHandler(enabled = viewing != null) { viewing = null }

    val open = viewing
    if (open != null) {
        PdfViewer(open, onBack = { viewing = null })
        return
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(Mekn.Ink).statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = MAX_WIDTH).fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Evidence", color = Mekn.Ground, fontFamily = Mekn.Display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp)
                Text("Your saved PDF reports, readable offline, and how RENK grades evidence",
                    color = Mekn.OnInkMuted, fontSize = 12.sp)
                Segmented(listOf("Saved PDFs", "How we grade"), tab, { tab = it })
            }
        }
        if (tab == 0) SavedPdfList(onOpen = { viewing = it }) else EvidenceGuide()
    }
}

@Composable
private fun SavedPdfList(onOpen: (File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    val files = remember(refresh) { PdfExport.savedReports(context) }
    var pendingExport by remember { mutableStateOf<File?>(null) }
    var confirmDelete by remember { mutableStateOf<File?>(null) }

    // "Save a copy": the user picks a place such as Downloads, to share or keep outside the app.
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val f = pendingExport
        if (uri != null && f != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { PdfExport.exportCopy(context, f, uri) }
                Toast.makeText(context, if (ok) "Copy saved" else "Couldn't save the copy", Toast.LENGTH_SHORT).show()
            }
        }
        pendingExport = null
    }

    confirmDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this report?") },
            text = { Text(f.name) },
            confirmButton = {
                TextButton(onClick = { f.delete(); confirmDelete = null; refresh++ }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = MAX_WIDTH).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (files.isEmpty()) {
                item {
                    BorderCard {
                        Text("No saved reports yet", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Mekn.Ink)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Search any medicine, disease or herb, or open a library page, then tap the PDF button at the " +
                                "top right. The report is saved here and opens without internet.",
                            fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFF2F3A4A)
                        )
                    }
                }
            } else {
                item {
                    Text("${files.size} saved reports · stored on this device", fontSize = 13.sp, color = Mekn.Muted)
                }
            }
            items(files, key = { it.name }) { f ->
                Surface(
                    color = Mekn.Surface, shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Mekn.Line), modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = Mekn.Preclinical)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name.removeSuffix(".pdf"), fontWeight = FontWeight.SemiBold, color = Mekn.Ink)
                                val date = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(f.lastModified()))
                                Text("$date · ${(f.length() / 1024).coerceAtLeast(1)} KB", fontSize = 12.sp, color = Mekn.Muted)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onOpen(f) }) { Text("Open") }
                            TextButton(onClick = { pendingExport = f; exporter.launch(f.name) }) { Text("Save a copy") }
                            TextButton(onClick = { confirmDelete = f }) { Text("Delete", color = Mekn.Preclinical) }
                        }
                    }
                }
            }
        }
    }
}

/** Shows a saved PDF inside the app, page by page, with Android's built-in PdfRenderer. */
@Composable
private fun PdfViewer(file: File, onBack: () -> Unit) {
    val pages by produceState<List<ImageBitmap>?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) {
            try { renderPdf(file, 1100) } catch (e: Exception) { emptyList() }
        }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(file.name.removeSuffix(".pdf"), "Saved report · works offline", onBack)
        Box(Modifier.fillMaxSize().background(Color(0xFFDDD9D0)), contentAlignment = Alignment.TopCenter) {
            val list = pages
            when {
                list == null -> Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Opening report…", color = Mekn.Muted)
                }
                list.isEmpty() -> Text("This report couldn't be opened.", color = Mekn.Preclinical, modifier = Modifier.padding(24.dp))
                else -> LazyColumn(
                    Modifier.widthIn(max = MAX_WIDTH).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(list.size) { i ->
                        Image(
                            bitmap = list[i], contentDescription = "Page ${i + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().background(Color.White)
                        )
                    }
                }
            }
        }
    }
}

private fun renderPdf(file: File, widthPx: Int): List<ImageBitmap> {
    val out = mutableListOf<ImageBitmap>()
    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(pfd)
    try {
        for (i in 0 until minOf(renderer.pageCount, 40)) {
            val page = renderer.openPage(i)
            try {
                val height = (widthPx.toFloat() * page.height / page.width).toInt()
                val bmp = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                out += bmp.asImageBitmap()
            } finally {
                page.close()
            }
        }
    } finally {
        renderer.close()
        pfd.close()
    }
    return out
}
