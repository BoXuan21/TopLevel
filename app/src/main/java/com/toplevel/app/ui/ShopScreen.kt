package com.toplevel.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toplevel.app.data.Prefs
import com.toplevel.app.data.Shop
import java.util.Locale

/**
 * Find the cheapest price for anything (PC parts, gadgets…) for free: no AI, it opens the search
 * of comparison sites and shops, sorted by price.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShopScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var rewardOpen by remember { mutableStateOf(false) }
    var recentVersion by remember { mutableIntStateOf(0) }
    val sites = remember { Shop.sites(Locale.getDefault().country) }
    val recent = recentVersion.let { vm.prefs.shopRecent.lines().filter { it.isNotBlank() } }

    fun open(site: Shop.Site, q: String = query) {
        if (q.isBlank()) return
        query = q
        vm.prefs.shopRecent = Shop.remember(vm.prefs.shopRecent, q); recentVersion++
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(site.url(q))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { vm.message = "No app can open this link." }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Gap) {
        item {
            Panel(title = "Find the best price", color = C.warn) {
                Field(query, { query = it }, "What are you looking for?", placeholder = "e.g. RTX 5070, Ryzen 7 9800X3D, 2TB NVMe",
                    singleLine = true)
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Compare on ${sites.first().name}", Modifier.fillMaxWidth(), enabled = query.isNotBlank()) {
                    open(sites.first())
                }
                Text("Opens in your browser, cheapest first. Free, no AI.", color = C.muted, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Panel(title = "Check other shops too") {
                sites.drop(1).forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp).clip(SmallShape).background(C.bg.copy(alpha = 0.5f))
                            .tap { open(s) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, color = if (query.isBlank()) C.muted else C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(s.hint, color = C.muted, fontSize = 12.sp)
                        }
                        Text("›", color = C.accent, fontSize = 22.sp)
                    }
                }
                if (vm.extra(Prefs.Extras.VAULT)) {
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Found it? Save as reward", color = C.warn, enabled = query.isNotBlank()) { rewardOpen = true }
                }
            }
        }
        if (recent.isNotEmpty()) item {
            Panel(title = "Recent") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    recent.forEach { q -> Segment(q, q == query) { query = q } }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }

    if (rewardOpen) AddRewardDialog(vm, initialName = query.trim()) { rewardOpen = false }
}
