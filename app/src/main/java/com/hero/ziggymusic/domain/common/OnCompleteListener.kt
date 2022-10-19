package com.hero.ziggymusic.domain.common

interface OnCompleteListener<T> {
    fun onComplete(isSuccess: Boolean, data: T)
}