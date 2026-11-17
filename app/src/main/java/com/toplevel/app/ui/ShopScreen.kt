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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toplevel.app.data.Prefs
import com.toplevel.app.data.ShopOffer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun price(v: Double) = "€" + "%.2f".format(v).replace(".00", "")

/**
 * Find the cheapest offer for a product (PC parts, gadgets, anything) across several shops.
 * Claude searches price comparison sites and shops on the web; the offers come back cheapest first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShopScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf(vm.shopResult?.query ?: "") }
    var budget by rememberSaveable { mutableStateOf("") }
    var used by rememberSaveable { mutableStateOf(false) }
    var reward by remember { mutableStateOf<Pair<String, String>?>(null) }
    fun search(q: String = query) { query = q; vm.searchShop(q, budget.toIntOrNull(), used) }
    fun openUrl(url: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { vm.message = "No app can open this link." }
    }
    val r = vm.shopResult

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Gap) {
        item {
            Panel(title = "Find the best price", color = C.warn) {
                Field(query, { query = it }, "What are you looking for?", placeholder = "e.g. RTX 5070, Ryzen 7 9800X3D, 2TB NVMe",
                    singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field(budget, { budget = it.filter(Char::isDigit) }, "Max € (optional)", Modifier.weight(1f), singleLine = true,
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Spacer(Modifier.width(12.dp))
                    Text("Used OK", color = C.text, fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Switch(checked = used, onCheckedChange = { used = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = C.accent))
                }
                Spacer(Modifier.height(10.dp))
                PrimaryButton(if (vm.shopBusy) "Searching…" else "Compare prices", Modifier.fillMaxWidth(),
                    enabled = query.isNotBlank() && !vm.shopBusy) { search() }
                if (vm.shopBusy) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = C.warn, trackColor = C.bg)
                    Text("Checking comparison sites and shops… this takes about a minute.", color = C.muted, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }
                if (!vm.prefs.hasKey) Text("Needs your Anthropic API key (Settings). Each search costs a few cents.",
                    color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                val recent = vm.prefs.shopRecent.lines().filter { it.isNotBlank() }
                if (recent.isNotEmpty() && !vm.shopBusy) {
                    Spacer(Modifier.height(10.dp))
                    Text("RECENT", color = C.muted, fontSize = 10.sp, fontFamily = Fonts.tech)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        recent.forEach { q -> Segment(q, false) { search(q) } }
                    }
                }
            }
        }

        vm.shopError?.let { e ->
            item {
                Panel(title = "Search failed", color = C.bad) {
                    Text(e, color = C.text, fontSize = 14.sp)
                    if (e.contains("web", true) && e.contains("search", true))
                        Text("Web search may be off for your API organization: turn it on in the Anthropic Console (Settings → Privacy).",
                            color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        if (r != null) {
            item {
                val best = r.cheapest
                Panel(title = r.product, color = C.good) {
                    if (best != null) {
                        Text("CHEAPEST", color = C.muted, fontSize = 10.sp, fontFamily = Fonts.tech)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(price(best.total), color = C.good, fontSize = 30.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.width(10.dp))
                            Text("at ${best.shop}", color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 5.dp))
                        }
                        val top = r.offers.maxOfOrNull { it.total } ?: best.total
                        if (r.offers.size > 1 && top > best.total) Text("Saves ${price(top - best.total)} vs. the most expensive offer found",
                            color = C.accentSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (r.summary.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(r.summary, color = C.text, fontSize = 14.sp)
                    }
                    if (best != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton("Open shop", color = C.good) { openUrl(best.url) }
                            if (vm.extra(Prefs.Extras.VAULT))
                                GhostButton("Save as reward", color = C.warn) { reward = r.product to "%.0f".format(best.total) }
                        }
                    }
                }
            }
            if (r.offers.isNotEmpty()) item {
                Panel(title = "All offers · ${r.offers.size}") {
                    r.offers.forEachIndexed { i, o -> OfferRow(o, cheapest = i == 0) { openUrl(o.url) } }
                    val time = Instant.ofEpochMilli(r.at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
                    Text("Prices from a web search at $time. Check the price in the shop before you buy. Tap an offer to open it.",
                        color = C.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
            } else item {
                Panel { Text("No offers with a price were found. Try a more exact product name.", color = C.muted, fontSize = 14.sp) }
            }
            if (r.alternatives.isNotEmpty()) item {
                Panel(title = "Alternatives") {
                    r.alternatives.forEach { a ->
                        Column(Modifier.fillMaxWidth().clip(SmallShape).tap { if (!vm.shopBusy) search(a.name) }.padding(vertical = 6.dp)) {
                            Row {
                                Text(a.name, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                a.approx?.let { Text("~${price(it)}", color = C.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                            }
                            if (a.why.isNotBlank()) Text(a.why, color = C.muted, fontSize = 12.sp)
                        }
                    }
                    Text("Tap one to compare its prices.", color = C.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }

    reward?.let { (name, eur) -> AddRewardDialog(vm, initialName = name, initialPrice = eur) { reward = null } }
}

@Composable
private fun OfferRow(o: ShopOffer, cheapest: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(SmallShape)
            .background(if (cheapest) C.good.copy(alpha = 0.10f) else C.bg.copy(alpha = 0.5f))
            .tap(onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(o.shop, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (o.title.isNotBlank()) Text(o.title, color = C.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val tags = listOfNotNull(
                o.condition.takeIf { it != "new" }?.replaceFirstChar { it.uppercase() },
                when (o.inStock) { true -> "In stock"; false -> "Not in stock"; null -> null },
                o.note.takeIf { it.isNotBlank() },
            )
            if (tags.isNotEmpty()) Text(tags.joinToString(" · "), color = if (o.inStock == false) C.bad else C.accentSoft, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(price(o.total), color = if (cheapest) C.good else C.text, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Text(when {
                o.shipping == null -> "+ shipping?"
                o.shipping == 0.0 -> "free shipping"
                else -> "incl. ${price(o.shipping)} shipping"
            }, color = C.muted, fontSize = 11.sp)
        }
        Text(" ›", color = C.accent, fontSize = 20.sp)
    }
}
