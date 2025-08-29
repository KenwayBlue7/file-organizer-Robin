package com.kenway.robin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: Tag)

    @Query("DELETE FROM tags WHERE imageUri = :imageUri")
    suspend fun deleteTagByUri(imageUri: String)

    @Query("SELECT * FROM tags ORDER BY id DESC")
    fun getAllTags(): Flow<List<Tag>>
}
