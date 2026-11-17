package com.toplevel.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toplevel.app.data.Flashcard
import com.toplevel.app.data.SkillItem
import com.toplevel.app.data.Stat

/** Sections of the Learn tab (deep links like "more:cards" land here). */
val JournalTabs = listOf("quiz", "journal", "cards")

/** Learn tab: the AI quiz on what you're learning, plus your journal. */
@Composable
fun JournalTab(vm: AppViewModel) {
    val due by vm.dueCards.collectAsState(initial = emptyList())
    Column {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            SystemHeader("LEARN")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Segment("Quiz" + if (due.isNotEmpty()) " · ${due.size}" else "", vm.journalTab != "journal") { vm.journalTab = "quiz" }
                Segment("Journal", vm.journalTab == "journal") { vm.journalTab = "journal" }
            }
        }
        Box(Modifier.weight(1f)) {
            if (vm.journalTab == "journal") JournalPage(vm) else QuizPage(vm)
        }
    }
}

private val learnColor get() = C.stat(Stat.INTELLECT)

@Composable
fun QuizPage(vm: AppViewModel) {
    val skills by vm.skills.collectAsState(initial = emptyList())
    val due by vm.dueCards.collectAsState(initial = emptyList())
    val topics = skills.filter { it.status == "learning" }
    var selected by rememberSaveable { mutableStateOf<String?>(null) } // null = all topics
    var adding by rememberSaveable { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<SkillItem?>(null) }
    var pinned by remember { mutableStateOf<Flashcard?>(null) } // card being checked by the AI
    var skipped by remember { mutableStateOf(setOf<Long>()) } // wrong answers go to the back of today's queue

    val sel = selected?.takeIf { s -> topics.any { it.title == s } }
    val queue = due.filter { sel == null || it.topic == sel }.sortedBy { it.id in skipped }
    val result = vm.quizResult?.takeIf { it.first == pinned?.id }?.second
    // Keep the checked card on screen (with its feedback) until "Next"; otherwise show the next due one
    val card = pinned?.takeIf { result != null || vm.checking } ?: queue.firstOrNull()

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Gap) {
        // Topics
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (topics.size > 1) Segment("All", sel == null) { selected = null }
                topics.forEach { t ->
                    val n = due.count { it.topic == t.title }
                    TopicChip(t.title + if (n > 0) " · $n" else "", sel == t.title,
                        onClick = { selected = t.title }, onLongClick = { menuFor = t })
                }
                Segment("+ Topic", adding) { adding = !adding }
            }
        }

        if (adding || topics.isEmpty()) item { AddTopicPanel(vm, first = topics.isEmpty()) { adding = false } }

        if (topics.isNotEmpty() || due.isNotEmpty()) item {
            if (card == null) {
                val t = topics.firstOrNull { it.title == sel } ?: topics.firstOrNull()
                Panel(title = "All done", color = C.good) {
                    Text("No questions due${if (sel != null) " on $sel" else ""}. Answered today: ${vm.prefs.cardsReviewed(vm.day)}.",
                        color = C.text, fontSize = 15.sp)
                    if (t != null && vm.prefs.hasKey) {
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton("More questions on ${t.title}", Modifier.fillMaxWidth(), enabled = vm.busy == null) {
                            vm.moreQuestions(t)
                        }
                    }
                }
            } else QuestionCard(vm, card, result,
                check = { a -> pinned = card; vm.checkAnswer(card, a) },
                next = { if (result != null && result.grade == 0) skipped = skipped + card.id; vm.quizResult = null; pinned = null },
                selfGraded = { g -> if (g == 0) skipped = skipped + card.id })
        }

        if (topics.isNotEmpty()) item {
            Text("Hold a topic to remove it or get new questions. Questions you get wrong come back sooner, the AI digs deeper there.",
                color = C.muted, fontSize = 12.sp)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    menuFor?.let { t ->
        AlertDialog(
            onDismissRequest = { menuFor = null }, containerColor = C.panel,
            title = { Text(t.title) },
            text = { Text(if (t.notes.isNotBlank()) "Level / goal: ${t.notes}" else "What do you want to do?", color = C.text) },
            confirmButton = {
                TextButton(onClick = { vm.moreQuestions(t); menuFor = null }, enabled = vm.prefs.hasKey && vm.busy == null) {
                    Text("New questions", color = C.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.removeLearnTopic(t); if (selected == t.title) selected = null; menuFor = null }) {
                    Text("Done learning this", color = C.bad)
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopicChip(label: String, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Text(label,
        color = if (selected) C.bg else learnColor,
        fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(SmallShape)
            .background(if (selected) learnColor else C.panel)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 7.dp))
}

@Composable
private fun AddTopicPanel(vm: AppViewModel, first: Boolean, done: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf("") }
    Panel(title = if (first) "What are you learning right now?" else "New topic", color = learnColor) {
        if (first) {
            Text("Type what you're learning. The AI writes questions for your level, quizzes you every day, checks your answers " +
                "and asks more about what you get wrong.", color = C.muted, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
        }
        Field(title, { title = it }, "Topic", singleLine = true, placeholder = "e.g. Kubernetes CKA, Spanish A2, Linear algebra")
        Spacer(Modifier.height(8.dp))
        Field(level, { level = it }, "Your level / goal (optional)", singleLine = true,
            placeholder = "e.g. beginner, exam in March, know the basics")
        Spacer(Modifier.height(10.dp))
        PrimaryButton(if (vm.prefs.hasKey) "Start quiz" else "Add topic", Modifier.fillMaxWidth(),
            enabled = title.isNotBlank() && vm.busy == null) {
            vm.addLearnTopic(title.trim(), level.trim()); title = ""; level = ""; done()
        }
        if (!vm.prefs.hasKey) Text("Add your API key in Settings so the AI can write and check questions.",
            color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun QuestionCard(
    vm: AppViewModel,
    card: Flashcard,
    result: com.toplevel.app.data.QuizResult?,
    check: (String) -> Unit,
    next: () -> Unit,
    selfGraded: (Int) -> Unit,
) {
    var answer by remember(card.id) { mutableStateOf("") }
    var revealed by remember(card.id) { mutableStateOf(false) }
    Panel(title = card.topic.ifBlank { "From your journal" }, color = learnColor) {
        Text(card.front, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
        Spacer(Modifier.height(12.dp))
        when {
            result != null -> {
                val (label, color) = when (result.grade) {
                    0 -> "NOT QUITE" to C.bad; 1 -> "PARTLY" to C.warn; 2 -> "CORRECT" to C.good; else -> "PERFECT" to C.accent
                }
                Chip(label, color)
                if (result.feedback.isNotBlank()) Text(result.feedback, color = C.text, fontSize = 15.sp, lineHeight = 21.sp,
                    modifier = Modifier.padding(top = 8.dp))
                AnswerBox(card.back)
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Next", Modifier.fillMaxWidth(), onClick = next)
            }
            revealed -> {
                AnswerBox(card.back)
                Spacer(Modifier.height(12.dp))
                Text("Did you know it?", color = C.muted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("No", Modifier.weight(1f), color = C.bad) { vm.reviewCard(card, 0); selfGraded(0) }
                    GhostButton("Partly", Modifier.weight(1f), color = C.warn) { vm.reviewCard(card, 1); selfGraded(1) }
                    GhostButton("Yes", Modifier.weight(1f), color = C.good) { vm.reviewCard(card, 2); selfGraded(2) }
                }
            }
            else -> {
                Field(answer, { answer = it }, "Your answer", minLines = 2, voice = true)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(if (vm.checking) "Checking…" else "Check", Modifier.weight(1f),
                        enabled = answer.isNotBlank() && vm.prefs.hasKey && !vm.checking) { check(answer.trim()) }
                    GhostButton("Show answer", color = C.muted) { revealed = true }
                }
            }
        }
    }
}

@Composable
private fun AnswerBox(text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(SmallShape).background(C.bg.copy(alpha = 0.5f)).padding(12.dp)) {
        Text("ANSWER", color = C.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text(text, color = C.accentSoft, fontSize = 16.sp, lineHeight = 22.sp)
    }
}
