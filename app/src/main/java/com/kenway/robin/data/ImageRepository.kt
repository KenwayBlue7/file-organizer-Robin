package com.kenway.robin.data

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ImageRepository(
    private val application: Application,
    private val tagDao: TagDao
) {

    suspend fun getImagesFromFolder(folderUri: Uri): List<Uri> = withContext(Dispatchers.IO) {
        val imageUris = mutableListOf<Uri>()
        val contentResolver = application.contentResolver

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri,
            DocumentsContract.getTreeDocumentId(folderUri)
        )

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val mimeType = cursor.getString(mimeTypeColumn)
                if (mimeType != null && mimeType.startsWith("image/")) {
                    val docId = cursor.getString(idColumn)
                    val imageUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                    imageUris.add(imageUri)
                }
            }
        }
        return@withContext imageUris
    }

    fun getAllTags(): Flow<List<Tag>> {
        return tagDao.getAllTags()
    }

    suspend fun addTag(tag: Tag) {
        tagDao.insertTag(tag)
    }

    suspend fun deleteTagByUri(imageUri: String) {
        tagDao.deleteTagByUri(imageUri)
    }
}
