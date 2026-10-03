package org.mekn.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.foundation.lazy.LazyRow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

// ---------- Data ----------

data class LibSection(val tag: String, val title: String, val text: String)

data class LibEntry(
    val id: String, val type: String, val name: String, val subtitle: String, val country: String, val search: String,
    val summary: String, val sections: List<LibSection>, val sources: List<String>
)

enum class LibKind(val type: String, val label: String, val blurb: String) {
    Medicines("medicine", "Medicines", "Uses, how they work, risks and interactions"),
    Diseases("disease", "Diseases", "Signs, diagnosis and established treatments"),
    Herbs("herb", "Herbs & plants", "Traditional use kept separate from human evidence"),
    Biochem("biochem", "Biochemistry", "Enzymes, receptors and the molecules drugs act on"),
    Companies("company", "Pharma companies", "Germany, India, China, Spain, Russia"),
    Institutions("institution", "Research institutes & labs", "Research centres and trial registries worldwide"),
    Universities("university", "Universities", "Medicine, pharmacy and chemistry universities"),
    Hospitals("hospital", "Hospitals & clinics", "Leading teaching and research hospitals"),
    Regulators("regulator", "Regulators & health systems", "Medicine agencies and public health insurance by country"),
    Devices("device", "Medical devices", "What common devices measure and their limits"),
    Guides("guide", "Guides", "How prescriptions work, side effects, dose basics")
}

object Library {
    @Volatile private var cache: List<LibEntry>? = null

    /** Reads the built-in library from assets/library.json (works offline). */
    fun load(context: Context): List<LibEntry> {
        cache?.let { return it }
        val list = try {
            val text = context.assets.open("library.json").bufferedReader().use { it.readText() }
            val arr = JSONObject(text).getJSONArray("entries")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val secs = o.optJSONArray("sections")
                val srcs = o.optJSONArray("sources")
                LibEntry(
                    id = o.optString("id"), type = o.optString("type"), name = o.optString("name"),
                    subtitle = o.optString("subtitle"), country = o.optString("country"), search = o.optString("search"),
                    summary = o.optString("summary"),
                    sections = (0 until (secs?.length() ?: 0)).map { j ->
                        val s = secs!!.getJSONObject(j)
                        LibSection(s.optString("tag"), s.optString("title"), s.optString("text"))
                    },
                    sources = (0 until (srcs?.length() ?: 0)).map { j -> srcs!!.optString(j) }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
        cache = list
        return list
    }
}

// ---------- Screens ----------

@Composable
fun LibraryScreen(kind: LibKind, onSearch: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val all = remember { Library.load(context) }
    var openId by remember { mutableStateOf<String?>(null) }
    val open = all.firstOrNull { it.id == openId }

    BackHandler(enabled = open != null) { openId = null }

    if (open != null) {
        LibraryDetail(open, onBack = { openId = null }, onSearch = onSearch)
        return
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(Mekn.Ink).statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier.widthIn(max = MAX_WIDTH).fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Mekn.Ground)
                }
                Column {
                    Text(kind.label, color = Mekn.Ground, fontFamily = Mekn.Display,
                        fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
                    Text("RENK Library · works offline", color = Mekn.OnInkMuted, fontSize = 12.sp)
                }
            }
        }
        val ofKind = all.filter { it.type == kind.type }
        val countries = ofKind.map { it.country }.filter { it.isNotBlank() }.distinct().sorted()
        var country by remember(kind) { mutableStateOf("All") }
        val shown = if (country == "All") ofKind else ofKind.filter { it.country == country }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = MAX_WIDTH).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (countries.size > 1) {
                    item { ChipRow(listOf("All") + countries, country) { country = it } }
                }
                if (all.isEmpty()) {
                    item { Text("The library file couldn't be read. Reinstall the app.", color = Mekn.Preclinical) }
                }
                items(shown, key = { it.id }) { e ->
                    Surface(
                        onClick = { openId = e.id },
                        color = Mekn.Surface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Mekn.Line),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(e.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Mekn.Ink)
                            Text(e.subtitle, fontSize = 12.sp, color = Mekn.Muted)
                            Text(e.summary, fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFF2F3A4A))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { c ->
            val on = c == selected
            Surface(
                onClick = { onSelect(c) },
                color = if (on) Mekn.Accent else Mekn.Surface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, if (on) Mekn.Accent else Mekn.Line)
            ) {
                Text(c, color = if (on) Color.White else Mekn.Ink, fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun LibraryDetail(e: LibEntry, onBack: () -> Unit, onSearch: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savePdf: () -> Unit = {
        val sections = listOf(ReportSection("Summary", listOf(e.subtitle, e.summary).filter { it.isNotBlank() }.joinToString("\n"))) +
            e.sections.map { ReportSection("${it.title} (${it.tag})", it.text) } +
            ReportSection("Sources", e.sources.joinToString(", "))
        scope.launch {
            val f = withContext(Dispatchers.IO) { PdfExport.saveToLibrary(context, e.name, e.name, sections) }
            Toast.makeText(context, if (f != null) "Saved to Evidence → Saved PDFs" else "Couldn't save the PDF", Toast.LENGTH_LONG).show()
        }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(Mekn.Ink).statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier.widthIn(max = MAX_WIDTH).fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Mekn.Ground)
                }
                Column(Modifier.weight(1f)) {
                    Text(e.name, color = Mekn.Ground, fontFamily = Mekn.Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
                    Text(e.subtitle, color = Mekn.OnInkMuted, fontSize = 12.sp)
                }
                IconButton(onClick = savePdf) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = "Save as PDF", tint = Mekn.Ground)
                }
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = MAX_WIDTH).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(e.summary, fontSize = 16.sp, lineHeight = 23.sp, color = Mekn.Ink)
                }
                items(e.sections) { s ->
                    BorderCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTag(s.tag)
                            Spacer(Modifier.width(10.dp))
                            Text(s.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Mekn.Ink)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(s.text, fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFF2F3A4A))
                    }
                }
                item {
                    Text("Sources: ${e.sources.joinToString(", ")}", fontSize = 12.sp, color = Mekn.Muted)
                }
                item {
                    Button(
                        onClick = { onSearch(e.search) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mekn.Accent)
                    ) { Text("Search live research: papers, trials, FDA", fontWeight = FontWeight.Bold) }
                }
                item {
                    Text(
                        "General information for learning. It is not medical advice. Talk to a doctor or pharmacist about your own health.",
                        fontSize = 12.sp, lineHeight = 16.sp, color = Mekn.Muted
                    )
                }
            }
        }
    }
}

/** Shows what kind of evidence a section rests on. */
@Composable
private fun SectionTag(tag: String) {
    val shape = RoundedCornerShape(4.dp)
    val (label, style) = when (tag) {
        "Official" -> "Official" to 0
        "Guideline" -> "Guideline" to 0
        "Research" -> "Human research" to 0
        "Lab" -> "Lab only" to 1
        "Traditional" -> "Traditional" to 1
        "Safety" -> "Safety" to 2
        else -> "Info" to 3
    }
    val mod = when (style) {
        0 -> Modifier.background(Mekn.Accent, shape)
        1 -> Modifier.background(Mekn.Surface, shape).border(BorderStroke(1.5.dp, Mekn.Predicted), shape)
        2 -> Modifier.background(Color(0xFFFBEFE4), shape).border(BorderStroke(1.5.dp, Mekn.Preclinical), shape)
        else -> Modifier.background(Color(0xFFECE9E2), shape)
    }
    val color = when (style) {
        0 -> Color.White
        2 -> Color(0xFF8A3F08)
        else -> Color(0xFF3D4452)
    }
    Box(mod.padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

// ---------- Evidence guide ----------

@Composable
fun EvidenceGuide() {
    val levels = listOf(
        "L1" to "Official: regulator labels and approvals, such as the FDA",
        "L2" to "Systematic reviews and meta-analyses of human studies",
        "L3" to "Clinical trials, especially randomized trials",
        "L4" to "Observational human studies and case reports",
        "L5" to "Animal studies",
        "L6" to "Laboratory, biochemical and cell studies",
        "L7" to "Computer predictions",
        "L8" to "Hypotheses and traditional use"
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = MAX_WIDTH).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionTitle("How RENK grades evidence") }
            item {
                Text(
                    "Not all evidence is equal. A lab result or a traditional use is a starting point, not proof " +
                        "that something works in people. RENK labels every claim so you can see what it rests on.",
                    fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFF2F3A4A)
                )
            }
            item {
                BorderCard {
                    levels.forEachIndexed { i, (code, text) ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            val human = i < 4
                            val shape = RoundedCornerShape(4.dp)
                            Box(
                                (if (human) Modifier.background(Mekn.Accent, shape)
                                else Modifier.background(Mekn.Surface, shape).border(BorderStroke(1.5.dp, Mekn.Predicted), shape))
                                    .widthIn(min = 38.dp).height(22.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(code, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                    color = if (human) Color.White else Color(0xFF3D4452))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(text, fontSize = 14.sp, color = Mekn.Ink)
                        }
                    }
                }
            }
            item {
                Text(
                    "Solid badges are human evidence. Outlined badges are preclinical, predicted or traditional.",
                    fontSize = 12.sp, color = Mekn.Muted
                )
            }
        }
    }
}
