package com.family.pswdmngr.data

import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * Handles moving items to the recycle bin (trash) instead of permanent deletion,
 * and managing the 30-day auto-purge.
 *
 * The trash table lives inside the encrypted SQLCipher vault, so deleted items
 * remain encrypted at rest until purged.
 */
object RecycleBinManager {

    /**
     * Move any deletable entity to the trash.
     * Supported types: VaultEntry, CardEntry, BankEntry, DocumentEntry, NoteEntry, TaskItem.
     */
    suspend fun trash(ctx: android.content.Context, item: Any) {
        val now = System.currentTimeMillis()
        val expireAt = now + TrashType.DAYS_UNTIL_PURGE * 24L * 60L * 60L * 1000L

        val (type, originalId, title, json) = when (item) {
            is VaultEntry -> listOf(TrashType.LOGIN, item.id, item.title, entryToJson(item))
            is CardEntry -> listOf(TrashType.CARD, item.id, item.label.ifBlank { "Card" }, cardToJson(item))
            is BankEntry -> listOf(TrashType.BANK, item.id, item.bankName, bankToJson(item))
            is DocumentEntry -> listOf(TrashType.DOC, item.id, item.title.ifBlank { DocType.label(item.docType) }, docToJson(item))
            is NoteEntry -> listOf(TrashType.NOTE, item.id, item.title, noteToJson(item))
            is TaskItem -> listOf(TrashType.TASK, item.id, item.title, taskToJson(item))
            else -> return
        }

        val trashItem = TrashItem(
            itemType = type as String,
            originalId = originalId as Long,
            title = title as String,
            dataJson = json as String,
            deletedAt = now,
            expiresAt = expireAt,
        )
        VaultSession.trashDao().insert(trashItem)
    }

    /** Permanently delete the original item AND trash it. Call after [trash]. */
    suspend fun deleteOriginal(item: Any) {
        when (item) {
            is VaultEntry -> VaultSession.dao().delete(item)
            is CardEntry -> VaultSession.cardDao().delete(item)
            is BankEntry -> VaultSession.bankDao().delete(item)
            is DocumentEntry -> VaultSession.docDao().delete(item)
            is NoteEntry -> VaultSession.noteDao().delete(item)
            is TaskItem -> VaultSession.taskDao().deleteTask(item)
        }
    }

    /** Permanently delete the original item by type/id. Used by restore flow. */
    suspend fun deleteOriginalById(type: String, originalId: Long) {
        when (type) {
            TrashType.LOGIN -> VaultSession.dao().byId(originalId)?.let { VaultSession.dao().delete(it) }
            TrashType.CARD -> VaultSession.cardDao().byId(originalId)?.let { VaultSession.cardDao().delete(it) }
            TrashType.BANK -> VaultSession.bankDao().byId(originalId)?.let { VaultSession.bankDao().delete(it) }
            TrashType.DOC -> VaultSession.docDao().byId(originalId)?.let { VaultSession.docDao().delete(it) }
        }
    }

    /** Restore a trashed item to its original table. Call with the trash item. */
    suspend fun restore(trashItem: TrashItem) {
        when (trashItem.itemType) {
            TrashType.LOGIN -> {
                val entry = entryFromJson(trashItem.dataJson)
                VaultSession.dao().upsert(entry.copy(id = 0))
            }
            TrashType.CARD -> {
                val card = cardFromJson(trashItem.dataJson)
                VaultSession.cardDao().upsert(card.copy(id = 0))
            }
            TrashType.BANK -> {
                val bank = bankFromJson(trashItem.dataJson)
                VaultSession.bankDao().upsert(bank.copy(id = 0))
            }
            TrashType.DOC -> {
                val doc = docFromJson(trashItem.dataJson)
                VaultSession.docDao().upsert(doc.copy(id = 0))
            }
            TrashType.NOTE -> {
                val note = noteFromJson(trashItem.dataJson)
                VaultSession.noteDao().upsert(note.copy(id = 0))
            }
            TrashType.TASK -> {
                val task = taskFromJson(trashItem.dataJson)
                VaultSession.taskDao().upsertTask(task.copy(id = 0))
            }
        }
        deleteOriginalById(trashItem.itemType, trashItem.originalId)
        VaultSession.trashDao().delete(trashItem)
    }

    /** Purge all expired trash items. Call on vault unlock. */
    suspend fun purgeExpired() {
        VaultSession.trashDao().deleteExpired(System.currentTimeMillis())
    }

    /** Empty the entire trash permanently. */
    suspend fun emptyAll() {
        VaultSession.trashDao().emptyAll()
    }

    // ── JSON serialization for each type ──

    private fun entryToJson(e: VaultEntry) = JSONObject().apply {
        put("title", e.title); put("category", e.category.name)
        put("username", e.username); put("password", e.password)
        put("url", e.url); put("notes", e.notes); put("totp", e.totpSecret)
        put("favorite", e.favorite); put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
    }.toString()

    private fun cardToJson(c: CardEntry) = JSONObject().apply {
        put("label", c.label); put("bankName", c.bankName); put("cardType", c.cardType)
        put("network", c.network); put("productId", c.productId)
        put("number", c.number); put("holder", c.holder); put("expiry", c.expiry)
        put("cvv", c.cvv); put("pin", c.pin); put("serialNo", c.serialNo)
        put("fieldsJson", c.fieldsJson); put("favorite", c.favorite)
        put("createdAt", c.createdAt); put("updatedAt", c.updatedAt)
    }.toString()

    private fun bankToJson(b: BankEntry) = JSONObject().apply {
        put("bankName", b.bankName); put("accountHolder", b.accountHolder)
        put("accountNumber", b.accountNumber); put("accountType", b.accountType)
        put("ifsc", b.ifsc); put("branch", b.branch); put("micr", b.micr)
        put("cif", b.cif); put("customerId", b.customerId)
        put("nbUser", b.netbankingUserId); put("nbPass", b.netbankingPassword)
        put("profilePass", b.profilePassword); put("txnPass", b.transactionPassword)
        put("upiPin", b.upiPin); put("mobile", b.registeredMobile)
        put("fieldsJson", b.fieldsJson); put("favorite", b.favorite)
        put("createdAt", b.createdAt); put("updatedAt", b.updatedAt)
    }.toString()

    private fun docToJson(d: DocumentEntry) = JSONObject().apply {
        put("title", d.title); put("docType", d.docType); put("number", d.number)
        put("holder", d.holder); put("notes", d.notes); put("fieldsJson", d.fieldsJson)
        put("favorite", d.favorite); put("createdAt", d.createdAt); put("updatedAt", d.updatedAt)
    }.toString()

    private fun noteToJson(n: NoteEntry) = JSONObject().apply {
        put("title", n.title); put("body", n.body); put("colorIdx", n.colorIdx)
        put("pinned", n.pinned); put("createdAt", n.createdAt); put("updatedAt", n.updatedAt)
    }.toString()

    private fun taskToJson(t: TaskItem) = JSONObject().apply {
        put("listId", t.listId); put("parentId", t.parentId); put("title", t.title)
        put("details", t.details); put("dueAt", t.dueAt); put("starred", t.starred)
        put("completed", t.completed); put("completedAt", t.completedAt)
        put("position", t.position); put("createdAt", t.createdAt); put("updatedAt", t.updatedAt)
    }.toString()

    // ── JSON deserialization ──

    private fun entryFromJson(json: String): VaultEntry {
        val o = JSONObject(json)
        return VaultEntry(
            title = o.getString("title"), category = EntryCategory.valueOf(o.optString("category", "LOGIN")),
            username = o.optString("username"), password = o.optString("password"),
            url = o.optString("url"), notes = o.optString("notes"),
            totpSecret = o.optString("totp"), favorite = o.optBoolean("favorite"),
            createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"),
        )
    }

    private fun cardFromJson(json: String): CardEntry {
        val o = JSONObject(json)
        return CardEntry(
            label = o.optString("label"), bankName = o.optString("bankName"),
            cardType = o.optString("cardType", CardType.DEBIT),
            network = o.optString("network", "AUTO"), productId = o.optString("productId"),
            number = o.optString("number"), holder = o.optString("holder"),
            expiry = o.optString("expiry"), cvv = o.optString("cvv"), pin = o.optString("pin"),
            serialNo = o.optString("serialNo"), fieldsJson = o.optString("fieldsJson", "[]"),
            favorite = o.optBoolean("favorite"), createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
        )
    }

    private fun bankFromJson(json: String): BankEntry {
        val o = JSONObject(json)
        return BankEntry(
            bankName = o.optString("bankName"), accountHolder = o.optString("accountHolder"),
            accountNumber = o.optString("accountNumber"),
            accountType = o.optString("accountType", "SAVINGS"),
            ifsc = o.optString("ifsc"), branch = o.optString("branch"),
            micr = o.optString("micr"), cif = o.optString("cif"),
            customerId = o.optString("customerId"), netbankingUserId = o.optString("nbUser"),
            netbankingPassword = o.optString("nbPass"), profilePassword = o.optString("profilePass"),
            transactionPassword = o.optString("txnPass"), upiPin = o.optString("upiPin"),
            registeredMobile = o.optString("mobile"), fieldsJson = o.optString("fieldsJson", "[]"),
            favorite = o.optBoolean("favorite"), createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
        )
    }

    private fun docFromJson(json: String): DocumentEntry {
        val o = JSONObject(json)
        return DocumentEntry(
            title = o.optString("title"), docType = o.optString("docType", "OTHER"),
            number = o.optString("number"), holder = o.optString("holder"),
            notes = o.optString("notes"), fieldsJson = o.optString("fieldsJson", "[]"),
            favorite = o.optBoolean("favorite"), createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
        )
    }

    private fun noteFromJson(json: String): NoteEntry {
        val o = JSONObject(json)
        return NoteEntry(
            title = o.optString("title"), body = o.optString("body"),
            colorIdx = o.optInt("colorIdx"), pinned = o.optBoolean("pinned"),
            createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"),
        )
    }

    private fun taskFromJson(json: String): TaskItem {
        val o = JSONObject(json)
        return TaskItem(
            listId = o.optLong("listId"), parentId = o.optLong("parentId"),
            title = o.optString("title"), details = o.optString("details"),
            dueAt = o.optLong("dueAt"), starred = o.optBoolean("starred"),
            completed = o.optBoolean("completed"), completedAt = o.optLong("completedAt"),
            position = o.optInt("position"), createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
        )
    }
}

/**
 * Helper extension to delete an item to trash instead of permanently.
 * Call this instead of direct dao.delete().
 */
suspend fun VaultEntry.deleteToTrash(ctx: android.content.Context) {
    RecycleBinManager.trash(ctx, this)
    VaultSession.dao().delete(this)
}

suspend fun CardEntry.deleteToTrash(ctx: android.content.Context) {
    RecycleBinManager.trash(ctx, this)
    VaultSession.cardDao().delete(this)
}

suspend fun BankEntry.deleteToTrash(ctx: android.content.Context) {
    RecycleBinManager.trash(ctx, this)
    VaultSession.bankDao().delete(this)
}

suspend fun DocumentEntry.deleteToTrash(ctx: android.content.Context) {
    // Also trash attachments
    val atts = VaultSession.attachmentDao().forOwnerOnce("DOC", this.id)
    for (a in atts) {
        com.family.pswdmngr.data.AttachmentStore.delete(ctx, a)
        VaultSession.attachmentDao().delete(a)
    }
    RecycleBinManager.trash(ctx, this)
    VaultSession.docDao().delete(this)
}

suspend fun NoteEntry.deleteToTrash(ctx: android.content.Context) {
    RecycleBinManager.trash(ctx, this)
    VaultSession.noteDao().delete(this)
}
