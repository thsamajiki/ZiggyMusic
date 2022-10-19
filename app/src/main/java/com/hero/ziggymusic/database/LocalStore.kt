package com.hero.ziggymusic.database

import android.content.Context

abstract class LocalStore<T> : DataStore<T> {
    private var dataList: MutableList<T> = ArrayList()
    private var context: Context? = null

    open fun LocalStore(context: Context?) {
        this.context = context
    }

    open fun LocalStore() {}

    open fun getDataList(): List<T>? {
        return dataList
    }

    open fun setDataList(dataList: MutableList<T>) {
        this.dataList = dataList
    }

    open fun getContext(): Context? {
        return context
    }

    open fun addAll(dataList: List<T>?) {
        if (dataList == null) {
            return
        }
        for (element in dataList) {
            val index = this.dataList.indexOf(element)
            if (index == -1) {
                // create
                this.dataList.add(element)
            } else {
                // update
                this.dataList[index] = element
            }
        }
    }
}