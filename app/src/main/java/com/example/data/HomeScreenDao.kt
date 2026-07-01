package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeScreenDao {
    @Query("SELECT * FROM home_screen_items")
    fun getAllItems(): Flow<List<HomeScreenItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: HomeScreenItem): Long

    @Update
    suspend fun updateItem(item: HomeScreenItem)

    @Delete
    suspend fun deleteItem(item: HomeScreenItem)

    @Query("DELETE FROM home_screen_items WHERE id = :id")
    suspend fun deleteItemById(id: Int)

    @Query("UPDATE home_screen_items SET page = :page, row = :row, `column` = :column WHERE id = :id")
    suspend fun updateItemPosition(id: Int, page: Int, row: Int, column: Int)

    @Query("UPDATE home_screen_items SET spanX = :spanX, spanY = :spanY WHERE id = :id")
    suspend fun updateItemSize(id: Int, spanX: Int, spanY: Int)
}
