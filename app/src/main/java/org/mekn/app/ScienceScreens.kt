package org.mekn.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow

private val BodyText = Color(0xFF2F3A4A)

// =====================================================================
// Shared page header
// =====================================================================

@Composable
fun PageHeader(title: String, subtitle: String, onBack: (() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().background(Mekn.Ink).statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.widthIn(max = MAX_WIDTH).fillMaxWidth()
                .padding(start = if (onBack != null) 8.dp else 20.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Mekn.Ground)
                }
            }
            Column {
                Text(title, color = Mekn.Ground, fontFamily = Mekn.Display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp)
                Text(subtitle, color = Mekn.OnInkMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CenteredList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = MAX_WIDTH).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun HubCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick, color = Mekn.Surface, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Mekn.Line), modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, contentDescription = null, tint = Mekn.Accent, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Mekn.Ink)
                Text(subtitle, fontSize = 13.sp, lineHeight = 18.sp, color = Mekn.Muted)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = Mekn.Muted)
        }
    }
}

@Composable
private fun LookupField(label: String, placeholder: String, button: String, onGo: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    BorderCard {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Mekn.Ink)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text(placeholder) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { if (text.isNotBlank()) onGo(text.trim()) },
                onDone = { if (text.isNotBlank()) onGo(text.trim()) },
                onGo = { if (text.isNotBlank()) onGo(text.trim()) }
            )
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { if (text.isNotBlank()) onGo(text.trim()) }, enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp), shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Mekn.Accent)
        ) { Text(button, fontWeight = FontWeight.Bold) }
    }
}

// =====================================================================
// Explore
// =====================================================================

/** Section codes: null = hub, "drugs" = drug index, "lib:<type>" = a library section. */
@Composable
fun ExploreScreen(section: String?, onSection: (String?) -> Unit, onSearch: (String) -> Unit, onLab: () -> Unit) {
    when {
        section == "drugs" -> DrugIndexScreen(onSearch = onSearch, onBack = { onSection(null) })
        section == "pack" -> OfflinePackScreen(onSearch = onSearch, onBack = { onSection(null) })
        section != null && section.startsWith("lib:") -> {
            val kind = LibKind.entries.firstOrNull { it.type == section.removePrefix("lib:") } ?: LibKind.Medicines
            LibraryScreen(kind = kind, onSearch = onSearch, onBack = { onSection(null) })
        }
        else -> ExploreHub(onSection, onSearch, onLab)
    }
}

@Composable
private fun ExploreHub(onSection: (String?) -> Unit, onSearch: (String) -> Unit, onLab: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Explore", "Medicine, chemistry, biology, plants, companies and devices")
        CenteredList {
            item {
                HubCard("Drug index", "${DrugIndexData.size} generic medicines grouped by pharmacology family",
                    Icons.Outlined.Medication) { onSection("drugs") }
            }
            item {
                val n = OfflinePack.topics.size
                HubCard(
                    "Offline research pack",
                    when {
                        OfflinePack.installing -> "Unpacking research onto this device…"
                        n > 0 -> "$n topics with papers, trials, FDA records and side effects, all without internet"
                        else -> "Not included in this build yet. See README: \"Offline research pack\""
                    },
                    Icons.Outlined.CloudOff
                ) { onSection("pack") }
            }
            item { SectionLabel("Library · works offline") }
            val icons = mapOf(
                LibKind.Medicines to Icons.Outlined.Medication,
                LibKind.Diseases to Icons.Outlined.MonitorHeart,
                LibKind.Herbs to Icons.Outlined.Eco,
                LibKind.Vegetables to Icons.Outlined.Restaurant,
                LibKind.Extraction to Icons.Outlined.Opacity,
                LibKind.Biochem to Icons.Outlined.Biotech,
                LibKind.Companies to Icons.Outlined.Factory,
                LibKind.Institutions to Icons.Outlined.AccountBalance,
                LibKind.Universities to Icons.Outlined.School,
                LibKind.Hospitals to Icons.Outlined.LocalHospital,
                LibKind.Regulators to Icons.Outlined.Gavel,
                LibKind.Devices to Icons.Outlined.Devices,
                LibKind.Guides to Icons.Outlined.Description
            )
            items(LibKind.entries.toList()) { k ->
                HubCard(k.label, k.blurb, icons[k] ?: Icons.Outlined.Info) { onSection("lib:${k.type}") }
            }
            item { SectionLabel("Chemistry & molecules") }
            item {
                LookupField(
                    label = "Look up a compound",
                    placeholder = "e.g. caffeine, curcumin, ethanol",
                    button = "Show formula, 2D structure and properties",
                    onGo = onSearch
                )
            }
            item { SectionLabel("Tools") }
            item {
                HubCard("Simulation Lab", "Educational models: reaction kinetics, receptor binding, dose-response",
                    Icons.Outlined.Science, onLab)
            }
            item { PlannedCard() }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
        color = Mekn.Muted, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun PlannedCard() {
    Surface(
        color = Color(0xFFECE9E2), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Coming in later versions", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Mekn.Ink)
            Text(
                "Camera scanning and barcode lookup · periodic table · 3D molecule viewer · drawing a structure " +
                    "to search · reaction viewer · live data from regulators outside the US · larger datasets from the RENK server.",
                fontSize = 13.sp, lineHeight = 18.sp, color = BodyText
            )
        }
    }
}

// =====================================================================
// Drug index
// =====================================================================

private data class DrugIndex(val name: String, val family: String, val search: String = name)

private val DrugIndexData = listOf(
    DrugIndex("Aspirin", "Salicylate / antiplatelet"),
    DrugIndex("Paracetamol (acetaminophen)", "Analgesic / antipyretic", "acetaminophen"),
    DrugIndex("Ibuprofen", "NSAID"),
    DrugIndex("Naproxen", "NSAID"),
    DrugIndex("Diclofenac", "NSAID"),
    DrugIndex("Amoxicillin", "Penicillin antibiotic"),
    DrugIndex("Amoxicillin / clavulanate", "Penicillin + beta-lactamase inhibitor", "amoxicillin clavulanate"),
    DrugIndex("Azithromycin", "Macrolide antibiotic"),
    DrugIndex("Doxycycline", "Tetracycline antibiotic"),
    DrugIndex("Ciprofloxacin", "Fluoroquinolone antibiotic"),
    DrugIndex("Metformin", "Biguanide"),
    DrugIndex("Glipizide", "Sulfonylurea"),
    DrugIndex("Insulin", "Peptide hormone / biologic"),
    DrugIndex("Atorvastatin", "Statin (HMG-CoA reductase inhibitor)"),
    DrugIndex("Rosuvastatin", "Statin (HMG-CoA reductase inhibitor)"),
    DrugIndex("Amlodipine", "Calcium channel blocker"),
    DrugIndex("Lisinopril", "ACE inhibitor"),
    DrugIndex("Losartan", "Angiotensin II receptor blocker"),
    DrugIndex("Hydrochlorothiazide", "Thiazide diuretic"),
    DrugIndex("Furosemide", "Loop diuretic"),
    DrugIndex("Omeprazole", "Proton pump inhibitor"),
    DrugIndex("Pantoprazole", "Proton pump inhibitor"),
    DrugIndex("Famotidine", "H2 receptor antagonist"),
    DrugIndex("Ondansetron", "5-HT3 receptor antagonist"),
    DrugIndex("Fluconazole", "Triazole antifungal"),
    DrugIndex("Aciclovir", "Nucleoside analogue antiviral", "acyclovir"),
    DrugIndex("Oseltamivir", "Neuraminidase inhibitor"),
    DrugIndex("Warfarin", "Vitamin K antagonist"),
    DrugIndex("Apixaban", "Direct factor Xa inhibitor"),
    DrugIndex("Clopidogrel", "P2Y12 inhibitor (antiplatelet)"),
    DrugIndex("Heparin", "Anticoagulant"),
    DrugIndex("Prednisone", "Glucocorticoid"),
    DrugIndex("Dexamethasone", "Glucocorticoid"),
    DrugIndex("Sertraline", "SSRI antidepressant"),
    DrugIndex("Fluoxetine", "SSRI antidepressant"),
    DrugIndex("Escitalopram", "SSRI antidepressant"),
    DrugIndex("Diazepam", "Benzodiazepine"),
    DrugIndex("Levothyroxine", "Thyroid hormone"),
    DrugIndex("Salbutamol (albuterol)", "Short-acting beta-2 agonist", "albuterol"),
    DrugIndex("Montelukast", "Leukotriene receptor antagonist"),
    DrugIndex("Loratadine", "Antihistamine (H1)"),
    DrugIndex("Cetirizine", "Antihistamine (H1)"),
    DrugIndex("Morphine", "Opioid analgesic"),
    DrugIndex("Tramadol", "Opioid analgesic"),
    DrugIndex("Lidocaine", "Local anaesthetic"),
    DrugIndex("Propofol", "General anaesthetic"),
    DrugIndex("Methotrexate", "Antimetabolite / immunomodulator"),
    DrugIndex("Hydroxychloroquine", "4-aminoquinoline"),
    DrugIndex("Tacrolimus", "Calcineurin inhibitor"),
    DrugIndex("Adalimumab", "Monoclonal antibody (TNF inhibitor)"),
    DrugIndex("Artemether / lumefantrine", "Artemisinin combination therapy", "artemether lumefantrine")
)

@Composable
private fun DrugIndexScreen(onSearch: (String) -> Unit, onBack: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("") }
    val shown = remember(filter) {
        DrugIndexData.filter { it.name.contains(filter, true) || it.family.contains(filter, true) }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Drug index", "${DrugIndexData.size} generic medicines · tap one for live research", onBack)
        CenteredList {
            item {
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Filter by name or family, e.g. statin") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }
                )
            }
            if (shown.isEmpty()) item { Text("No match in the index. Try the search on Home.", color = Mekn.Muted) }
            items(shown, key = { it.name }) { d ->
                Surface(
                    onClick = { onSearch(d.search) }, color = Mekn.Surface, shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Mekn.Line), modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name, fontWeight = FontWeight.SemiBold, color = Mekn.Ink)
                            Text(d.family, fontSize = 12.sp, color = Mekn.Muted)
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = Mekn.Accent)
                    }
                }
            }
            item {
                Text("For orientation only. Check each medicine against current official sources.",
                    fontSize = 12.sp, color = Mekn.Muted)
            }
        }
    }
}

// =====================================================================
// Offline research pack
// =====================================================================

@Composable
private fun OfflinePackScreen(onSearch: (String) -> Unit, onBack: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("") }
    val all = OfflinePack.topics
    val shown = remember(filter, all) { all.filter { it.contains(filter, true) } }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Offline research pack", "${all.size} topics stored on this device · no internet needed", onBack)
        CenteredList {
            if (all.isEmpty()) {
                item {
                    BorderCard {
                        Text(if (OfflinePack.installing) "Unpacking the research pack…" else "No offline pack in this build",
                            fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Mekn.Ink)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "The pack is built on GitHub: run the \"Build offline research pack\" workflow " +
                                "(or bash termux/build-pack.sh), then install the new app. Every topic in it then works " +
                                "with papers, trials, FDA records, side effects and chemistry, without internet.",
                            fontSize = 14.sp, lineHeight = 20.sp, color = BodyText
                        )
                    }
                }
            } else {
                item {
                    OutlinedTextField(
                        value = filter, onValueChange = { filter = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Filter topics") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }
                    )
                }
                item {
                    Text("Tap a topic to open its full results offline. Online, results refresh automatically. " +
                        "Country filters need internet; offline shows worldwide results.",
                        fontSize = 12.sp, lineHeight = 16.sp, color = Mekn.Muted)
                }
                items(shown, key = { it }) { t ->
                    Surface(
                        onClick = { onSearch(t) }, color = Mekn.Surface, shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Mekn.Line), modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.OfflinePin, contentDescription = null, tint = Mekn.Accent)
                            Spacer(Modifier.width(12.dp))
                            Text(t, fontWeight = FontWeight.SemiBold, color = Mekn.Ink, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = Mekn.Muted)
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// Research center
// =====================================================================

@Composable
fun ResearchScreen(onSearch: (String) -> Unit, onExplore: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Research center", "Papers and trials worldwide, medicine records and chemistry")
        CenteredList {
            item {
                LookupField(
                    label = "Search all research sources",
                    placeholder = "A disease, medicine, herb or compound",
                    button = "Search research",
                    onGo = onSearch
                )
            }
            item {
                HubCard("Scientific papers", "Europe PMC and PubMed, with study-type badges and abstracts",
                    Icons.AutoMirrored.Outlined.MenuBook) { onSearch("type 2 diabetes") }
            }
            item {
                HubCard("Clinical trials", "ClinicalTrials.gov, with posted results and serious adverse events",
                    Icons.Outlined.Groups) { onSearch("metformin") }
            }
            item {
                HubCard("Medicine research", "Side effects, prescribing information, approvals, recalls, interactions",
                    Icons.Outlined.Medication) { onSearch("aspirin") }
            }
            item {
                HubCard("Chemistry research", "PubChem identity, 2D structure and computed properties",
                    Icons.Outlined.Science) { onSearch("caffeine") }
            }
            item {
                HubCard("Pharma companies", "Germany, India, China, Spain, Russia",
                    Icons.Outlined.Factory) { onExplore("lib:company") }
            }
            item {
                HubCard("Research institutes & labs", "Germany, India, China, Russia, Spain, Cambodia",
                    Icons.Outlined.AccountBalance) { onExplore("lib:institution") }
            }
            item {
                HubCard("Universities", "Medicine and chemistry universities by country",
                    Icons.Outlined.School) { onExplore("lib:university") }
            }
            item {
                HubCard("Hospitals & clinics", "AIIMS, Charité, Peking Union, Hospital Clínic and more",
                    Icons.Outlined.LocalHospital) { onExplore("lib:hospital") }
            }
            item {
                HubCard("Regulators & health systems", "WHO, EMA, FDA, NMPA, CDSCO, Medicare, PM-JAY and more",
                    Icons.Outlined.Gavel) { onExplore("lib:regulator") }
            }
            item {
                BorderCard {
                    Text("Evidence principle", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Mekn.Ink)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "RENK shows the evidence behind a claim instead of one unexplained score: source, study type, " +
                            "population, limitations and date stay visible.",
                        fontSize = 14.sp, lineHeight = 20.sp, color = BodyText
                    )
                }
            }
        }
    }
}

// =====================================================================
// Simulation Lab (educational models only)
// =====================================================================

@Composable
fun SimulationLabScreen() {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(Mekn.Ink).statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = MAX_WIDTH).fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Simulation Lab", color = Mekn.Ground, fontFamily = Mekn.Display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp)
                Text("Educational models only. Not doses, diagnosis or treatment advice.", color = Mekn.OnInkMuted, fontSize = 12.sp)
                Segmented(listOf("Kinetics", "Binding", "Dose-response"), tab, { tab = it })
            }
        }
        when (tab) {
            0 -> KineticsSim()
            1 -> BindingSim()
            else -> DoseResponseSim()
        }
    }
}

@Composable
private fun KineticsSim() {
    var c0 by remember { mutableFloatStateOf(10f) }
    var k by remember { mutableFloatStateOf(0.5f) }
    var t by remember { mutableFloatStateOf(2f) }
    val halfLife = ln(2.0) / k
    val remaining = c0 * exp(-k * t.toDouble())
    CenteredList {
        item {
            SimCard("First-order decay",
                "Many reactions, and the breakdown of many substances, lose a fixed fraction per unit of time. " +
                    "The half-life is the time for the amount to halve. Units here are relative.")
        }
        item { Plot(0.0, 10.0, false, t.toDouble(), c0.toDouble(), "time", "amount") { x -> c0 * exp(-k * x) } }
        item { SliderCard("Starting amount C₀", c0, 1f..20f) { c0 = it } }
        item { SliderCard("Rate constant k", k, 0.05f..2f) { k = it } }
        item { SliderCard("Time t", t, 0f..10f) { t = it } }
        item { ResultCard("Amount remaining", "%.2f".format(remaining), "C(t) = C₀ · e^(−k·t)") }
        item { ResultCard("Half-life", "%.2f".format(halfLife), "t½ = ln 2 / k") }
    }
}

@Composable
private fun BindingSim() {
    var conc by remember { mutableFloatStateOf(1f) }
    var kd by remember { mutableFloatStateOf(1f) }
    val occupancy = conc / (conc + kd)
    CenteredList {
        item {
            SimCard("Receptor occupancy",
                "How much of a receptor is bound depends on the concentration of the molecule and its affinity (Kd). " +
                    "At a concentration equal to Kd, half the receptors are occupied.")
        }
        item { Plot(0.01, 100.0, true, conc.toDouble(), 1.0, "concentration (log scale)", "occupancy") { x -> x / (x + kd) } }
        item { SliderCard("Concentration (relative)", conc, 0.01f..100f) { conc = it } }
        item { SliderCard("Kd (relative)", kd, 0.01f..100f) { kd = it } }
        item { ResultCard("Occupancy", "%.1f%%".format(occupancy * 100), "occupancy = C / (C + Kd)") }
    }
}

@Composable
private fun DoseResponseSim() {
    var conc by remember { mutableFloatStateOf(1f) }
    var ec50 by remember { mutableFloatStateOf(1f) }
    var hill by remember { mutableFloatStateOf(1f) }
    val response = conc.pow(hill) / (conc.pow(hill) + ec50.pow(hill))
    CenteredList {
        item {
            SimCard("Hill dose-response curve",
                "EC50 is the concentration giving half the maximum effect. The Hill coefficient sets how steep the curve is. " +
                    "Values are dimensionless, not patient doses.")
        }
        item {
            Plot(0.01, 100.0, true, conc.toDouble(), 1.0, "concentration (log scale)", "response") { x ->
                x.pow(hill.toDouble()) / (x.pow(hill.toDouble()) + ec50.toDouble().pow(hill.toDouble()))
            }
        }
        item { SliderCard("Concentration (relative)", conc, 0.01f..100f) { conc = it } }
        item { SliderCard("EC50 (relative)", ec50, 0.01f..100f) { ec50 = it } }
        item { SliderCard("Hill coefficient", hill, 0.25f..4f) { hill = it } }
        item { ResultCard("Response (% of maximum)", "%.1f%%".format(response * 100), "E/Emax = Cⁿ / (Cⁿ + EC50ⁿ)") }
    }
}

/** A simple line plot with a marker at the current x value. */
@Composable
private fun Plot(
    xMin: Double, xMax: Double, logX: Boolean, marker: Double, yMax: Double,
    xLabel: String, yLabel: String, f: (Double) -> Double
) {
    BorderCard {
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            val w = size.width
            val h = size.height
            fun px(x: Double): Float {
                val t = if (logX) (log10(x) - log10(xMin)) / (log10(xMax) - log10(xMin)) else (x - xMin) / (xMax - xMin)
                return (t.coerceIn(0.0, 1.0) * w).toFloat()
            }
            fun py(y: Double): Float = (h - (y / yMax).coerceIn(0.0, 1.0) * h).toFloat()
            fun xAt(i: Int, n: Int): Double {
                val t = i.toDouble() / n
                return if (logX) 10.0.pow(log10(xMin) + t * (log10(xMax) - log10(xMin))) else xMin + t * (xMax - xMin)
            }
            val grid = Color(0xFFECE9E2)
            for (g in 1..3) drawLine(grid, Offset(0f, h * g / 4f), Offset(w, h * g / 4f), strokeWidth = 2f)
            drawLine(Color(0xFFB9C0CA), Offset(0f, h), Offset(w, h), strokeWidth = 3f)
            drawLine(Color(0xFFB9C0CA), Offset(0f, 0f), Offset(0f, h), strokeWidth = 3f)
            val n = 120
            val path = Path()
            for (i in 0..n) {
                val x = xAt(i, n)
                val p = Offset(px(x), py(f(x)))
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, Mekn.Accent, style = Stroke(width = 5f))
            val m = Offset(px(marker), py(f(marker)))
            drawCircle(Color(0xFFF2A65A), radius = 11f, center = m)
            drawCircle(Color.White, radius = 5f, center = m)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("↑ $yLabel", fontSize = 12.sp, color = Mekn.Muted, modifier = Modifier.weight(1f))
            Text("$xLabel →", fontSize = 12.sp, color = Mekn.Muted)
        }
    }
}

@Composable
private fun SliderCard(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    BorderCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.SemiBold, color = Mekn.Ink, modifier = Modifier.weight(1f))
            Text("%.2f".format(value), fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = Mekn.Ink)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SimCard(title: String, body: String) {
    BorderCard {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Mekn.Ink)
        Spacer(Modifier.height(5.dp))
        Text(body, fontSize = 14.sp, lineHeight = 20.sp, color = BodyText)
    }
}

@Composable
private fun ResultCard(title: String, value: String, formula: String) {
    Surface(color = Color(0xFFE6ECF4), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Mekn.Ink)
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Mekn.Accent)
            Text(formula, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Mekn.Muted)
        }
    }
}
