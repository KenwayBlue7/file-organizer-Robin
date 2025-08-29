package com.kenway.robin.data

import android.app.Application
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val TAG = "ImageRepository"

class ImageRepository(
    private val application: Application,
    private val tagDao: TagDao
) {

    suspend fun getImagesFromFolder(folderUri: Uri): List<Uri> = withContext(Dispatchers.IO) {
        val imageUris = mutableListOf<Uri>()
        val contentResolver = application.contentResolver
        Log.d("ImageRepository", "Starting to get images from folder: $folderUri")

        try {
            val documentId = DocumentsContract.getTreeDocumentId(folderUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, documentId)
            Log.d("ImageRepository", "Querying children URI: $childrenUri")

            contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null,
                null,
                null
            )?.use { cursor ->
                Log.d("ImageRepository", "Cursor has ${cursor.count} items.")
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val childDocumentId = cursor.getString(idColumn)
                    val mimeType = cursor.getString(mimeTypeColumn)
                    
                    Log.d("ImageRepository", "Found item with MIME type: $mimeType")

                    if (mimeType != null && mimeType.startsWith("image/")) {
                        val childUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, childDocumentId)
                        imageUris.add(childUri)
                        Log.d("ImageRepository", "Added image URI: $childUri")
                    }
                }
            } ?: Log.w("ImageRepository", "ContentResolver query returned a null cursor.")
        } catch (e: Exception) {
            Log.e("ImageRepository", "Error getting images from folder.", e)
        }

        Log.d("ImageRepository", "Finished. Found ${imageUris.size} images.")
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

    fun getUniqueTagNames(): Flow<List<String>> {
        Log.d(TAG, "getUniqueTagNames called")
        return tagDao.getAllTags()
            .map { tags ->
                tags.map { it.tagName }
                    .distinct()
                    .sorted()
            }
    }

    suspend fun finalizeTag(uri: Uri, tagName: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "finalizeTag: Starting for uri: $uri with tag: $tagName")
        val contentResolver = application.contentResolver

        // 1. Create destination directory
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val organizerDir = File(picturesDir, "Organizer")
        val destinationDir = File(organizerDir, tagName)
        if (!destinationDir.exists()) {
            val created = destinationDir.mkdirs()
            Log.d(TAG, "finalizeTag: Destination directory created at ${destinationDir.absolutePath}: $created")
        } else {
            Log.d(TAG, "finalizeTag: Destination directory already exists at ${destinationDir.absolutePath}")
        }

        // Get original file name
        var fileName: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        Log.d(TAG, "finalizeTag: Original file name: $fileName")

        if (fileName == null) {
            // Fallback file name if original can't be determined
            fileName = "image_${System.currentTimeMillis()}.jpg"
            Log.w(TAG, "finalizeTag: Could not determine original file name. Using fallback: $fileName")
        }

        val destinationFile = File(destinationDir, fileName!!)

        try {
            // 2. Copy file to new directory
            Log.d(TAG, "finalizeTag: Copying file to ${destinationFile.absolutePath}")
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw Exception("Failed to open input stream for $uri")
            Log.d(TAG, "finalizeTag: File copy successful.")

            // 3. Save tag to database
            val newTag = Tag(
                imageUri = Uri.fromFile(destinationFile).toString(),
                tagName = tagName,
                originalPath = uri.toString()
            )
            addTag(newTag)
            Log.d(TAG, "finalizeTag: Saved new tag to database: $newTag")

        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing tag for $uri", e)
            // Optionally re-throw or handle the error
        }
    }
}
