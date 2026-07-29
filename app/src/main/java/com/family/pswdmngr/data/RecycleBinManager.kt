package com.family.pswdmngr.data

import kotlinx.coroutines.flow.first

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
            is VaultEntry -> listOf(TrashType.LOGIN, item.id, item.title, item.toTrashJson())
            is CardEntry -> listOf(TrashType.CARD, item.id, item.label.ifBlank { "Card" }, item.toTrashJson())
            is BankEntry -> listOf(TrashType.BANK, item.id, item.bankName, item.toTrashJson())
            is DocumentEntry -> listOf(TrashType.DOC, item.id, item.title.ifBlank { DocType.label(item.docType) }, item.toTrashJson())
            is NoteEntry -> listOf(TrashType.NOTE, item.id, item.title, item.toTrashJson())
            is TaskItem -> listOf(TrashType.TASK, item.id, item.title, item.toTrashJson())
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
            TrashType.LOGIN -> VaultSession.dao().upsert(vaultEntryFromJson(trashItem.dataJson).copy(id = 0))
            TrashType.CARD -> VaultSession.cardDao().upsert(cardEntryFromJson(trashItem.dataJson).copy(id = 0))
            TrashType.BANK -> VaultSession.bankDao().upsert(bankEntryFromJson(trashItem.dataJson).copy(id = 0))
            TrashType.DOC -> VaultSession.docDao().upsert(documentEntryFromJson(trashItem.dataJson).copy(id = 0))
            TrashType.NOTE -> VaultSession.noteDao().upsert(noteEntryFromJson(trashItem.dataJson).copy(id = 0))
            TrashType.TASK -> VaultSession.taskDao().upsertTask(taskItemFromJson(trashItem.dataJson).copy(id = 0))
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
