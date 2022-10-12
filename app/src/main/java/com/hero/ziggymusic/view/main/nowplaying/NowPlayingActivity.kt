package com.hero.ziggymusic.view.main.nowplaying

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.MusicFileData
import com.hero.ziggymusic.databinding.ActivityNowPlayingBinding

class NowPlayingActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var binding : ActivityNowPlayingBinding
    private lateinit var sbPlayer: SeekBar
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var handler: Handler


    val EXTRA_MUSIC_FILE_KEY : String = "id"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNowPlayingBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        setOnClickListener()


        sbPlayer.max = 100

        prepareMediaPlayer()

        sbPlayer.setOnTouchListener { view, motionEvent ->
            false
        }

        sbPlayer.setOnTouchListener(OnTouchListener { view: View, motionEvent: MotionEvent? ->
            val seekBar = view as SeekBar
            val playPosition = mediaPlayer.duration / 100 * seekBar.progress
            mediaPlayer.seekTo(playPosition)
            binding.tvCurrentTime.text = milliSecondsToTimer(mediaPlayer.currentPosition.toLong())
            false
        })

        mediaPlayer.setOnBufferingUpdateListener(MediaPlayer.OnBufferingUpdateListener { mediaPlayer, i ->
            sbPlayer.secondaryProgress = i
        })

        mediaPlayer.setOnCompletionListener(MediaPlayer.OnCompletionListener {
            sbPlayer.progress = 0
            binding.ibPlayPause.setImageResource(R.drawable.ic_play_button)
            binding.tvCurrentTime.text = R.string.current_playing_time.toString()
            mediaPlayer.reset()
            prepareMediaPlayer()
        })
    }

    private fun initView() {

        val musicFileKey = intent.getStringExtra(EXTRA_MUSIC_FILE_KEY)

//        initializeNowPlaying()
    }

    private fun setOnClickListener() {
        binding.ivBack.setOnClickListener(this)
        binding.ibPlayPause.setOnClickListener(View.OnClickListener {
            if (mediaPlayer.isPlaying) {
                handler.removeCallbacks(updater)
                mediaPlayer.pause()
                binding.ibPlayPause.setImageResource(R.drawable.ic_play_button)
            } else {
                mediaPlayer.start()
                binding.ibPlayPause.setImageResource(R.drawable.ic_pause_button)
                updateSeekBar()
            }
        })
        binding.ibPrevious.setOnClickListener(this)
        binding.ibNext.setOnClickListener(this)
        binding.ibRepeat.setOnClickListener(this)
    }

    private fun initializeNowPlaying(musicFileData: MusicFileData) {

    }

    private fun prepareMediaPlayer() {

    }

    private var updater : Runnable = Runnable {
        run {
            updateSeekBar()
            val currentDuration : Long = mediaPlayer.currentPosition.toLong()
            binding.tvCurrentTime.text = milliSecondsToTimer(currentDuration)
        }
    }

    private fun updateSeekBar() {
        if (mediaPlayer.isPlaying) {
            sbPlayer.progress = (((mediaPlayer.currentPosition.div(mediaPlayer.duration)).times(
                100
            )).toFloat()).toInt()
            handler.postDelayed(updater, 1000)
        }
    }

    private fun milliSecondsToTimer(milliSeconds : Long) : String {
        var timerString : String = ""
        var secondsString : String

        var minutes : Int = (milliSeconds % (1000 * 60 * 60) / (1000 * 60)).toInt()
        var seconds : Int = ((milliSeconds % (1000 * 60 * 60)) % (1000 * 60) / 1000).toInt()

        if (seconds < 10) {
            secondsString = "0$seconds"
        } else {
            secondsString = "" + seconds
        }

        timerString = "$timerString$minutes:$secondsString"

        return timerString
    }




    override fun onClick(p0: View?) {
        when(p0?.id) {
            R.id.iv_back -> finish()
        }
    }
}