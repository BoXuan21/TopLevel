package com.toplevel.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class StatTotal(val stat: Stat, val total: Int)

@Dao
interface GameDao {
    // Quests
    @Query("SELECT * FROM quests WHERE date = :day ORDER BY done ASC, id ASC")
    fun questsFor(day: Long): Flow<List<Quest>>

    @Query("SELECT * FROM quests WHERE date = :day ORDER BY done ASC, id ASC")
    suspend fun questsForNow(day: Long): List<Quest>

    @Query("SELECT * FROM quests WHERE id = :id")
    suspend fun quest(id: Long): Quest?

    @Insert
    suspend fun insertQuest(q: Quest): Long

    @Update
    suspend fun updateQuest(q: Quest)

    @Delete
    suspend fun deleteQuest(q: Quest)

    @Query("DELETE FROM quests WHERE date = :day AND source = 'ai' AND done = 0")
    suspend fun clearOpenAiQuests(day: Long)

    @Query("SELECT COUNT(*) FROM quests WHERE date = :day AND done = 1")
    suspend fun doneCount(day: Long): Int

    @Query("SELECT * FROM quests WHERE date >= :fromDay ORDER BY date DESC")
    suspend fun questsSince(fromDay: Long): List<Quest>

    // XP
    @Insert
    suspend fun insertXp(e: XpEvent)

    @Query("DELETE FROM xp_events WHERE reason = :reason")
    suspend fun deleteXpByReason(reason: String)

    @Query("SELECT stat, SUM(amount) AS total FROM xp_events GROUP BY stat")
    fun statTotals(): Flow<List<StatTotal>>

    @Query("SELECT stat, SUM(amount) AS total FROM xp_events GROUP BY stat")
    suspend fun statTotalsNow(): List<StatTotal>

    @Query("SELECT * FROM xp_events ORDER BY ts DESC LIMIT :n")
    fun recentXp(n: Int): Flow<List<XpEvent>>
}

@Dao
interface LifeDao {
    // Plans
    @Query("SELECT * FROM plans")
    fun plans(): Flow<List<Plan>>

    @Query("SELECT * FROM plans")
    suspend fun plansNow(): List<Plan>

    @Upsert
    suspend fun upsertPlan(p: Plan)

    // Check-ins
    @Query("SELECT * FROM checkins WHERE date = :day")
    fun checkIn(day: Long): Flow<CheckIn?>

    @Query("SELECT * FROM checkins WHERE date = :day")
    suspend fun checkInNow(day: Long): CheckIn?

    @Query("SELECT * FROM checkins ORDER BY date DESC LIMIT :n")
    suspend fun recentCheckIns(n: Int): List<CheckIn>

    @Query("SELECT * FROM checkins ORDER BY date DESC LIMIT :n")
    fun recentCheckInsFlow(n: Int): Flow<List<CheckIn>>

    @Upsert
    suspend fun upsertCheckIn(c: CheckIn)

    // Food
    @Query("SELECT * FROM food WHERE date = :day ORDER BY ts ASC")
    fun foodFor(day: Long): Flow<List<FoodEntry>>

    @Query("SELECT * FROM food WHERE date >= :fromDay ORDER BY ts ASC")
    suspend fun foodSince(fromDay: Long): List<FoodEntry>

    @Insert
    suspend fun insertFood(f: FoodEntry)

    @Delete
    suspend fun deleteFood(f: FoodEntry)

    // Logs
    @Query("SELECT * FROM logs WHERE date = :day ORDER BY ts DESC")
    fun logsFor(day: Long): Flow<List<LogEntry>>

    @Query("SELECT * FROM logs ORDER BY ts DESC LIMIT :n")
    fun recentLogs(n: Int): Flow<List<LogEntry>>

    @Query("SELECT * FROM logs WHERE date >= :fromDay ORDER BY ts ASC")
    suspend fun logsSince(fromDay: Long): List<LogEntry>

    @Insert
    suspend fun insertLog(l: LogEntry)

    @Delete
    suspend fun deleteLog(l: LogEntry)

    // Skills
    @Query("SELECT * FROM skills ORDER BY CASE status WHEN 'learning' THEN 0 WHEN 'todo' THEN 1 ELSE 2 END, createdAt DESC")
    fun skills(): Flow<List<SkillItem>>

    @Query("SELECT * FROM skills")
    suspend fun skillsNow(): List<SkillItem>

    @Insert
    suspend fun insertSkill(s: SkillItem): Long

    @Update
    suspend fun updateSkill(s: SkillItem)

    @Delete
    suspend fun deleteSkill(s: SkillItem)

    // Chat
    @Query("SELECT * FROM chat ORDER BY ts ASC")
    fun chat(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM (SELECT * FROM chat ORDER BY ts DESC LIMIT :n) ORDER BY ts ASC")
    suspend fun recentChat(n: Int): List<ChatMessage>

    @Insert
    suspend fun insertChat(m: ChatMessage)

    @Query("DELETE FROM chat")
    suspend fun clearChat()

    // Memories
    @Query("SELECT * FROM memories ORDER BY ts DESC")
    fun memories(): Flow<List<Memory>>

    @Query("SELECT * FROM memories ORDER BY ts DESC")
    suspend fun memoriesNow(): List<Memory>

    @Insert
    suspend fun insertMemory(m: Memory)

    @Delete
    suspend fun deleteMemory(m: Memory)

    // Discover
    @Query("SELECT * FROM discover WHERE date = :day ORDER BY kind ASC, id ASC")
    fun discoverFor(day: Long): Flow<List<DiscoverItem>>

    @Query("SELECT * FROM discover WHERE date >= :fromDay")
    suspend fun discoverSince(fromDay: Long): List<DiscoverItem>

    @Query("DELETE FROM discover WHERE date = :day")
    suspend fun clearDiscover(day: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiscover(items: List<DiscoverItem>)
}

@Dao
interface FitDao {
    // Sets
    @Query("SELECT * FROM sets WHERE date = :day ORDER BY ts ASC")
    fun setsFor(day: Long): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM sets WHERE date >= :fromDay ORDER BY ts ASC")
    suspend fun setsSince(fromDay: Long): List<WorkoutSet>

    @Query("SELECT * FROM sets WHERE date >= :fromDay ORDER BY ts ASC")
    fun setsSinceFlow(fromDay: Long): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM sets ORDER BY ts ASC")
    fun allSets(): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM sets WHERE exercise = :ex COLLATE NOCASE ORDER BY ts ASC")
    fun setsForExercise(ex: String): Flow<List<WorkoutSet>>

    @Query("SELECT exercise FROM sets GROUP BY exercise COLLATE NOCASE ORDER BY MAX(ts) DESC LIMIT 30")
    fun recentExercises(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM sets")
    suspend fun setCount(): Int

    @Query("SELECT COUNT(*) FROM sets WHERE date = :day")
    suspend fun setCountFor(day: Long): Int

    @Insert
    suspend fun insertSet(s: WorkoutSet): Long

    @Delete
    suspend fun deleteSet(s: WorkoutSet)

    // Health
    @Query("SELECT * FROM health WHERE date = :day")
    fun healthFor(day: Long): Flow<HealthDay?>

    @Query("SELECT * FROM health WHERE date >= :fromDay ORDER BY date ASC")
    suspend fun healthSince(fromDay: Long): List<HealthDay>

    @Upsert
    suspend fun upsertHealth(h: HealthDay)

    // Body
    @Query("SELECT * FROM body ORDER BY date ASC")
    fun body(): Flow<List<BodyEntry>>

    @Query("SELECT * FROM body WHERE date >= :fromDay ORDER BY date ASC")
    suspend fun bodySince(fromDay: Long): List<BodyEntry>

    @Query("SELECT * FROM body WHERE date = :day")
    suspend fun bodyOn(day: Long): BodyEntry?

    @Upsert
    suspend fun upsertBody(b: BodyEntry)

    @Delete
    suspend fun deleteBody(b: BodyEntry)

    // Boss
    @Query("SELECT * FROM bosses WHERE weekStart = :week")
    fun boss(week: Long): Flow<WeeklyBoss?>

    @Query("SELECT * FROM bosses WHERE weekStart = :week")
    suspend fun bossNow(week: Long): WeeklyBoss?

    @Query("SELECT COUNT(*) FROM bosses WHERE defeated = 1")
    suspend fun bossesDefeated(): Int

    @Upsert
    suspend fun upsertBoss(b: WeeklyBoss)

    // Achievements
    @Query("SELECT * FROM achievements")
    fun achievements(): Flow<List<AchievementUnlock>>

    @Query("SELECT * FROM achievements")
    suspend fun achievementsNow(): List<AchievementUnlock>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun unlock(a: AchievementUnlock): Long
}

@Dao
interface VaultDao {
    // Gold
    @Insert suspend fun insertGold(g: GoldEvent)
    @Query("DELETE FROM gold WHERE reason = :reason") suspend fun deleteGoldByReason(reason: String)
    @Query("SELECT COALESCE(SUM(amount), 0) FROM gold") fun goldBalance(): Flow<Int>
    @Query("SELECT COALESCE(SUM(amount), 0) FROM gold") suspend fun goldBalanceNow(): Int

    // Rewards
    @Query("SELECT * FROM rewards ORDER BY CASE WHEN redeemedAt IS NULL THEN 0 ELSE 1 END, createdAt DESC")
    fun rewards(): Flow<List<Reward>>
    @Query("SELECT * FROM rewards") suspend fun rewardsNow(): List<Reward>
    @Query("SELECT * FROM rewards WHERE id = :id") suspend fun reward(id: Long): Reward?
    @Insert suspend fun insertReward(r: Reward): Long
    @Update suspend fun updateReward(r: Reward)
    @Delete suspend fun deleteReward(r: Reward)

    // Savings
    @Query("SELECT * FROM savings ORDER BY ts DESC LIMIT 100") fun savings(): Flow<List<SavingsEntry>>
    @Query("SELECT COALESCE(SUM(amountEur), 0) FROM savings") fun pot(): Flow<Float>
    @Query("SELECT COALESCE(SUM(amountEur), 0) FROM savings") suspend fun potNow(): Float
    @Query("SELECT COALESCE(SUM(amountEur), 0) FROM savings WHERE amountEur > 0") suspend fun totalDepositedNow(): Float
    @Query("SELECT COALESCE(SUM(amountEur), 0) FROM savings WHERE amountEur > 0 AND date >= :fromDay") fun depositedSince(fromDay: Long): Flow<Float>
    @Query("SELECT COALESCE(SUM(amountEur), 0) FROM savings WHERE amountEur > 0 AND date >= :fromDay") suspend fun depositedSinceNow(fromDay: Long): Float
    @Insert suspend fun insertSavings(s: SavingsEntry): Long
    @Delete suspend fun deleteSavings(s: SavingsEntry)

    // Calisthenics ladders
    @Query("SELECT * FROM skill_progress") fun progress(): Flow<List<SkillProgress>>
    @Query("SELECT * FROM skill_progress") suspend fun progressNow(): List<SkillProgress>
    @Query("SELECT * FROM skill_progress WHERE ladder = :id") suspend fun progressOf(id: String): SkillProgress?
    @Upsert suspend fun upsertProgress(p: SkillProgress)

    // Session
    @Query("SELECT * FROM session_items WHERE date = :day ORDER BY pos ASC") fun session(day: Long): Flow<List<SessionItem>>
    @Query("SELECT * FROM session_items WHERE date = :day ORDER BY pos ASC") suspend fun sessionNow(day: Long): List<SessionItem>
    @Query("DELETE FROM session_items WHERE date = :day") suspend fun clearSession(day: Long)
    @Insert suspend fun insertSession(items: List<SessionItem>)
    @Update suspend fun updateSessionItem(i: SessionItem)
}

/** Whole-table reads/writes for backup & restore. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM quests") suspend fun quests(): List<Quest>
    @Query("SELECT * FROM xp_events") suspend fun xp(): List<XpEvent>
    @Query("SELECT * FROM plans") suspend fun plans(): List<Plan>
    @Query("SELECT * FROM checkins") suspend fun checkins(): List<CheckIn>
    @Query("SELECT * FROM food") suspend fun food(): List<FoodEntry>
    @Query("SELECT * FROM logs") suspend fun logs(): List<LogEntry>
    @Query("SELECT * FROM skills") suspend fun skills(): List<SkillItem>
    @Query("SELECT * FROM chat") suspend fun chat(): List<ChatMessage>
    @Query("SELECT * FROM memories") suspend fun memories(): List<Memory>
    @Query("SELECT * FROM discover") suspend fun discover(): List<DiscoverItem>
    @Query("SELECT * FROM sets") suspend fun sets(): List<WorkoutSet>
    @Query("SELECT * FROM health") suspend fun health(): List<HealthDay>
    @Query("SELECT * FROM body") suspend fun body(): List<BodyEntry>
    @Query("SELECT * FROM bosses") suspend fun bosses(): List<WeeklyBoss>
    @Query("SELECT * FROM achievements") suspend fun achievements(): List<AchievementUnlock>
    @Query("SELECT * FROM skill_progress") suspend fun progress(): List<SkillProgress>
    @Query("SELECT * FROM session_items") suspend fun sessionItems(): List<SessionItem>
    @Query("SELECT * FROM gold") suspend fun gold(): List<GoldEvent>
    @Query("SELECT * FROM rewards") suspend fun rewards(): List<Reward>
    @Query("SELECT * FROM savings") suspend fun savings(): List<SavingsEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putQuests(l: List<Quest>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putXp(l: List<XpEvent>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPlans(l: List<Plan>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCheckins(l: List<CheckIn>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putFood(l: List<FoodEntry>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putLogs(l: List<LogEntry>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSkills(l: List<SkillItem>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putChat(l: List<ChatMessage>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMemories(l: List<Memory>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDiscover(l: List<DiscoverItem>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSets(l: List<WorkoutSet>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHealth(l: List<HealthDay>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBody(l: List<BodyEntry>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBosses(l: List<WeeklyBoss>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAchievements(l: List<AchievementUnlock>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putProgress(l: List<SkillProgress>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSessionItems(l: List<SessionItem>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putGold(l: List<GoldEvent>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRewards(l: List<Reward>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSavings(l: List<SavingsEntry>)
}

@Database(
    entities = [Quest::class, XpEvent::class, Plan::class, CheckIn::class, FoodEntry::class,
        LogEntry::class, SkillItem::class, ChatMessage::class, Memory::class, DiscoverItem::class,
        WorkoutSet::class, HealthDay::class, BodyEntry::class, WeeklyBoss::class, AchievementUnlock::class,
        SkillProgress::class, SessionItem::class, GoldEvent::class, Reward::class, SavingsEntry::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDb : RoomDatabase() {
    abstract fun game(): GameDao
    abstract fun life(): LifeDao
    abstract fun fit(): FitDao
    abstract fun backup(): BackupDao
    abstract fun vault(): VaultDao

    companion object {
        @Volatile private var instance: AppDb? = null
        fun get(ctx: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "toplevel.db")
                .build().also { instance = it }
        }
    }
}
