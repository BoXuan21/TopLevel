package com.toplevel.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CoachScreen(vm: AppViewModel) {
    val chat by vm.chat.collectAsState(initial = emptyList())
    var input by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(chat.size, vm.busy) {
        if (chat.isNotEmpty()) listState.animateScrollToItem(chat.size)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SystemHeader("COACH") }
            IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.Delete, "Clear chat", tint = C.muted) }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (chat.isEmpty()) item {
                Panel(title = "Your coach") {
                    Text(
                        if (vm.prefs.hasKey)
                            "Ask anything — training, food, work, what to learn, or just talk about your day.\n\n" +
                                "Paste your training or eating plan here and I'll store it. Tell me what you ate and I'll log it. " +
                                "I can also change things for you: today's quests, your weekly training schedule, " +
                                "your sports and nutrition targets, skills and calendar events."
                        else "Add your Anthropic API key in Setup to talk to your coach.",
                        color = C.text, fontSize = 14.sp, lineHeight = 20.sp,
                    )
                }
            }
            items(chat, key = { it.id }) { m ->
                val mine = m.role == "user"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    SelectionContainer {
                        Text(
                            m.content,
                            color = if (mine) C.bg else C.text,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier
                                .widthIn(max = 520.dp)
                                .clip(SmallShape)
                                .background(if (mine) C.accent else C.panel2)
                                .padding(12.dp),
                        )
                    }
                }
            }
            if (vm.busy != null) item {
                Text("▍ ${vm.busy}", color = C.accent, fontFamily = Fonts.tech, fontSize = 12.sp)
            }
        }
        Row(
            Modifier.fillMaxWidth().background(C.panel).padding(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.weight(1f)) {
                Field(input, { input = it }, "Message", placeholder = "e.g. Bench 80kg 3x8. I had 2 eggs and oats.", voice = true)
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                enabled = input.isNotBlank() && vm.busy == null && vm.prefs.hasKey,
                onClick = { vm.send(input.trim()); input = "" },
            ) { Icon(Icons.Filled.Send, "Send", tint = C.accent) }
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        containerColor = C.panel,
        title = { Text("Clear chat history?") },
        text = { Text("Your plans, logs and memories stay. Only the conversation is removed.") },
        confirmButton = { TextButton(onClick = { vm.clearChat(); confirmClear = false }) { Text("Clear", color = C.bad) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}
