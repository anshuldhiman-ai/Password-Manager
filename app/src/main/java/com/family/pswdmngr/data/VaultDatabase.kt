package com.family.pswdmngr.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class EntryCategory { LOGIN, CARD, NOTE, IDENTITY, WIFI }

@Entity(tableName = "entries")
data class VaultEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: EntryCategory = EntryCategory.LOGIN,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val totpSecret: String = "", // base32, empty if none
    val favorite: Boolean = false,
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
        TrashItem::class,
    ],
    version = 4,
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

    companion object {
        /** v1 (logins only) -> v2: adds cards, banks, documents, attachments, notes, tasks. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cards` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`label` TEXT NOT NULL, `bankName` TEXT NOT NULL, `cardType` TEXT NOT NULL, " +
                        "`network` TEXT NOT NULL, `number` TEXT NOT NULL, `holder` TEXT NOT NULL, " +
                        "`expiry` TEXT NOT NULL, `cvv` TEXT NOT NULL, `pin` TEXT NOT NULL, " +
                        "`serialNo` TEXT NOT NULL, `fieldsJson` TEXT NOT NULL, `favorite` INTEGER NOT NULL, " +
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
    }
}
