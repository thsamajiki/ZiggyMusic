package com.hero.ziggymusic.ext

import android.net.Uri
import android.widget.ImageView
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.MusicModel

@BindingAdapter("imageURI")
fun ImageView.setAlbumImageURI(uri: Uri?) {
    setImageURI(uri)
}