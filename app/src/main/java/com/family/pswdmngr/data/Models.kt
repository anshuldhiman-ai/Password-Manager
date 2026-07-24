package com.family.pswdmngr.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

/* ---------- Custom fields (user-added columns on any record) ---------- */

data class CustomField(val label: String, val value: String, val secret: Boolean)

fun parseFields(json: String): List<CustomField> = try {
    val arr = JSONArray(json.ifBlank { "[]" })
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        CustomField(o.optString("label"), o.optString("value"), o.optBoolean("secret"))
    }
} catch (e: Exception) { emptyList() }

fun fieldsToJson(fields: List<CustomField>): String = JSONArray().apply {
    fields.forEach { f ->
        put(JSONObject().apply {
            put("label", f.label); put("value", f.value); put("secret", f.secret)
        })
    }
}.toString()

/* ---------- Cards ---------- */

object CardType {
    const val DEBIT = "DEBIT"; const val CREDIT = "CREDIT"; const val PREPAID = "PREPAID"
    const val CSD = "CSD"; const val OTHER = "OTHER"
    /** The personal-bank wallet is intentionally limited to debit and credit cards.
     * CSD is managed in its own area, so it never clashes with bank-card entry. */
    val ALL = listOf(DEBIT, CREDIT)
    fun label(t: String) = when (t) {
        DEBIT -> "Debit"; CREDIT -> "Credit"; PREPAID -> "Prepaid"
        CSD -> "CSD Canteen"; else -> "Other"
    }
}

@Entity(tableName = "cards")
data class CardEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String = "",
    val bankName: String = "",
    val cardType: String = CardType.DEBIT,
    val network: String = "AUTO", // AUTO = detect from number
    val productId: String = "",  // catalog product (e.g. hdfc_c_millennia) for real card art
    val number: String = "",
    val holder: String = "",
    val expiry: String = "",     // MM/YY
    val cvv: String = "",
    val pin: String = "",
    val serialNo: String = "",   // CSD canteen card serial number
    val fieldsJson: String = "[]",
    val favorite: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY favorite DESC, bankName COLLATE NOCASE ASC, label COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CardEntry>>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun byId(id: Long): CardEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: CardEntry): Long

    @Delete
    suspend fun delete(card: CardEntry)

    @Query("SELECT * FROM cards")
    suspend fun allOnce(): List<CardEntry>
}

/* ---------- Bank accounts ---------- */

object AccountType {
    val ALL = listOf("SAVINGS", "CURRENT", "SALARY", "NRI", "PPF", "FD/RD", "OTHER")
    fun label(t: String) = when (t) {
        "SAVINGS" -> "Savings"; "CURRENT" -> "Current"; "SALARY" -> "Salary"
        "NRI" -> "NRI"; "PPF" -> "PPF"; "FD/RD" -> "FD / RD"; else -> "Other"
    }
}

@Entity(tableName = "banks")
data class BankEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankName: String = "",
    val accountHolder: String = "",
    val accountNumber: String = "",
    val accountType: String = "SAVINGS",
    val ifsc: String = "",
    val branch: String = "",
    val micr: String = "",
    val cif: String = "",
    val customerId: String = "",
    val netbankingUserId: String = "",
    val netbankingPassword: String = "",
    val profilePassword: String = "",
    val transactionPassword: String = "",
    val upiPin: String = "",
    val registeredMobile: String = "",
    val fieldsJson: String = "[]",
    val favorite: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Dao
interface BankDao {
    @Query("SELECT * FROM banks ORDER BY favorite DESC, bankName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<BankEntry>>

    @Query("SELECT * FROM banks WHERE id = :id")
    suspend fun byId(id: Long): BankEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bank: BankEntry): Long

    @Delete
    suspend fun delete(bank: BankEntry)

    @Query("SELECT * FROM banks")
    suspend fun allOnce(): List<BankEntry>
}

/* ---------- Documents (Aadhaar, PAN, trading, ...) ---------- */

object DocType {
    val ALL = listOf(
        "AADHAAR", "PAN", "PASSPORT", "DRIVING_LICENSE", "VOTER_ID",
        "TRADING", "INSURANCE", "VEHICLE", "PROPERTY", "OTHER",
    )
    fun label(t: String) = when (t) {
        "AADHAAR" -> "Aadhaar"; "PAN" -> "PAN Card"; "PASSPORT" -> "Passport"
        "DRIVING_LICENSE" -> "Driving Licence"; "VOTER_ID" -> "Voter ID"
        "TRADING" -> "Trading / Demat"; "INSURANCE" -> "Insurance"
        "VEHICLE" -> "Vehicle"; "PROPERTY" -> "Property"; else -> "Other"
    }
    /** Suggested extra fields per document type, offered as one-tap chips. */
    fun suggestedFields(t: String): List<String> = when (t) {
        "AADHAAR" -> listOf("VID", "Enrolment No", "Linked Mobile")
        "PAN" -> listOf("Date of Birth", "Father's Name")
        "PASSPORT" -> listOf("File No", "Issue Date", "Expiry Date", "Place of Issue")
        "DRIVING_LICENSE" -> listOf("Valid Till", "Vehicle Class", "Issued By")
        "TRADING" -> listOf("Broker", "Client ID", "Login Password", "T-PIN / TPIN", "DP ID", "BO ID (Demat No)", "MPIN")
        "INSURANCE" -> listOf("Policy Term", "Premium", "Due Date", "Nominee")
        "VEHICLE" -> listOf("Registration No", "Chassis No", "Engine No", "Insurance Valid Till")
        else -> emptyList()
    }
}

@Entity(tableName = "documents")
data class DocumentEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val docType: String = "OTHER",
    val number: String = "",   // Aadhaar no / PAN / policy no / client id ...
    val holder: String = "",
    val notes: String = "",
    val fieldsJson: String = "[]",
    val favorite: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Dao
interface DocDao {
    @Query("SELECT * FROM documents ORDER BY favorite DESC, title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<DocumentEntry>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun byId(id: Long): DocumentEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(doc: DocumentEntry): Long

    @Delete
    suspend fun delete(doc: DocumentEntry)

    @Query("SELECT * FROM documents")
    suspend fun allOnce(): List<DocumentEntry>
}

/* ---------- Encrypted attachments (images / PDFs on documents) ---------- */

@Entity(tableName = "attachments")
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerType: String = "DOC",
    val ownerId: Long = 0,
    val displayName: String = "",
    val mime: String = "",
    val storedName: String = "", // encrypted blob file inside app storage
    val size: Long = 0,
    val createdAt: Long = 0,
)

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY createdAt ASC")
    fun observeForOwner(ownerType: String, ownerId: Long): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun forOwnerOnce(ownerType: String, ownerId: Long): List<Attachment>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun byId(id: Long): Attachment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(a: Attachment): Long

    @Delete
    suspend fun delete(a: Attachment)

    @Query("SELECT * FROM attachments")
    suspend fun allOnce(): List<Attachment>
}

/* ---------- Secret notes ---------- */

@Entity(tableName = "notes")
data class NoteEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val colorIdx: Int = 0,
    val pinned: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntry>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun byId(id: Long): NoteEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntry): Long

    @Delete
    suspend fun delete(note: NoteEntry)

    @Query("SELECT * FROM notes")
    suspend fun allOnce(): List<NoteEntry>
}

/* ---------- Recycle Bin ---------- */

/** Item type stored in the trash. */
object TrashType {
    const val LOGIN = "LOGIN"; const val CARD = "CARD"; const val BANK = "BANK"
    const val DOC = "DOC"; const val NOTE = "NOTE"; const val TASK = "TASK"
    val ALL = listOf(LOGIN, CARD, BANK, DOC, NOTE, TASK)
    const val DAYS_UNTIL_PURGE = 30
}

@Entity(tableName = "trash")
data class TrashItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemType: String = TrashType.LOGIN,
    val originalId: Long = 0,       // the item's original PK (for potential restore)
    val title: String = "",         // human-readable title for the list
    val dataJson: String = "",      // full JSON of the deleted item
    val deletedAt: Long = 0,
    val expiresAt: Long = 0,       // auto-purge after this timestamp
)

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash ORDER BY deletedAt DESC")
    fun observeAll(): Flow<List<TrashItem>>

    @Query("SELECT * FROM trash WHERE id = :id")
    suspend fun byId(id: Long): TrashItem?

    @Query("SELECT COUNT(*) FROM trash")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TrashItem): Long

    @Delete
    suspend fun delete(item: TrashItem)

    @Query("DELETE FROM trash WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long): Int

    @Query("DELETE FROM trash")
    suspend fun emptyAll()
}

/* ---------- Tasks (Google-Tasks style) ---------- */

@Entity(tableName = "task_lists")
data class TaskList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val position: Int = 0,
    val createdAt: Long = 0,
)

@Entity(tableName = "tasks")
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long = 0,
    val parentId: Long = 0,     // 0 = top level, otherwise subtask of that task
    val title: String = "",
    val details: String = "",
    val dueAt: Long = 0,        // 0 = no due date
    val starred: Boolean = false,
    val completed: Boolean = false,
    val completedAt: Long = 0,
    val position: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM task_lists ORDER BY position ASC, id ASC")
    fun observeLists(): Flow<List<TaskList>>

    @Query("SELECT * FROM tasks WHERE listId = :listId ORDER BY completed ASC, position ASC, id DESC")
    fun observeTasks(listId: Long): Flow<List<TaskItem>>

    @Query("SELECT * FROM tasks WHERE starred = 1 AND completed = 0 ORDER BY dueAt ASC")
    fun observeStarred(): Flow<List<TaskItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertList(list: TaskList): Long

    @Delete
    suspend fun deleteList(list: TaskList)

    @Query("DELETE FROM tasks WHERE listId = :listId")
    suspend fun deleteTasksInList(listId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskItem): Long

    @Delete
    suspend fun deleteTask(task: TaskItem)

    @Query("DELETE FROM tasks WHERE parentId = :taskId")
    suspend fun deleteSubtasks(taskId: Long)

    @Query("SELECT * FROM tasks WHERE parentId = :taskId")
    suspend fun subtasksOnce(taskId: Long): List<TaskItem>

    @Query("SELECT * FROM task_lists")
    suspend fun allListsOnce(): List<TaskList>

    @Query("SELECT * FROM tasks")
    suspend fun allTasksOnce(): List<TaskItem>

    @Query("SELECT COUNT(*) FROM tasks WHERE listId = :listId AND completed = 0")
    fun observePendingCount(listId: Long): Flow<Int>
}
