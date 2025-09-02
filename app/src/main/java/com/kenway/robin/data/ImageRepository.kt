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

data class TaggedFolder(
    val folderFile: File,
    val thumbnailUri: Uri?
)

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

    suspend fun getTaggedFolders(): List<TaggedFolder> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getTaggedFolders: Scanning for tagged folders.")
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val robinDir = File(picturesDir, "Robin")

        if (!robinDir.exists() || !robinDir.isDirectory) {
            Log.w(TAG, "getTaggedFolders: Robin directory does not exist at ${robinDir.absolutePath}")
            return@withContext emptyList()
        }

        val taggedFolders = mutableListOf<TaggedFolder>()
        // Get all subdirectories, but exclude "Trash"
        val subDirs = robinDir.listFiles { file -> file.isDirectory && file.name != "Trash" } ?: return@withContext emptyList()

        for (folder in subDirs) {
            val firstImageFile = folder.listFiles { file ->
                file.isFile && (
                        file.extension.equals("jpg", ignoreCase = true) ||
                        file.extension.equals("jpeg", ignoreCase = true) ||
                        file.extension.equals("png", ignoreCase = true) ||
                        file.extension.equals("webp", ignoreCase = true) ||
                        file.extension.equals("gif", ignoreCase = true)
                        )
            }?.firstOrNull()

            val thumbnailUri = firstImageFile?.let { Uri.fromFile(it) }
            taggedFolders.add(TaggedFolder(folderFile = folder, thumbnailUri = thumbnailUri))
            Log.d(TAG, "getTaggedFolders: Found folder '${folder.name}' with thumbnail: $thumbnailUri")
        }

        Log.d(TAG, "getTaggedFolders: Found ${taggedFolders.size} tagged folders.")
        return@withContext taggedFolders
    }

    suspend fun finalizeTag(uri: Uri, tagName: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "finalizeTag: Starting for uri: $uri with tag: $tagName")
        val contentResolver = application.contentResolver

        // 1. Create destination directory
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val robinDir = File(picturesDir, "Robin") // Create a "Robin" base folder
        val destinationDir = File(robinDir, tagName) // Create the tag-specific folder inside "Robin"
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

            // 4. Delete the original file
            try {
                if (DocumentsContract.deleteDocument(contentResolver, uri)) {
                    Log.d(TAG, "finalizeTag: Successfully deleted original file: $uri")
                } else {
                    Log.w(TAG, "finalizeTag: Failed to delete original file: $uri")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "finalizeTag: Lacked permission to delete original file: $uri", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing tag for $uri", e)
            // Optionally re-throw or handle the error
        }
    }

    suspend fun moveFileToTrash(uri: Uri) = withContext(Dispatchers.IO) {
        Log.d(TAG, "moveFileToTrash: Starting for uri: $uri")
        val contentResolver = application.contentResolver

        // 1. Create destination directory
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val robinDir = File(picturesDir, "Robin")
        val trashDir = File(robinDir, "Trash")
        if (!trashDir.exists()) {
            val created = trashDir.mkdirs()
            Log.d(TAG, "moveFileToTrash: Trash directory created at ${trashDir.absolutePath}: $created")
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
        Log.d(TAG, "moveFileToTrash: Original file name: $fileName")

        if (fileName == null) {
            fileName = "trashed_${System.currentTimeMillis()}"
            Log.w(TAG, "moveFileToTrash: Could not determine original file name. Using fallback: $fileName")
        }

        val destinationFile = File(trashDir, fileName!!)

        try {
            // 2. Copy file to trash directory
            Log.d(TAG, "moveFileToTrash: Copying file to ${destinationFile.absolutePath}")
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw Exception("Failed to open input stream for $uri")
            Log.d(TAG, "moveFileToTrash: File copy successful.")

            // 3. Delete the original file
            try {
                if (DocumentsContract.deleteDocument(contentResolver, uri)) {
                    Log.d(TAG, "moveFileToTrash: Successfully deleted original file: $uri")
                } else {
                    Log.w(TAG, "moveFileToTrash: Failed to delete original file: $uri")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "moveFileToTrash: Lacked permission to delete original file: $uri", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error moving file to trash for $uri", e)
        }
    }

    suspend fun deleteFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "deleteFile: Attempting to delete file: $uri")
        val contentResolver = application.contentResolver
        try {
            if (DocumentsContract.deleteDocument(contentResolver, uri)) {
                Log.d(TAG, "deleteFile: Successfully deleted file: $uri")
                return@withContext true
            } else {
                Log.w(TAG, "deleteFile: Failed to delete file: $uri")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteFile: Error deleting file: $uri", e)
            return@withContext false
        }
    }

    suspend fun getTrashedFiles(): List<File> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getTrashedFiles: Scanning for trashed files.")
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val robinDir = File(picturesDir, "Robin")
        val trashDir = File(robinDir, "Trash")

        if (!trashDir.exists() || !trashDir.isDirectory) {
            Log.w(TAG, "getTrashedFiles: Trash directory does not exist at ${trashDir.absolutePath}")
            return@withContext emptyList()
        }

        val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")
        val trashedFiles = trashDir.listFiles { file ->
            file.isFile && file.extension.lowercase() in imageExtensions
        }?.toList() ?: emptyList()

        Log.d(TAG, "getTrashedFiles: Found ${trashedFiles.size} files in trash.")
        return@withContext trashedFiles
    }

    suspend fun restoreFile(file: File) = withContext(Dispatchers.IO) {
        Log.d(TAG, "restoreFile: Attempting to restore file: ${file.absolutePath}")
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val robinDir = File(picturesDir, "Robin")
        val restoredDir = File(robinDir, "Restored")

        if (!restoredDir.exists()) {
            val created = restoredDir.mkdirs()
            Log.d(TAG, "restoreFile: Restored directory created at ${restoredDir.absolutePath}: $created")
        }

        val destinationFile = File(restoredDir, file.name)

        try {
            if (!file.renameTo(destinationFile)) {
                Log.w(TAG, "restoreFile: Failed to move file, attempting copy-delete: ${file.absolutePath}")
                file.copyTo(destinationFile, overwrite = true)
                file.delete()
            }
            Log.d(TAG, "restoreFile: Successfully moved file to ${destinationFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "restoreFile: Error restoring file ${file.absolutePath}", e)
        }
    }

    suspend fun deleteFile(file: File): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "deleteFile: Attempting to permanently delete file: ${file.absolutePath}")
        try {
            if (file.delete()) {
                Log.d(TAG, "deleteFile: Successfully deleted file: ${file.absolutePath}")
                return@withContext true
            } else {
                Log.w(TAG, "deleteFile: Failed to delete file: ${file.absolutePath}")
                return@withContext false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "deleteFile: Lacked permission to delete file: ${file.absolutePath}", e)
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "deleteFile: Error deleting file: ${file.absolutePath}", e)
            return@withContext false
        }
    }
}
