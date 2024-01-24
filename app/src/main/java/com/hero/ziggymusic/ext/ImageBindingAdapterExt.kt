package com.hero.ziggymusic.ext

import android.net.Uri
import android.util.Log
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide
import com.hero.ziggymusic.R

@BindingAdapter("imageURI")
fun ImageView.setAlbumImageURI(uri: Uri?) {
    Log.d("setAlbumImageURI", "uri: $uri")
    Glide.with(context)
        .load(uri)
        .error(R.drawable.ic_no_album_image)
        .fallback(R.drawable.ic_no_album_image)
        .into(this)
}