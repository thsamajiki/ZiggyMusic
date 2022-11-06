package com.hero.ziggymusic.view.listener

import android.view.View

interface OnRecyclerItemClickListener<T> {
    fun onItemClick(position: Int, view: View, data: T)
}