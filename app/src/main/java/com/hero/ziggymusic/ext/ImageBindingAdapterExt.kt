package com.hero.ziggymusic.ext

import android.net.Uri
import android.widget.ImageView
import androidx.databinding.BindingAdapter

@BindingAdapter("imageURI")
fun ImageView.setAlbumImageURI(uri: Uri?) {
    setImageURI(uri)
}