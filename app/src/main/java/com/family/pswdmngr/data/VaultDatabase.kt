package com.family.pswdmngr.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class EntryCategory { WEBSITE_APP, BANK, CARD, NOTE, IDENTITY, WIFI }

/**
 * Parses a persisted category name. Tolerates the pre-v5 `LOGIN` spelling, which was
 * renamed to `WEBSITE_APP` — backups and trash rows written before v5 still carry it.
 */
fun entryCategoryFromName(name: String?): EntryCategory = when (name) {
    null, "" -> EntryCategory.WEBSITE_APP
    "LOGIN" -> EntryCategory.WEBSITE_APP
    else -> runCatching { EntryCategory.valueOf(name) }.getOrDefault(EntryCategory.WEBSITE_APP)
}

@Entity(tableName = "entries")
data class VaultEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: EntryCategory = EntryCategory.WEBSITE_APP,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val totpSecret: String = "", // base32, empty if none
    val tags: String = "", // comma-separated tags
    val favorite: Boolean = false,
    val lastUsedAt: Long = 0, // for "recently used" tracking
    val passwordStrength: Int = 0, // 0-100 score
    val isCompromised: Boolean = false, // flag for known breaches
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface VaultDao {
    @Query("SELECT * FROM entries ORDER BY favorite DESC, title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<VaultEntry>>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun byId(id: Long): VaultEntry?

    @Query("SELECT * FROM entries WHERE url LIKE '%' || :domain || '%' OR title LIKE '%' || :domain || '%'")
    suspend fun matchDomain(domain: String): List<VaultEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: VaultEntry): Long

    @Delete
    suspend fun delete(entry: VaultEntry)

    @Query("SELECT * FROM entries")
    suspend fun allOnce(): List<VaultEntry>
}

class Converters {
    @TypeConverter fun catToString(c: EntryCategory) = c.name
    @TypeConverter fun stringToCat(s: String) = EntryCategory.valueOf(s)
}

@Database(
    entities = [
        VaultEntry::class, CardEntry::class, BankEntry::class, DocumentEntry::class,
        Attachment::class, NoteEntry::class, TaskList::class, TaskItem::class,
        TrashItem::class, Reminder::class,
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun dao(): VaultDao
    abstract fun cardDao(): CardDao
    abstract fun bankDao(): BankDao
    abstract fun docDao(): DocDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun noteDao(): NoteDao
    abstract fun taskDao(): TaskDao
    abstract fun trashDao(): TrashDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        /** v1 (logins only) -> v2: adds cards, banks, documents, attachments, notes, tasks. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cards` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`label` TEXT NOT NULL, `bankName` TEXT NOT NULL, `cardType` TEXT NOT NULL, " +
                        "`network` TEXT NOT NULL, `number` TEXT NOT NULL, `holder` TEXT NOT NULL, " +
                        "`expiry` TEXT NOT NULL, `cvv` TEXT NOT NULL, `pin` TEXT NOT NULL, " +
                        "`fieldsJson` TEXT NOT NULL, `favorite` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `banks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`bankName` TEXT NOT NULL, `accountHolder` TEXT NOT NULL, `accountNumber` TEXT NOT NULL, " +
                        "`accountType` TEXT NOT NULL, `ifsc` TEXT NOT NULL, `branch` TEXT NOT NULL, " +
                        "`micr` TEXT NOT NULL, `cif` TEXT NOT NULL, `customerId` TEXT NOT NULL, " +
                        "`netbankingUserId` TEXT NOT NULL, `netbankingPassword` TEXT NOT NULL, " +
                        "`profilePassword` TEXT NOT NULL, `transactionPassword` TEXT NOT NULL, " +
                        "`upiPin` TEXT NOT NULL, `registeredMobile` TEXT NOT NULL, `fieldsJson` TEXT NOT NULL, " +
                        "`favorite` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `documents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `docType` TEXT NOT NULL, `number` TEXT NOT NULL, " +
                        "`holder` TEXT NOT NULL, `notes` TEXT NOT NULL, `fieldsJson` TEXT NOT NULL, " +
                        "`favorite` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`ownerType` TEXT NOT NULL, `ownerId` INTEGER NOT NULL, `displayName` TEXT NOT NULL, " +
                        "`mime` TEXT NOT NULL, `storedName` TEXT NOT NULL, `size` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `body` TEXT NOT NULL, `colorIdx` INTEGER NOT NULL, " +
                        "`pinned` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `task_lists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`listId` INTEGER NOT NULL, `parentId` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                        "`details` TEXT NOT NULL, `dueAt` INTEGER NOT NULL, `starred` INTEGER NOT NULL, " +
                        "`completed` INTEGER NOT NULL, `completedAt` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
            }
        }

        /** v2 -> v3: cards gain a catalog productId (real card appearance). */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `productId` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v3 -> v4: recycle bin (trash table). */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trash` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`itemType` TEXT NOT NULL, " +
                        "`originalId` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`dataJson` TEXT NOT NULL, " +
                        "`deletedAt` INTEGER NOT NULL, " +
                        "`expiresAt` INTEGER NOT NULL)"
                )
            }
        }

        /**
         * v4 -> v5: unified vault (tags / recently-used / strength / breach flag) + reminders.
         *
         * `entries` is rebuilt rather than ALTER TABLE ADD COLUMN so that every source
         * version lands on one identical table. `entries` is the only table reached by all
         * upgrade paths, and the rebuild states each new column's DEFAULT explicitly instead
         * of inheriting whatever ADD COLUMN appends. (Room's TableInfo check is name-keyed
         * with no ordinal field, so ADD COLUMN would in fact have validated; the rebuild is
         * for uniformity, not to satisfy validation.)
         *
         * Room runs migrations in a transaction, so a failed attempt rolls back and the DB
         * stays at its source version — rebuilding unconditionally is safe and self-correcting.
         */
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE `entries_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`username` TEXT NOT NULL, " +
                        "`password` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`notes` TEXT NOT NULL, " +
                        "`totpSecret` TEXT NOT NULL DEFAULT '', " +
                        "`tags` TEXT NOT NULL DEFAULT '', " +
                        "`favorite` INTEGER NOT NULL, " +
                        "`lastUsedAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`passwordStrength` INTEGER NOT NULL DEFAULT 0, " +
                        "`isCompromised` INTEGER NOT NULL DEFAULT 0, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
                // v1 already shipped totpSecret, so this guard should always be true; it stays
                // as a cheap defence against a hand-edited or partially-migrated DB.
                val hasTotp = db.query("SELECT 1 FROM pragma_table_info('entries') WHERE name = 'totpSecret'")
                    .use { it.moveToFirst() }
                db.execSQL(
                    "INSERT INTO `entries_new` (`id`, `title`, `category`, `username`, `password`, " +
                        "`url`, `notes`, `totpSecret`, `favorite`, `createdAt`, `updatedAt`) " +
                        "SELECT `id`, `title`, `category`, `username`, `password`, `url`, `notes`, " +
                        (if (hasTotp) "`totpSecret`" else "''") + ", `favorite`, `createdAt`, `updatedAt` " +
                        "FROM `entries`"
                )
                db.execSQL("DROP TABLE `entries`")
                db.execSQL("ALTER TABLE `entries_new` RENAME TO `entries`")

                // `LOGIN` was renamed to `WEBSITE_APP` in v5; v1 also allowed IDENTITY on WiFi.
                db.execSQL("UPDATE `entries` SET `category` = 'WEBSITE_APP' WHERE `category` = 'LOGIN'")
                db.execSQL("UPDATE `entries` SET `category` = 'WIFI' WHERE `category` = 'IDENTITY'")

                // Create reminders table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reminders` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`reminderType` TEXT NOT NULL, " +
                        "`linkedItemType` TEXT NOT NULL, " +
                        "`linkedItemId` INTEGER NOT NULL, " +
                        "`dueAt` INTEGER NOT NULL, " +
                        "`completed` INTEGER NOT NULL, " +
                        "`completedAt` INTEGER NOT NULL, " +
                        "`recurring` INTEGER NOT NULL, " +
                        "`recurringIntervalDays` INTEGER NOT NULL, " +
                        "`notes` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
            }
        }
    }
}
