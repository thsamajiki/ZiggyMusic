package com.hero.ziggymusic.database

import com.hero.ziggymusic.domain.common.OnCompleteListener

interface DataStore<T> {
    fun getData(
        onCompleteListener: OnCompleteListener<T>,
        vararg params: Any?
    ) // 인자를 0개 이상 넣어도 됨(Null이어도 됨)

    fun getDataList(onCompleteListener: OnCompleteListener<List<T>>, vararg params: Any?)
    fun add(onCompleteListener: OnCompleteListener<T>, data: T?)
}