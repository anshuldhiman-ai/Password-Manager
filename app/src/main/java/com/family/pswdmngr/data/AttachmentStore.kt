package com.family.pswdmngr.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.family.pswdmngr.crypto.VaultCrypto
import java.io.File

/**
 * Attachments (Aadhaar/PAN scans, PDFs) are never stored as plain files.
 * Each is AES-256-GCM encrypted under the vault key into filesDir/attachments/,
 * decrypted only into memory while being viewed.
 */
object AttachmentStore {

    private fun dir(ctx: Context): File =
        File(ctx.filesDir, "attachments").apply { mkdirs() }

    /** Copy the picked URI into the encrypted store and register it on the owner. */
    suspend fun add(ctx: Context, ownerType: String, ownerId: Long, src: Uri): Attachment? {
        val resolver = ctx.contentResolver
        val bytes = resolver.openInputStream(src)?.use { it.readBytes() } ?: return null
        val mime = resolver.getType(src) ?: "application/octet-stream"

        var displayName = "attachment"
        resolver.query(src, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) displayName = c.getString(idx) ?: displayName
        }

        val storedName = "att_" + System.currentTimeMillis() + "_" +
            android.util.Base64.encodeToString(VaultCrypto.randomBytes(6),
                android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)

        val key = VaultSession.currentKey()
        try {
            val blob = VaultCrypto.encrypt(key, bytes)
            File(dir(ctx), storedName).writeBytes(blob)
        } finally {
            VaultCrypto.wipe(key, bytes)
        }

        val att = Attachment(
            ownerType = ownerType, ownerId = ownerId,
            displayName = displayName, mime = mime,
            storedName = storedName, size = bytes.size.toLong(),
            createdAt = System.currentTimeMillis(),
        )
        val id = VaultSession.attachmentDao().upsert(att)
        return att.copy(id = id)
    }

    /** Decrypt into memory. Caller must wipe the result when done. */
    fun readBytes(ctx: Context, att: Attachment): ByteArray? {
        val f = File(dir(ctx), att.storedName)
        if (!f.exists()) return null
        val key = VaultSession.currentKey()
        return try {
            VaultCrypto.decrypt(key, f.readBytes())
        } catch (e: Exception) {
            null
        } finally {
            VaultCrypto.wipe(key)
        }
    }

    fun decodeBitmap(ctx: Context, att: Attachment): Bitmap? {
        val bytes = readBytes(ctx, att) ?: return null
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } finally {
            VaultCrypto.wipe(bytes)
        }
    }

    /**
     * Render PDF pages to bitmaps. PdfRenderer only reads from a file descriptor,
     * so the plaintext briefly lands in cacheDir and is deleted in `finally`.
     */
    fun renderPdfPages(ctx: Context, att: Attachment, maxPages: Int = 8): List<Bitmap> {
        val bytes = readBytes(ctx, att) ?: return emptyList()
        val tmp = File(ctx.cacheDir, "pdfview.tmp")
        return try {
            tmp.writeBytes(bytes)
            VaultCrypto.wipe(bytes)
            val pages = mutableListOf<Bitmap>()
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until minOf(renderer.pageCount, maxPages)) {
                        renderer.openPage(i).use { page ->
                            val scale = 1500f / page.width
                            val bmp = Bitmap.createBitmap(
                                (page.width * scale).toInt(),
                                (page.height * scale).toInt(),
                                Bitmap.Config.ARGB_8888,
                            )
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            pages += bmp
                        }
                    }
                }
            }
            pages
        } catch (e: Exception) {
            emptyList()
        } finally {
            tmp.delete()
        }
    }

    fun delete(ctx: Context, att: Attachment) {
        File(dir(ctx), att.storedName).delete()
    }

    suspend fun deleteForOwner(ctx: Context, ownerType: String, ownerId: Long) {
        val dao = VaultSession.attachmentDao()
        dao.forOwnerOnce(ownerType, ownerId).forEach {
            delete(ctx, it)
            dao.delete(it)
        }
    }
}
