package org.mekn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    India("India", Icons.Outlined.Eco),
    Evidence("Evidence", Icons.Outlined.VerifiedUser)
}

enum class Mode { Patient, Researcher }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MeknApp() }
    }
}

@Composable
fun MeknApp() {
    var tab by rememberSaveable { mutableStateOf(Screen.Home) }
    var showScan by rememberSaveable { mutableStateOf(false) }

    MaterialTheme(colorScheme = lightColorScheme(primary = Mekn.Accent, background = Mekn.Ground)) {
        Scaffold(
            containerColor = Mekn.Ground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar(containerColor = Mekn.Surface) {
                    Screen.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t && !showScan,
                            onClick = { showScan = false; tab = t },
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
                when {
                    showScan -> PlaceholderScreen("Scan to identify", "Camera screen comes in step 3 (CameraX + ML Kit).")
                    tab == Screen.Home -> HomeScreen(
                        onScan = { showScan = true },
                        onOpen = { tab = it }
                    )
                    tab == Screen.Explore -> PlaceholderScreen("Explore", "Diseases, medicines, companies, labs and devices.")
                    tab == Screen.India -> PlaceholderScreen("India & herbs", "Institutions and medicinal plants.")
                    tab == Screen.Evidence -> PlaceholderScreen("Evidence", "Why we believe a claim.")
                }
            }
        }
    }
}

// ---------- Home ----------
@Composable
fun HomeScreen(onScan: () -> Unit, onOpen: (Screen) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(Mode.Patient) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Header
        Column(
            Modifier.fillMaxWidth().background(Mekn.Ink)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text("MEKN", color = Mekn.Ground, fontFamily = Mekn.Display,
                    fontWeight = FontWeight.SemiBold, fontSize = 34.sp)
                Text("Medical Evidence Knowledge Network", color = Mekn.OnInkMuted, fontSize = 13.sp)
            }
            Text("Search diseases, medicines, herbs", color = Mekn.OnInkMuted,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
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
            ModeSwitch(mode) { mode = it }
        }

        // Body
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            BorderCard {
                Text(
                    "MEKN explains published evidence. It does not diagnose or recommend treatment. " +
                        "Talk to a doctor before starting or changing any medicine.",
                    fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFF2F3A4A)
                )
            }

            ScanCard(onScan)

            SectionTitle("Explore")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExploreCard("Diseases", "Treatments, trials, research", Modifier.weight(1f)) { onOpen(Screen.Explore) }
                ExploreCard("Medicines", "Ingredients, uses, safety", Modifier.weight(1f)) { onOpen(Screen.Explore) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExploreCard("India & herbs", "Institutions, medicinal plants", Modifier.weight(1f)) { onOpen(Screen.India) }
                ExploreCard("Evidence", "Why we believe a claim", Modifier.weight(1f)) { onOpen(Screen.Evidence) }
            }
            if (mode == Mode.Researcher) {
                ExploreCard("Molecules & chemistry", "Structures, atoms, computed properties", Modifier.fillMaxWidth()) {
                    onOpen(Screen.Explore)
                }
            }

            SectionTitle("Recent scans")
            BorderCard(padding = 0.dp) {
                RecentRow("Aspirin", "Medicine pack · barcode", verified = true, divider = true)
                RecentRow("Turmeric", "Plant photo", verified = false, divider = false)
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
fun RecentRow(name: String, detail: String, verified: Boolean, divider: Boolean) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp),
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
