package com.example.data

import kotlinx.coroutines.flow.Flow

class HomeScreenRepository(private val homeScreenDao: HomeScreenDao) {
    val allItems: Flow<List<HomeScreenItem>> = homeScreenDao.getAllItems()

    suspend fun insertItem(item: HomeScreenItem): Long {
        return homeScreenDao.insertItem(item)
    }

    suspend fun updateItem(item: HomeScreenItem) {
        homeScreenDao.updateItem(item)
    }

    suspend fun deleteItem(item: HomeScreenItem) {
        homeScreenDao.deleteItem(item)
    }

    suspend fun deleteItemById(id: Int) {
        homeScreenDao.deleteItemById(id)
    }

    suspend fun updateItemPosition(id: Int, page: Int, row: Int, column: Int) {
        homeScreenDao.updateItemPosition(id, page, row, column)
    }

    suspend fun updateItemSize(id: Int, spanX: Int, spanY: Int) {
        homeScreenDao.updateItemSize(id, spanX, spanY)
    }
}
