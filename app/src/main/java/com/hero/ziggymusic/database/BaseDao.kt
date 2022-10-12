package com.hero.ziggymusic.database

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update

interface BaseDao<T> {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertData(data: T)

    @Insert
    fun insertAll(dataList: List<T>?)

    @Update
    fun updateData(data: T)

    @Delete
    fun deleteData(data: T)
}