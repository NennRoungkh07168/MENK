package org.mekn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Content never gets wider than this, so it reads well on tablets. */
val MAX_WIDTH = 760.dp

// ---------- Design tokens (from Design.pdf) ----------
object Mekn {
    val Ink = Color(0xFF16202E)        // header background, main text
    val Ground = Color(0xFFF4F2EC)     // page background
    val Surface = Color(0xFFFFFFFF)
    val Line = Color(0xFFDAD6CC)
    val Muted = Color(0xFF4A5566)
    val Accent = Color(0xFF1F4E8C)     // buttons, verified badge
    val SegmentTrack = Color(0xFF2A3547)
    val OnInkMuted = Color(0xFFC9D0DA)
    val Preclinical = Color(0xFFB4560F)
    val Predicted = Color(0xFF5B6270)
    val Display = FontFamily.Serif     // swap for Newsreader later
}

enum class Screen(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Explore("Explore", Icons.Outlined.Layers),
    Research("Research", Icons.AutoMirrored.Outlined.MenuBook),
    Lab("Lab", Icons.Outlined.Science),
    Evidence("Evidence", Icons.Outlined.VerifiedUser)
}

enum class Mode { Patient, Researcher }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Api.init(filesDir)   // saved copies of searches, so they work offline
        setContent { MeknApp() }
    }
}

@Composable
fun MeknApp() {
    var tab by rememberSaveable { mutableStateOf(Screen.Home) }
    var showScan by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf<String?>(null) }
    var exploreSection by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = search != null || showScan) {
        search = null
        showScan = false
    }
    BackHandler(enabled = search == null && !showScan && tab == Screen.Explore && exploreSection != null) {
        exploreSection = null
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Mekn.Accent, background = Mekn.Ground)) {
        Scaffold(
            containerColor = Mekn.Ground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar(containerColor = Mekn.Surface) {
                    Screen.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t && !showScan && search == null,
                            onClick = { showScan = false; search = null; if (t == Screen.Explore) exploreSection = null; tab = t },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Mekn.Accent,
                                selectedTextColor = Mekn.Accent,
                                unselectedIconColor = Mekn.Muted,
                                unselectedTextColor = Mekn.Muted,
                                indicatorColor = Color(0xFFE6ECF4)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                val q = search
                when {
                    q != null -> SearchScreen(query = q, onBack = { search = null })
                    showScan -> PlaceholderScreen("Scan to identify", "Camera scanning with barcode recognition is planned for a later version.")
                    tab == Screen.Home -> HomeScreen(
                        onScan = { showScan = true },
                        onOpen = { tab = it },
                        onExplore = { exploreSection = it; tab = Screen.Explore },
                        onSearch = { search = it.trim() }
                    )
                    tab == Screen.Explore -> ExploreScreen(
                        section = exploreSection,
                        onSection = { exploreSection = it },
                        onSearch = { search = it },
                        onLab = { tab = Screen.Lab }
                    )
                    tab == Screen.Research -> ResearchScreen(
                        onSearch = { search = it },
                        onExplore = { exploreSection = it; tab = Screen.Explore }
                    )
                    tab == Screen.Lab -> SimulationLabScreen()
                    tab == Screen.Evidence -> EvidenceScreen()
                }
            }
        }
    }
}

// ---------- Home ----------
@Composable
fun HomeScreen(onScan: () -> Unit, onOpen: (Screen) -> Unit, onExplore: (String) -> Unit, onSearch: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(Mode.Patient) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Header
        Column(
            Modifier.fillMaxWidth().background(Mekn.Ink)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .wrapContentWidth()
                .widthIn(max = MAX_WIDTH),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text("RENK", color = Mekn.Ground, fontFamily = Mekn.Display,
                    fontWeight = FontWeight.SemiBold, fontSize = 34.sp)
                Text("Research Evidence Network of Knowledge · v1.01", color = Mekn.OnInkMuted, fontSize = 13.sp)
            }
            Text("Search diseases, medicines, herbs", color = Mekn.OnInkMuted,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    // Different keyboards send different "enter" actions; accept them all.
                    onSearch = { if (query.isNotBlank()) onSearch(query) },
                    onDone = { if (query.isNotBlank()) onSearch(query) },
                    onGo = { if (query.isNotBlank()) onSearch(query) },
                    onSend = { if (query.isNotBlank()) onSearch(query) }
                ),
                placeholder = { Text("e.g. pancreatic cancer, aspirin, turmeric") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = onScan) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = "Scan with camera", tint = Mekn.Accent)
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Mekn.Surface,
                    unfocusedContainerColor = Mekn.Surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Button(
                onClick = { if (query.isNotBlank()) onSearch(query) },
                enabled = query.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF2A65A),
                    contentColor = Mekn.Ink,
                    disabledContainerColor = Color(0xFF3A4659),
                    disabledContentColor = Color(0xFFAEB8C6)
                )
            ) {
                Text("Search research", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            ModeSwitch(mode) { mode = it }
        }

        // Body
        Column(
            Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = MAX_WIDTH)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            BorderCard {
                Text(
                    "RENK explains published evidence. It does not diagnose or recommend treatment. " +
                        "Talk to a doctor before starting or changing any medicine.",
                    fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFF2F3A4A)
                )
            }

            ScanCard(onScan)

            SectionTitle("Explore")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExploreCard("Diseases", "Offline library", Modifier.weight(1f)) { onExplore("lib:disease") }
                ExploreCard("Drug index", "50+ generic medicines", Modifier.weight(1f)) { onExplore("drugs") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExploreCard("Herbs & plants", "Offline library", Modifier.weight(1f)) { onExplore("lib:herb") }
                ExploreCard("Simulation Lab", "Kinetics, binding, dose-response", Modifier.weight(1f)) { onOpen(Screen.Lab) }
            }
            ExploreCard("Saved PDF reports", "Your evidence library · opens offline",
                Modifier.fillMaxWidth()) { onOpen(Screen.Evidence) }
            ExploreCard("Guides: prescriptions & side effects", "How prescriptions work · understanding side effects · dose basics",
                Modifier.fillMaxWidth()) { onExplore("lib:guide") }
            if (mode == Mode.Researcher) {
                ExploreCard("Molecules & chemistry", "Try: caffeine · formula, 2D structure, properties", Modifier.fillMaxWidth()) {
                    onSearch("caffeine")
                }
            }

            SectionTitle("Recent scans")
            BorderCard(padding = 0.dp) {
                RecentRow("Aspirin", "Medicine pack · barcode", verified = true, divider = true) { onSearch("aspirin") }
                RecentRow("Turmeric", "Plant photo", verified = false, divider = false) { onSearch("curcumin") }
            }
        }
    }
}

@Composable
fun ModeSwitch(mode: Mode, onChange: (Mode) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Mekn.SegmentTrack, RoundedCornerShape(10.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Mode.entries.forEach { m ->
            val selected = m == mode
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp)
                    .background(if (selected) Mekn.Surface else Color.Transparent, RoundedCornerShape(7.dp))
                    .clickable { onChange(m) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    m.name,
                    color = if (selected) Mekn.Ink else Color(0xFFD9DEE6),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun ScanCard(onScan: () -> Unit) {
    Surface(
        onClick = onScan,
        color = Mekn.Accent,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            Column {
                Text("Scan to identify", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Medicines, pills, plants, spices, chemicals, structures", color = Color(0xFFDCE5F2), fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ExploreCard(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Mekn.Surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Mekn.Line),
        modifier = modifier.heightIn(min = 96.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Mekn.Ink)
            Text(subtitle, fontSize = 13.sp, color = Mekn.Muted)
        }
    }
}

@Composable
fun RecentRow(name: String, detail: String, verified: Boolean, divider: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold, color = Mekn.Ink)
            Text(detail, fontSize = 12.sp, color = Mekn.Muted)
        }
        if (verified) {
            Text("Verified", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Mekn.Accent, RoundedCornerShape(4.dp))
                    .padding(horizontal = 9.dp, vertical = 4.dp))
        } else {
            // Dashed border needs a custom draw; a solid grey outline stands in for now.
            Text("Possible", color = Color(0xFF3D4452), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Mekn.Surface, RoundedCornerShape(4.dp))
                    .border1(Mekn.Predicted)
                    .padding(horizontal = 9.dp, vertical = 4.dp))
        }
    }
    if (divider) HorizontalDivider(color = Color(0xFFECE9E2))
}

private fun Modifier.border1(c: Color) =
    this.then(Modifier.border(BorderStroke(1.5.dp, c), RoundedCornerShape(4.dp)))

@Composable
fun BorderCard(padding: androidx.compose.ui.unit.Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Mekn.Surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Mekn.Line),
        modifier = Modifier.fillMaxWidth()
    ) { Column(Modifier.padding(padding), content = content) }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontFamily = Mekn.Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = Mekn.Ink)
}

@Composable
fun PlaceholderScreen(title: String, note: String) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionTitle(title)
        Text(note, color = Mekn.Muted, fontSize = 14.sp)
    }
}
