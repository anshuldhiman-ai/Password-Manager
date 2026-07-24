package com.family.pswdmngr.data

import android.content.Context
import android.net.Uri
import com.family.pswdmngr.crypto.VaultCrypto
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Encrypted backup of the FULL vault (logins, cards, banks, documents,
 * attachments, notes, tasks) → AES-256-GCM under a key derived from a
 * backup password (own salt, Argon2id). Without the password the file is
 * indistinguishable from random data past the magic header.
 *
 * v2 format: magic "PSWDMGR1"(8) | version=2(1) | salt(16) | gcm blob
 * v1 files (entries-only) still import.
 */
object BackupManager {

    private val MAGIC = "PSWDMGR1".toByteArray()
    private val ALL_CATEGORIES = setOf("entries", "cards", "banks", "documents", "notes", "tasks")

    /**
     * Full export — all categories.
     */
    suspend fun export(ctx: Context, dest: Uri, backupPassword: CharArray) =
        export(ctx, dest, backupPassword, ALL_CATEGORIES)

    /**
     * Selective export — only the specified [categories].
     * Category keys: "entries", "cards", "banks", "documents", "notes", "tasks"
     */
    suspend fun export(ctx: Context, dest: Uri, backupPassword: CharArray, categories: Set<String>) {
        val root = JSONObject()

        if ("entries" in categories) {
            root.put("entries", JSONArray().apply {
                VaultSession.dao().allOnce().forEach { e ->
                    put(JSONObject().apply {
                        put("title", e.title); put("category", e.category.name)
                        put("username", e.username); put("password", e.password)
                        put("url", e.url); put("notes", e.notes)
                        put("totp", e.totpSecret); put("favorite", e.favorite)
                        put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
                    })
                }
            })
        }

        if ("cards" in categories) {
            root.put("cards", JSONArray().apply {
                VaultSession.cardDao().allOnce().forEach { c ->
                    put(JSONObject().apply {
                        put("label", c.label); put("bankName", c.bankName)
                        put("cardType", c.cardType); put("network", c.network)
                        put("productId", c.productId)
                        put("number", c.number); put("holder", c.holder)
                        put("expiry", c.expiry); put("cvv", c.cvv); put("pin", c.pin)
                        put("serialNo", c.serialNo); put("fields", c.fieldsJson)
                        put("favorite", c.favorite)
                        put("createdAt", c.createdAt); put("updatedAt", c.updatedAt)
                    })
                }
            })
        }

        if ("banks" in categories) {
            root.put("banks", JSONArray().apply {
                VaultSession.bankDao().allOnce().forEach { b ->
                    put(JSONObject().apply {
                        put("bankName", b.bankName); put("accountHolder", b.accountHolder)
                        put("accountNumber", b.accountNumber); put("accountType", b.accountType)
                        put("ifsc", b.ifsc); put("branch", b.branch); put("micr", b.micr)
                        put("cif", b.cif); put("customerId", b.customerId)
                        put("nbUser", b.netbankingUserId); put("nbPass", b.netbankingPassword)
                        put("profilePass", b.profilePassword); put("txnPass", b.transactionPassword)
                        put("upiPin", b.upiPin); put("mobile", b.registeredMobile)
                        put("fields", b.fieldsJson); put("favorite", b.favorite)
                        put("createdAt", b.createdAt); put("updatedAt", b.updatedAt)
                    })
                }
            })
        }

        if ("documents" in categories) {
            root.put("documents", JSONArray().apply {
                val attDao = VaultSession.attachmentDao()
                VaultSession.docDao().allOnce().forEach { d ->
                    put(JSONObject().apply {
                        put("title", d.title); put("docType", d.docType)
                        put("number", d.number); put("holder", d.holder)
                        put("notes", d.notes); put("fields", d.fieldsJson)
                        put("favorite", d.favorite)
                        put("createdAt", d.createdAt); put("updatedAt", d.updatedAt)
                        put("attachments", JSONArray().apply {
                            attDao.forOwnerOnce("DOC", d.id).forEach { a ->
                                val bytes = AttachmentStore.readBytes(ctx, a)
                                if (bytes != null) {
                                    put(JSONObject().apply {
                                        put("name", a.displayName); put("mime", a.mime)
                                        put("data", android.util.Base64.encodeToString(
                                            bytes, android.util.Base64.NO_WRAP))
                                    })
                                    VaultCrypto.wipe(bytes)
                                }
                            }
                        })
                    })
                }
            })
        }

        if ("notes" in categories) {
            root.put("notes", JSONArray().apply {
                VaultSession.noteDao().allOnce().forEach { n ->
                    put(JSONObject().apply {
                        put("title", n.title); put("body", n.body)
                        put("colorIdx", n.colorIdx); put("pinned", n.pinned)
                        put("createdAt", n.createdAt); put("updatedAt", n.updatedAt)
                    })
                }
            })
        }

        if ("tasks" in categories) {
            val taskDao = VaultSession.taskDao()
            root.put("taskLists", JSONArray().apply {
                taskDao.allListsOnce().forEach { l ->
                    put(JSONObject().apply {
                        put("id", l.id); put("name", l.name); put("position", l.position)
                        put("createdAt", l.createdAt)
                    })
                }
            })
            root.put("tasks", JSONArray().apply {
                taskDao.allTasksOnce().forEach { t ->
                    put(JSONObject().apply {
                        put("id", t.id); put("listId", t.listId); put("parentId", t.parentId)
                        put("title", t.title); put("details", t.details); put("dueAt", t.dueAt)
                        put("starred", t.starred); put("completed", t.completed)
                        put("completedAt", t.completedAt); put("position", t.position)
                        put("createdAt", t.createdAt); put("updatedAt", t.updatedAt)
                    })
                }
            })
        }

        val json = root.toString().toByteArray()
        val salt = VaultCrypto.randomBytes(16)
        val pw = VaultCrypto.charsToBytes(backupPassword)
        val key = VaultCrypto.deriveKey(pw, salt)
        VaultCrypto.wipe(pw)
        val blob = VaultCrypto.encrypt(key, json, aad = MAGIC)
        VaultCrypto.wipe(key, json)

        ctx.contentResolver.openOutputStream(dest)!!.use { out ->
            out.write(MAGIC); out.write(2); out.write(salt); out.write(blob)
        }
    }

    /** Returns number of imported records. Throws on wrong password/corrupt file. */
    suspend fun import(ctx: Context, src: Uri, backupPassword: CharArray): Int {
        val bytes = ctx.contentResolver.openInputStream(src)!!.use { it.readBytes() }
        require(bytes.size > 25 && bytes.copyOfRange(0, 8).contentEquals(MAGIC)) { "Not a PSWD MNGR backup" }
        val version = bytes[8].toInt()
        val salt = bytes.copyOfRange(9, 25)
        val blob = bytes.copyOfRange(25, bytes.size)

        val pw = VaultCrypto.charsToBytes(backupPassword)
        val key = VaultCrypto.deriveKey(pw, salt)
        VaultCrypto.wipe(pw)
        val json = try {
            VaultCrypto.decrypt(key, blob, aad = MAGIC)
        } finally {
            VaultCrypto.wipe(key)
        }

        return try {
            if (version >= 2) importV2(ctx, JSONObject(String(json)))
            else importV1(JSONArray(String(json)))
        } finally {
            VaultCrypto.wipe(json)
        }
    }

    private suspend fun importV1(arr: JSONArray): Int {
        val dao = VaultSession.dao()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            dao.upsert(
                VaultEntry(
                    title = o.getString("title"),
                    category = EntryCategory.valueOf(o.optString("category", "LOGIN")),
                    username = o.optString("username"),
                    password = o.optString("password"),
                    url = o.optString("url"),
                    notes = o.optString("notes"),
                    totpSecret = o.optString("totp"),
                    favorite = o.optBoolean("favorite"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                )
            )
        }
        return arr.length()
    }

    private suspend fun importV2(ctx: Context, root: JSONObject): Int {
        var count = 0
        val now = System.currentTimeMillis()

        root.optJSONArray("entries")?.let { count += importV1(it) }

        root.optJSONArray("cards")?.let { arr ->
            val dao = VaultSession.cardDao()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                dao.upsert(CardEntry(
                    label = o.optString("label"), bankName = o.optString("bankName"),
                    cardType = o.optString("cardType", CardType.DEBIT),
                    network = o.optString("network", "AUTO"),
                    productId = o.optString("productId"),
                    number = o.optString("number"), holder = o.optString("holder"),
                    expiry = o.optString("expiry"), cvv = o.optString("cvv"),
                    pin = o.optString("pin"), serialNo = o.optString("serialNo"),
                    fieldsJson = o.optString("fields", "[]"),
                    favorite = o.optBoolean("favorite"),
                    createdAt = o.optLong("createdAt", now), updatedAt = o.optLong("updatedAt", now),
                ))
                count++
            }
        }

        root.optJSONArray("banks")?.let { arr ->
            val dao = VaultSession.bankDao()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                dao.upsert(BankEntry(
                    bankName = o.optString("bankName"), accountHolder = o.optString("accountHolder"),
                    accountNumber = o.optString("accountNumber"),
                    accountType = o.optString("accountType", "SAVINGS"),
                    ifsc = o.optString("ifsc"), branch = o.optString("branch"),
                    micr = o.optString("micr"), cif = o.optString("cif"),
                    customerId = o.optString("customerId"),
                    netbankingUserId = o.optString("nbUser"),
                    netbankingPassword = o.optString("nbPass"),
                    profilePassword = o.optString("profilePass"),
                    transactionPassword = o.optString("txnPass"),
                    upiPin = o.optString("upiPin"), registeredMobile = o.optString("mobile"),
                    fieldsJson = o.optString("fields", "[]"),
                    favorite = o.optBoolean("favorite"),
                    createdAt = o.optLong("createdAt", now), updatedAt = o.optLong("updatedAt", now),
                ))
                count++
            }
        }

        root.optJSONArray("documents")?.let { arr ->
            val dao = VaultSession.docDao()
            val attDao = VaultSession.attachmentDao()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val docId = dao.upsert(DocumentEntry(
                    title = o.optString("title"), docType = o.optString("docType", "OTHER"),
                    number = o.optString("number"), holder = o.optString("holder"),
                    notes = o.optString("notes"), fieldsJson = o.optString("fields", "[]"),
                    favorite = o.optBoolean("favorite"),
                    createdAt = o.optLong("createdAt", now), updatedAt = o.optLong("updatedAt", now),
                ))
                count++
                o.optJSONArray("attachments")?.let { atts ->
                    for (j in 0 until atts.length()) {
                        val a = atts.getJSONObject(j)
                        val data = android.util.Base64.decode(a.optString("data"), android.util.Base64.NO_WRAP)
                        val storedName = "att_" + now + "_" + i + "_" + j
                        val vk = VaultSession.currentKey()
                        try {
                            File(File(ctx.filesDir, "attachments").apply { mkdirs() }, storedName)
                                .writeBytes(VaultCrypto.encrypt(vk, data))
                        } finally {
                            VaultCrypto.wipe(vk, data)
                        }
                        attDao.upsert(Attachment(
                            ownerType = "DOC", ownerId = docId,
                            displayName = a.optString("name"), mime = a.optString("mime"),
                            storedName = storedName, size = data.size.toLong(), createdAt = now,
                        ))
                    }
                }
            }
        }

        root.optJSONArray("notes")?.let { arr ->
            val dao = VaultSession.noteDao()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                dao.upsert(NoteEntry(
                    title = o.optString("title"), body = o.optString("body"),
                    colorIdx = o.optInt("colorIdx"), pinned = o.optBoolean("pinned"),
                    createdAt = o.optLong("createdAt", now), updatedAt = o.optLong("updatedAt", now),
                ))
                count++
            }
        }

        // Remap list/task ids so an import merges instead of colliding
        val taskDao = VaultSession.taskDao()
        val listIdMap = mutableMapOf<Long, Long>()
        root.optJSONArray("taskLists")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val newId = taskDao.upsertList(TaskList(
                    name = o.optString("name"), position = o.optInt("position"),
                    createdAt = o.optLong("createdAt", now),
                ))
                listIdMap[o.optLong("id")] = newId
            }
        }
        val taskIdMap = mutableMapOf<Long, Long>()
        root.optJSONArray("tasks")?.let { arr ->
            // two passes so parentId can be remapped after all tasks exist
            val pending = mutableListOf<Pair<Long, JSONObject>>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val newId = taskDao.upsertTask(TaskItem(
                    listId = listIdMap[o.optLong("listId")] ?: 0,
                    parentId = 0,
                    title = o.optString("title"), details = o.optString("details"),
                    dueAt = o.optLong("dueAt"), starred = o.optBoolean("starred"),
                    completed = o.optBoolean("completed"), completedAt = o.optLong("completedAt"),
                    position = o.optInt("position"),
                    createdAt = o.optLong("createdAt", now), updatedAt = o.optLong("updatedAt", now),
                ))
                taskIdMap[o.optLong("id")] = newId
                if (o.optLong("parentId") != 0L) pending += newId to o
                count++
            }
            pending.forEach { (newId, o) ->
                val parent = taskIdMap[o.optLong("parentId")] ?: 0
                if (parent != 0L) {
                    taskDao.upsertTask(TaskItem(
                        id = newId,
                        listId = listIdMap[o.optLong("listId")] ?: 0,
                        parentId = parent,
                        title = o.optString("title"), details = o.optString("details"),
                        dueAt = o.optLong("dueAt"), starred = o.optBoolean("starred"),
                        completed = o.optBoolean("completed"), completedAt = o.optLong("completedAt"),
                        position = o.optInt("position"),
                        createdAt = o.optLong("createdAt", now), updatedAt = o.optLong("updatedAt", now),
                    ))
                }
            }
        }

        return count
    }
}
