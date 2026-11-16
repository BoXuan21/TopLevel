package com.toplevel.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.toplevel.app.MainActivity
import com.toplevel.app.data.Quest
import com.toplevel.app.data.Repo
import com.toplevel.app.data.StatTotal
import com.toplevel.app.game.Leveling

object Widgets {
    suspend fun refresh(ctx: Context) {
        try {
            QuestWidget().updateAll(ctx)
            StatusWidget().updateAll(ctx)
        } catch (_: Exception) { }
    }
}

private val BG = Color(0xF20A1020)
private val PANEL = Color(0xFF111A2E)
private val ACCENT = Color(0xFF3FA9FF)
private val ACCENT_SOFT = Color(0xFF9BD8FF)
private val MUTED = Color(0xFF7D8BA6)
private val TEXT = Color(0xFFE8F1FF)
private val DONE = Color(0xFF4BE3A0)

private fun style(size: Int, color: Color = TEXT, bold: Boolean = false) =
    TextStyle(color = ColorProvider(color), fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

// ───────────────────────── Quest list widget ─────────────────────────

class QuestWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = Repo.get(context)
        val day = repo.today()
        val initialQuests = repo.game.questsForNow(day)
        val initialTotals = repo.game.statTotalsNow()
        val questsFlow = repo.game.questsFor(day)
        val totalsFlow = repo.game.statTotals()
        provideContent {
            val quests by questsFlow.collectAsState(initial = initialQuests)
            val totals by totalsFlow.collectAsState(initial = initialTotals)
            QuestContent(quests, totals)
        }
    }
}

class QuestWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuestWidget()
}

val QuestIdKey = ActionParameters.Key<Long>("questId")

class ToggleQuestAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[QuestIdKey] ?: return
        Repo.get(context).toggleQuest(id)
    }
}

@Composable
private fun QuestContent(quests: List<Quest>, totals: List<StatTotal>) {
    val xp = Leveling.totals(totals).values.sum()
    val p = Leveling.progress(xp)
    val done = quests.count { it.done }
    Column(
        modifier = GlanceModifier.fillMaxSize().background(BG).cornerRadius(20.dp).padding(12.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("DAILY QUESTS", style = style(12, ACCENT, bold = true))
            Spacer(GlanceModifier.defaultWeight())
            Text("LV ${p.level} · $done/${quests.size}", style = style(12, ACCENT_SOFT, bold = true))
        }
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = p.fraction,
            modifier = GlanceModifier.fillMaxWidth().height(4.dp),
            color = ColorProvider(ACCENT),
            backgroundColor = ColorProvider(PANEL),
        )
        Spacer(GlanceModifier.height(8.dp))
        if (quests.isEmpty()) {
            Text("No quests yet — open TopLevel to generate today's.",
                style = style(13, MUTED),
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()))
        } else {
            LazyColumn {
                items(quests, itemId = { it.id }) { q ->
                    Column {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().background(PANEL).cornerRadius(10.dp)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .clickable(actionRunCallback<ToggleQuestAction>(actionParametersOf(QuestIdKey to q.id))),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (q.done) "◆" else "◇", style = style(14, if (q.done) DONE else ACCENT, bold = true))
                            Spacer(GlanceModifier.width(8.dp))
                            Text(q.title, style = style(13, if (q.done) MUTED else TEXT),
                                maxLines = 2, modifier = GlanceModifier.defaultWeight())
                            Spacer(GlanceModifier.width(6.dp))
                            Text("+${q.xp}", style = style(11, ACCENT_SOFT))
                        }
                        Spacer(GlanceModifier.height(5.dp))
                    }
                }
            }
        }
    }
}

// ───────────────────────── Status widget ─────────────────────────

class StatusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = Repo.get(context)
        val day = repo.today()
        val initialQuests = repo.game.questsForNow(day)
        val initialTotals = repo.game.statTotalsNow()
        val name = repo.prefs.name
        val initialGold = repo.db.vault().goldBalanceNow()
        val goldFlow = repo.db.vault().goldBalance()
        val questsFlow = repo.game.questsFor(day)
        val totalsFlow = repo.game.statTotals()
        provideContent {
            val quests by questsFlow.collectAsState(initial = initialQuests)
            val totals by totalsFlow.collectAsState(initial = initialTotals)
            val gold by goldFlow.collectAsState(initial = initialGold)
            StatusContent(name, quests, totals, gold)
        }
    }
}

class StatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusWidget()
}

@Composable
private fun StatusContent(name: String, quests: List<Quest>, totals: List<StatTotal>, gold: Int) {
    val map = Leveling.totals(totals)
    val xp = map.values.sum()
    val p = Leveling.progress(xp)
    Column(
        modifier = GlanceModifier.fillMaxSize().background(BG).cornerRadius(20.dp).padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(if (name.isBlank()) "STATUS" else name.uppercase(), style = style(11, MUTED, bold = true))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LV ${p.level}", style = style(26, TEXT, bold = true))
            Spacer(GlanceModifier.width(8.dp))
            Text("${Leveling.rank(p.level)}-RANK", style = style(12, ACCENT, bold = true))
        }
        Text(Leveling.title(p.level), style = style(11, ACCENT_SOFT))
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = p.fraction,
            modifier = GlanceModifier.fillMaxWidth().height(4.dp),
            color = ColorProvider(ACCENT),
            backgroundColor = ColorProvider(PANEL),
        )
        Text("${p.intoLevel}/${p.levelSpan} XP", style = style(10, MUTED))
        Spacer(GlanceModifier.height(6.dp))
        Text(map.entries.joinToString("  ") { "${it.key.short} ${Leveling.statLevel(it.value)}" },
            style = style(11, TEXT))
        Spacer(GlanceModifier.height(4.dp))
        Text("Quests ${quests.count { it.done }}/${quests.size}   ◈ $gold", style = style(11, DONE, bold = true))
    }
}
