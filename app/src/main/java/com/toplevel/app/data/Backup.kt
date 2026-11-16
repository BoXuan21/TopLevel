package com.toplevel.app.data

import android.net.Uri
import androidx.room.withTransaction
import com.toplevel.app.widget.Widgets
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupFile(
    val version: Int = 1,
    val exportedAt: Long,
    val prefs: Map<String, String> = emptyMap(),
    val quests: List<Quest> = emptyList(),
    val xp: List<XpEvent> = emptyList(),
    val plans: List<Plan> = emptyList(),
    val checkins: List<CheckIn> = emptyList(),
    val food: List<FoodEntry> = emptyList(),
    val logs: List<LogEntry> = emptyList(),
    val skills: List<SkillItem> = emptyList(),
    val chat: List<ChatMessage> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val discover: List<DiscoverItem> = emptyList(),
    val sets: List<WorkoutSet> = emptyList(),
    val health: List<HealthDay> = emptyList(),
    val body: List<BodyEntry> = emptyList(),
    val bosses: List<WeeklyBoss> = emptyList(),
    val achievements: List<AchievementUnlock> = emptyList(),
    val progress: List<SkillProgress> = emptyList(),
    val sessionItems: List<SessionItem> = emptyList(),
    val gold: List<GoldEvent> = emptyList(),
    val rewards: List<Reward> = emptyList(),
    val savings: List<SavingsEntry> = emptyList(),
)

private val backupJson = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }

/**
 * Writes all data (except your API key) to a file you pick — choose Google Drive
 * in the picker to back up to the cloud.
 */
suspend fun Repo.exportBackup(uri: Uri) {
    val b = db.backup()
    val file = BackupFile(
        exportedAt = System.currentTimeMillis(),
        prefs = prefs.exportMap(),
        quests = b.quests(), xp = b.xp(), plans = b.plans(), checkins = b.checkins(), food = b.food(),
        logs = b.logs(), skills = b.skills(), chat = b.chat(), memories = b.memories(), discover = b.discover(),
        sets = b.sets(), health = b.health(), body = b.body(), bosses = b.bosses(), achievements = b.achievements(),
        progress = b.progress(), sessionItems = b.sessionItems(), gold = b.gold(), rewards = b.rewards(), savings = b.savings(),
    )
    ctx.contentResolver.openOutputStream(uri, "wt")?.use {
        it.write(backupJson.encodeToString(BackupFile.serializer(), file).toByteArray())
    } ?: throw IllegalStateException("Couldn't open file")
    prefs.lastBackup = System.currentTimeMillis()
}

/** Replaces all current data with the backup. */
suspend fun Repo.importBackup(uri: Uri) {
    val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        ?: throw IllegalStateException("Couldn't open file")
    val f = backupJson.decodeFromString(BackupFile.serializer(), text)
    val b = db.backup()
    db.clearAllTables()
    db.withTransaction {
        b.putQuests(f.quests); b.putXp(f.xp); b.putPlans(f.plans); b.putCheckins(f.checkins)
        b.putFood(f.food); b.putLogs(f.logs); b.putSkills(f.skills); b.putChat(f.chat)
        b.putMemories(f.memories); b.putDiscover(f.discover); b.putSets(f.sets); b.putHealth(f.health)
        b.putBody(f.body); b.putBosses(f.bosses); b.putAchievements(f.achievements)
        b.putProgress(f.progress); b.putSessionItems(f.sessionItems); b.putGold(f.gold)
        b.putRewards(f.rewards); b.putSavings(f.savings)
    }
    prefs.importMap(f.prefs)
    Widgets.refresh(ctx)
}
