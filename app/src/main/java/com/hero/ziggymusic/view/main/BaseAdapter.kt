package com.hero.ziggymusic.view.main

import androidx.recyclerview.widget.RecyclerView
import com.hero.ziggymusic.view.listener.OnRecyclerItemClickListener

abstract class BaseAdapter<T : RecyclerView.ViewHolder, D> : RecyclerView.Adapter<T>() {
    private var onRecyclerItemClickListener: OnRecyclerItemClickListener<D>? = null

    open fun getOnRecyclerItemClickListener(): OnRecyclerItemClickListener<D>? {
        return onRecyclerItemClickListener
    }

    open fun setOnRecyclerItemClickListener(onRecyclerItemClickListener: OnRecyclerItemClickListener<D>?) {
        this.onRecyclerItemClickListener = onRecyclerItemClickListener
    }
}