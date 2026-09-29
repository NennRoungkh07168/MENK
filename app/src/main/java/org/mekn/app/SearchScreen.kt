package org.mekn.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loading state for each data source. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data object Failed : Load<Nothing>
    data class Done<T>(val value: T) : Load<T>
}

private fun <T> loaded(v: T?): Load<T> = if (v == null) Load.Failed else Load.Done(v)

private enum class ResultTab(val label: String) {
    Summary("Summary"), Papers("Papers"), Trials("Trials"), Fda("FDA"), Chemistry("Chemistry")
}

private val Body = Color(0xFF2F3A4A)

@Composable
fun SearchScreen(query: String, onBack: () -> Unit) {
    var tab by remember(query) { mutableStateOf(ResultTab.Summary) }
    var map by remember(query) { mutableStateOf<Load<List<EvidenceCount>>>(Load.Loading) }
    var papers by remember(query) { mutableStateOf<Load<List<Paper>>>(Load.Loading) }
    var trials by remember(query) { mutableStateOf<Load<TrialSummary>>(Load.Loading) }
    var compound by remember(query) { mutableStateOf<Load<Compound?>>(Load.Loading) }
    var label by remember(query) { mutableStateOf<Load<DrugLabel?>>(Load.Loading) }
    var approvals by remember(query) { mutableStateOf<Load<FdaApprovals>>(Load.Loading) }
    var market by remember(query) { mutableStateOf<Load<Market>>(Load.Loading) }
    var recalls by remember(query) { mutableStateOf<Load<Recalls>>(Load.Loading) }

    // Sources load in parallel; each card fills in as soon as its data arrives.
    LaunchedEffect(query) { map = loaded(withContext(Dispatchers.IO) { Api.evidenceMap(query) }) }
    LaunchedEffect(query) { papers = loaded(withContext(Dispatchers.IO) { Api.papers(query) }) }
    LaunchedEffect(query) { trials = loaded(withContext(Dispatchers.IO) { Api.trials(query) }) }
    LaunchedEffect(query) {
        compound = Load.Done(withContext(Dispatchers.IO) { Api.compound(query) })
    }
    LaunchedEffect(query) {
        approvals = loaded(withContext(Dispatchers.IO) { Api.approvals(query) })
        label = Load.Done(withContext(Dispatchers.IO) { Api.label(query) })
    }
    LaunchedEffect(query) {
        market = loaded(withContext(Dispatchers.IO) { Api.market(query) })
        recalls = loaded(withContext(Dispatchers.IO) { Api.recalls(query) })
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(Mekn.Ink).statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = MAX_WIDTH).fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Mekn.Ground)
                    }
                    Column {
                        Text(query, color = Mekn.Ground, fontFamily = Mekn.Display, fontWeight = FontWeight.SemiBold,
                            fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Live results from public research and FDA databases", color = Mekn.OnInkMuted, fontSize = 12.sp)
                    }
                }
                Segmented(
                    options = ResultTab.entries.map { it.label },
                    selected = tab.ordinal,
                    onSelect = { tab = ResultTab.entries[it] },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = MAX_WIDTH).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (tab) {
                    ResultTab.Summary -> {
                        item { SummaryIntro(query) }
                        item { EvidenceMapCard(map) }
                        item { TrialCountCard(trials) }
                        item { FoundCard(compound, approvals, market, label) }
                    }
                    ResultTab.Papers -> when (val p = papers) {
                        Load.Loading -> item { LoadingRow("Searching Europe PMC and PubMed…") }
                        Load.Failed -> item { FailedRow() }
                        is Load.Done -> {
                            item { Note("The 25 most relevant papers. Levels come from publication-type tags, not a quality review.") }
                            if (p.value.isEmpty()) item { Note("No papers found. Try a different spelling or a broader term.") }
                            items(p.value) { PaperCard(it) }
                        }
                    }
                    ResultTab.Trials -> when (val t = trials) {
                        Load.Loading -> item { LoadingRow("Searching ClinicalTrials.gov…") }
                        Load.Failed -> item { FailedRow() }
                        is Load.Done -> {
                            val withResults = t.value.trials.count { it.hasResults }
                            item {
                                Note("${t.value.total} registered studies, ${t.value.recruiting} recruiting now. " +
                                    "Showing ${t.value.trials.size}; $withResults of these have posted results.")
                            }
                            items(t.value.trials) { TrialCard(it) }
                        }
                    }
                    ResultTab.Fda -> {
                        item { ApprovalCard(approvals) }
                        item { MarketCard(market) }
                        item { RecallCard(recalls) }
                        item { LabelCard(label) }
                    }
                    ResultTab.Chemistry -> {
                        item { CompoundCard(compound) }
                        item { PhysicalCard(compound) }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

// ---------- Summary ----------

@Composable
private fun SummaryIntro(query: String) {
    BorderCard {
        Title("What has been studied")
        Text(
            "How much research exists on \"$query\", what kind, and its official status. " +
                "This does not show that anything works, and it is not medical advice.",
            fontSize = 14.sp, lineHeight = 20.sp, color = Body
        )
    }
}

@Composable
private fun EvidenceMapCard(state: Load<List<EvidenceCount>>) {
    BorderCard {
        Title("Published research by study type")
        when (state) {
            Load.Loading -> LoadingRow("Counting studies…")
            Load.Failed -> FailedRow()
            is Load.Done -> {
                val max = (state.value.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
                state.value.forEach { row ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LevelBadge(row.level)
                            Spacer(Modifier.width(10.dp))
                            Text(row.label, fontSize = 14.sp, modifier = Modifier.weight(1f), color = Mekn.Ink)
                            Text("%,d".format(row.count), fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Mekn.Ink)
                        }
                        Spacer(Modifier.height(4.dp))
                        Bar(row.count.toFloat() / max, if (row.level.human) Mekn.Accent else Mekn.Predicted)
                    }
                }
                Small("Source: Europe PMC. Very recent papers may not be tagged yet.")
            }
        }
    }
}

@Composable
private fun TrialCountCard(state: Load<TrialSummary>) {
    BorderCard {
        Title("Clinical studies")
        when (state) {
            Load.Loading -> LoadingRow("Checking ClinicalTrials.gov…")
            Load.Failed -> FailedRow()
            is Load.Done -> Text(
                "${state.value.total} registered studies mention this, ${state.value.recruiting} of them recruiting now.",
                fontSize = 14.sp, lineHeight = 20.sp, color = Body
            )
        }
    }
}

@Composable
private fun FoundCard(compound: Load<Compound?>, approvals: Load<FdaApprovals>, market: Load<Market>, label: Load<DrugLabel?>) {
    BorderCard {
        Title("Official records")
        StatusLine(
            when (approvals) {
                Load.Loading -> null to "FDA approvals: checking…"
                Load.Failed -> null to "FDA approvals: couldn't connect"
                is Load.Done -> if (approvals.value.total > 0)
                    true to "FDA approvals: ${approvals.value.total} approved applications found"
                else false to "FDA approvals: not in FDA's approved-drug database"
            }
        )
        StatusLine(
            when (market) {
                Load.Loading -> null to "US market: checking…"
                Load.Failed -> null to "US market: couldn't connect"
                is Load.Done -> if (market.value.total > 0)
                    true to "US market: ${market.value.total} product listings"
                else false to "US market: no listed products"
            }
        )
        StatusLine(
            when (label) {
                Load.Loading -> null to "US drug label: checking…"
                Load.Failed -> null to "US drug label: couldn't connect"
                is Load.Done -> if (label.value != null) true to "US drug label: found" else false to "US drug label: none found"
            }
        )
        StatusLine(
            when (compound) {
                Load.Loading -> null to "PubChem: checking…"
                Load.Failed -> null to "PubChem: couldn't connect"
                is Load.Done -> compound.value?.let { true to "PubChem: chemical record found (CID ${it.cid})" }
                    ?: (false to "PubChem: no single chemical matches this name")
            }
        )
        Small("Details are in the FDA and Chemistry tabs.")
    }
}

// ---------- Papers & trials ----------

@Composable
private fun PaperCard(p: Paper) {
    val uri = LocalUriHandler.current
    var open by remember { mutableStateOf(false) }
    BorderCard {
        Row(verticalAlignment = Alignment.Top) {
            LevelBadge(p.level)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = Mekn.Ink)
                Spacer(Modifier.height(4.dp))
                Small(listOf(p.journal, p.year).filter { it.isNotBlank() }.joinToString(", "))
                if (p.authors.isNotBlank()) {
                    Text(p.authors, fontSize = 12.sp, color = Mekn.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (open && p.abstract.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(p.abstract, fontSize = 14.sp, lineHeight = 20.sp, color = Body)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (p.abstract.isNotBlank()) {
                TextButton(onClick = { open = !open }) { Text(if (open) "Hide abstract" else "Read abstract") }
            }
            TextButton(onClick = { uri.openUri(p.url) }) { Text("Open paper") }
        }
    }
}

@Composable
private fun TrialCard(t: Trial) {
    val uri = LocalUriHandler.current
    var open by remember { mutableStateOf(false) }
    BorderCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.id, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Mekn.Muted, modifier = Modifier.weight(1f))
            if (t.hasResults) Chip("Results posted", filled = true)
        }
        Text(t.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, color = Mekn.Ink)
        Spacer(Modifier.height(4.dp))
        Text(listOf(t.status, t.phase).filter { it.isNotBlank() }.joinToString(", "), fontSize = 13.sp, color = Body)
        if (t.enrollment.isNotBlank()) Small(t.enrollment)
        if (t.conditions.isNotBlank()) Small("Conditions: ${t.conditions}")
        if (t.whyStopped.isNotBlank()) {
            Text("Stopped early: ${t.whyStopped}", fontSize = 13.sp, color = Mekn.Preclinical)
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            if (t.primaryOutcome.isNotBlank()) KeyValueText("Main question measured", t.primaryOutcome)
            if (t.completion.isNotBlank()) KeyValueText("Completion date", t.completion)
            t.outcome?.let { o ->
                Spacer(Modifier.height(6.dp))
                Text("Posted result", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Mekn.Ink)
                Text(o.title, fontSize = 13.sp, color = Body)
                o.values.forEach { Text("• $it", fontSize = 13.sp, color = Body) }
                if (o.pValue.isNotBlank()) Small("Reported p-value: ${o.pValue}")
            }
            if (t.safety.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Serious adverse events", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Mekn.Preclinical)
                t.safety.forEach { Text("• $it", fontSize = 13.sp, color = Body) }
            }
            if (t.hasResults) Small("Results are as reported by the study team to ClinicalTrials.gov, not independently checked.")
            if (!t.hasResults) Small("No results posted yet.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { open = !open }) { Text(if (open) "Hide details" else if (t.hasResults) "See results" else "Details") }
            TextButton(onClick = { uri.openUri(t.url) }) { Text("Open study") }
        }
    }
}

// ---------- FDA tab ----------

@Composable
private fun ApprovalCard(state: Load<FdaApprovals>) {
    BorderCard {
        Title("FDA approval")
        when (state) {
            Load.Loading -> LoadingRow("Checking Drugs@FDA…")
            Load.Failed -> FailedRow()
            is Load.Done -> {
                val a = state.value
                if (a.total == 0) {
                    StatusLine(false to "Not in FDA's approved-drug database")
                    Text(
                        "This can mean it isn't an FDA-approved medicine (for example herbs, supplements and research " +
                            "compounds), that it's sold under an over-the-counter monograph without an individual approval, " +
                            "or that it's listed under a different name.",
                        fontSize = 14.sp, lineHeight = 20.sp, color = Body
                    )
                } else {
                    StatusLine(true to "${a.total} approved applications")
                    a.items.forEach { ap ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(listOf(ap.brand, ap.appNo).filter { it.isNotBlank() }.joinToString(" · "),
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Mekn.Ink)
                            Small(listOf(ap.sponsor, ap.form).filter { it.isNotBlank() }.joinToString(", "))
                            Small(listOf(
                                appType(ap.appNo),
                                ap.marketing,
                                if (ap.approvedOn.isNotBlank()) "first approved ${ap.approvedOn}" else ""
                            ).filter { it.isNotBlank() }.joinToString(", "))
                        }
                    }
                    if (a.total > a.items.size) Small("Showing ${a.items.size} of ${a.total}.")
                }
                Small("Source: Drugs@FDA via openFDA. Approval rules differ in India, Germany and the EU.")
            }
        }
    }
}

@Composable
private fun MarketCard(state: Load<Market>) {
    BorderCard {
        Title("Manufacturing and products")
        when (state) {
            Load.Loading -> LoadingRow("Checking the FDA product directory…")
            Load.Failed -> FailedRow()
            is Load.Done -> {
                val m = state.value
                if (m.total == 0) {
                    StatusLine(false to "No products listed in the US drug directory")
                } else {
                    Text("${m.total} product listings in the US", fontSize = 14.sp, color = Body)
                    Spacer(Modifier.height(8.dp))
                    SubTitle("Approval route of listed products")
                    m.categories.forEach { c ->
                        val (text, approved) = category(c.term)
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Chip(if (approved) "Approved" else "Not approved", filled = approved)
                            Spacer(Modifier.width(8.dp))
                            Text(text, fontSize = 13.sp, color = Mekn.Ink, modifier = Modifier.weight(1f))
                            Text("${c.count}", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Mekn.Ink)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SubTitle("Companies with the most listings")
                    m.makers.forEach { KeyCount(it.term, it.count) }
                    Spacer(Modifier.height(8.dp))
                    SubTitle("Dosage forms")
                    m.forms.forEach { KeyCount(it.term.lowercase().replaceFirstChar { c -> c.uppercase() }, it.count) }
                }
                Small("Source: FDA National Drug Code directory via openFDA. Labelers include makers, packagers and distributors.")
            }
        }
    }
}

@Composable
private fun RecallCard(state: Load<Recalls>) {
    BorderCard {
        Title("Manufacturing recalls")
        when (state) {
            Load.Loading -> LoadingRow("Checking FDA recall reports…")
            Load.Failed -> FailedRow()
            is Load.Done -> {
                val r = state.value
                if (r.total == 0) {
                    StatusLine(true to "No FDA recall reports found for this name")
                } else {
                    Text("${r.total} recall reports, newest first", fontSize = 14.sp, color = Body)
                    r.items.forEach { rc ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(listOf(rc.firm, rc.date).filter { it.isNotBlank() }.joinToString(" · "),
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Mekn.Ink)
                            Small(listOf(rc.classification, rc.status).filter { it.isNotBlank() }.joinToString(", "))
                            Text(rc.reason, fontSize = 13.sp, lineHeight = 18.sp, color = Body)
                        }
                    }
                }
                Small("Class I is the most serious, Class III the least. Source: FDA enforcement reports via openFDA.")
            }
        }
    }
}

@Composable
private fun LabelCard(state: Load<DrugLabel?>) {
    BorderCard {
        Title("Official US label: uses, safety and interactions")
        when (state) {
            Load.Loading -> LoadingRow("Checking openFDA…")
            Load.Failed -> FailedRow()
            is Load.Done -> {
                val l = state.value
                if (l == null) {
                    StatusLine(false to "No US label found")
                    Text("Herbs, supplements and research compounds usually don't have one.", fontSize = 14.sp, color = Body)
                } else {
                    if (l.brand.isNotBlank()) KeyValueText("Example product", l.brand)
                    if (l.generic.isNotBlank()) KeyValueText("Generic name", l.generic)
                    if (l.manufacturer.isNotBlank()) KeyValueText("Manufacturer", l.manufacturer)
                    LabelSection("Approved uses", l.uses, Mekn.Ink)
                    LabelSection("Drug interactions", l.interactions, Mekn.Preclinical)
                    LabelSection("Do not use / contraindications", l.contraindications, Mekn.Preclinical)
                    LabelSection("Warnings", l.warnings, Mekn.Preclinical)
                    Small("One example label from openFDA. Other products and labels in India may differ. Ask a pharmacist about your own medicines.")
                }
            }
        }
    }
}

@Composable
private fun LabelSection(title: String, text: String, color: Color) {
    if (text.isBlank()) return
    Spacer(Modifier.height(8.dp))
    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = color)
    Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = Body)
}

private fun appType(appNo: String): String = when {
    appNo.startsWith("NDA") -> "brand-name approval (NDA)"
    appNo.startsWith("ANDA") -> "generic approval (ANDA)"
    appNo.startsWith("BLA") -> "biologic approval (BLA)"
    else -> ""
}

/** Plain-language marketing category, and whether it means FDA approval. */
private fun category(term: String): Pair<String, Boolean> {
    val t = term.uppercase()
    return when {
        t == "NDA" -> "Brand-name drug (NDA)" to true
        t == "ANDA" -> "Generic drug (ANDA)" to true
        t == "BLA" -> "Biologic (BLA)" to true
        "AUTHORIZED GENERIC" in t -> "Authorized generic" to true
        "MONOGRAPH" in t || t.startsWith("OTC") -> "Over-the-counter monograph, no individual approval" to false
        "UNAPPROVED" in t -> "Unapproved drug" to false
        "BULK" in t -> "Bulk ingredient for manufacturing" to false
        "EMERGENCY" in t -> "Emergency use authorization" to false
        else -> term.lowercase().replaceFirstChar { it.uppercase() } to false
    }
}

// ---------- Chemistry tab ----------

@Composable
private fun CompoundCard(state: Load<Compound?>) {
    val uri = LocalUriHandler.current
    BorderCard {
        Title("Chemical identity")
        when (state) {
            Load.Loading -> LoadingRow("Checking PubChem…")
            Load.Failed -> FailedRow()
            is Load.Done -> {
                val c = state.value
                if (c == null) {
                    Text("No single chemical matches this name. Try a medicine or compound name, like \"aspirin\" or \"curcumin\".",
                        fontSize = 14.sp, color = Body)
                } else {
                    StructureImage(c.cid, c.formula)
                    KeyValue("Formula", c.formula)
                    KeyValue("Molecular weight", "${c.weight} g/mol")
                    KeyValue("PubChem CID", c.cid)
                    KeyValue("InChIKey", c.inchikey)
                    if (c.iupac.isNotBlank()) KeyValue("IUPAC name", c.iupac)
                    TextButton(onClick = { uri.openUri(c.url) }) { Text("Open in PubChem") }
                }
            }
        }
    }
}

/** PubChem's 2D drawing of the molecule, loaded on demand. */
@Composable
private fun StructureImage(cid: String, formula: String) {
    var bmp by remember(cid) { mutableStateOf<ImageBitmap?>(null) }
    var tried by remember(cid) { mutableStateOf(false) }
    LaunchedEffect(cid) {
        bmp = withContext(Dispatchers.IO) {
            Api.structurePng(cid)?.let { b -> BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }
        }
        tried = true
    }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 180.dp).background(Color.White, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        when {
            b != null -> Image(bitmap = b, contentDescription = "2D structure, $formula",
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp))
            !tried -> LoadingRow("Drawing structure…")
            else -> Text("Structure image unavailable", fontSize = 13.sp, color = Mekn.Muted)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PhysicalCard(state: Load<Compound?>) {
    if (state !is Load.Done) return
    val c = state.value ?: return
    BorderCard {
        Title("Physical and chemical properties")
        PropertyRow("XLogP", c.xlogp, "Fat vs water preference. Higher means it dissolves better in fats.")
        PropertyRow("Polar surface area", c.tpsa.let { if (it.isBlank()) it else "$it Å²" },
            "Linked to how easily it crosses cell membranes.")
        PropertyRow("H-bond donors", c.donors, "Groups that can give a hydrogen bond.")
        PropertyRow("H-bond acceptors", c.acceptors, "Groups that can accept a hydrogen bond.")
        PropertyRow("Rotatable bonds", c.rotatable, "How flexible the molecule is.")
        PropertyRow("Heavy atoms", c.heavyAtoms, "Atoms other than hydrogen.")
        PropertyRow("Exact mass", c.exactMass, "Mass of the most common isotopes, used in mass spectrometry.")
        PropertyRow("Complexity", c.complexity, "PubChem's score for how complex the structure is.")
        Small("Computed by PubChem. These are predictions from the structure, not lab measurements.")
    }
}

@Composable
private fun PropertyRow(name: String, value: String, meaning: String) {
    if (value.isBlank()) return
    Column(Modifier.padding(vertical = 5.dp)) {
        Row {
            Text(name, fontSize = 14.sp, color = Mekn.Ink, modifier = Modifier.weight(1f))
            Text(value, fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Mekn.Ink)
        }
        Text(meaning, fontSize = 12.sp, color = Mekn.Muted)
    }
}

// ---------- Small shared pieces ----------

@Composable
fun LevelBadge(level: Level) {
    val shape = RoundedCornerShape(4.dp)
    val mod = if (level.human) Modifier.background(Mekn.Accent, shape)
    else Modifier.background(Mekn.Surface, shape).border(BorderStroke(1.5.dp, Mekn.Predicted), shape)
    Box(mod.widthIn(min = 38.dp).height(22.dp).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Text(level.code, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            color = if (level.human) Color.White else Color(0xFF3D4452))
    }
}

@Composable
private fun Chip(text: String, filled: Boolean) {
    val shape = RoundedCornerShape(4.dp)
    val mod = if (filled) Modifier.background(Mekn.Accent, shape)
    else Modifier.background(Mekn.Surface, shape).border(BorderStroke(1.5.dp, Mekn.Predicted), shape)
    Box(mod.padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = if (filled) Color.White else Color(0xFF3D4452), maxLines = 1)
    }
}

/** A found (true), not-found (false) or pending (null) status line. */
@Composable
private fun StatusLine(state: Pair<Boolean?, String>) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        val color = when (state.first) { true -> Mekn.Accent; false -> Mekn.Predicted; null -> Color(0xFFC9C4B8) }
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(5.dp)))
        Spacer(Modifier.width(10.dp))
        Text(state.second, fontSize = 14.sp, color = Body)
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0xFFECE9E2), RoundedCornerShape(3.dp))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).background(color, RoundedCornerShape(3.dp)))
    }
}

@Composable
private fun Title(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Mekn.Ink)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SubTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Mekn.Muted)
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun Small(text: String) {
    if (text.isBlank()) return
    Text(text, fontSize = 12.sp, lineHeight = 16.sp, color = Mekn.Muted)
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(key, fontSize = 13.sp, color = Mekn.Muted, modifier = Modifier.width(130.dp))
        Text(value, fontSize = 13.sp, color = Mekn.Ink, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun KeyValueText(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(key, fontSize = 13.sp, color = Mekn.Muted, modifier = Modifier.width(130.dp))
        Text(value, fontSize = 13.sp, color = Mekn.Ink, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun KeyCount(key: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(key, fontSize = 13.sp, color = Mekn.Ink, modifier = Modifier.weight(1f))
        Text("$count", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Mekn.Ink)
    }
}

@Composable
private fun LoadingRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Mekn.Accent)
        Text(text, fontSize = 14.sp, color = Mekn.Muted)
    }
}

@Composable
private fun FailedRow() {
    Text("Couldn't connect. Check your internet connection and search again.", fontSize = 14.sp, color = Mekn.Preclinical)
}

@Composable
private fun Note(text: String) {
    Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = Mekn.Muted)
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(Mekn.SegmentTrack, RoundedCornerShape(10.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp)
                    .background(if (on) Mekn.Surface else Color.Transparent, RoundedCornerShape(7.dp))
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) Mekn.Ink else Color(0xFFD9DEE6),
                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}
