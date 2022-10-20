package com.hero.ziggymusic.view.main.nowplaying

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.databinding.ActivityNowPlayingBinding
import com.hero.ziggymusic.view.main.nowplaying.viewmodel.NowPlayingViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NowPlayingActivity : AppCompatActivity(), View.OnClickListener {

    private var _binding: ActivityNowPlayingBinding? = null
    private val binding get() = _binding!!

    private val nowPlayingViewModel by viewModels<NowPlayingViewModel>()

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var handler: Handler

    companion object {
        const val EXTRA_MUSIC_FILE_KEY: String = "id"

        fun getIntent(context: Context, musicKey: String): Intent =
            Intent(context, NowPlayingActivity::class.java)
                .putExtra(EXTRA_MUSIC_FILE_KEY, musicKey)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = DataBindingUtil.setContentView(this, R.layout.activity_now_playing)
        binding.lifecycleOwner = this
//        val view = binding.root
//        setContentView(view)



        initView()
        initViewModel()
        setOnClickListener()
//        prepareMediaPlayer()
        initPlayer()
    }

    private fun initPlayer() {
        mediaPlayer = MediaPlayer()
        mediaPlayer.setOnBufferingUpdateListener(MediaPlayer.OnBufferingUpdateListener { mediaPlayer, i ->
            binding.sbPlayer.secondaryProgress = i
        })

        mediaPlayer.setOnCompletionListener(MediaPlayer.OnCompletionListener {
            binding.sbPlayer.progress = 0
            binding.ibPlayPause.setImageResource(R.drawable.ic_play_button)
            binding.tvCurrentTime.text = R.string.current_playing_time.toString()
            mediaPlayer.reset()
//            prepareMediaPlayer()
        })

        lifecycleScope.launch {
            ticker(1000L, 1000L)
                .receiveAsFlow()
                .collect() {
                    binding.tvCurrentTime.text =
                        milliSecondsToTimer(mediaPlayer.currentPosition.toLong())
                }
        }
    }

    private fun initViewModel() {
        nowPlayingViewModel.nowPlayingMusic.observe(this) { musicModel ->
            initializeNowPlaying(musicModel)
            playMusic(musicModel.getMusicFileUri())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.stop()
        mediaPlayer.release()
    }

    private fun initView() {
        val musicKey = intent.getStringExtra(EXTRA_MUSIC_FILE_KEY).orEmpty()

        nowPlayingViewModel.requestMusic(musicKey)

        binding.sbPlayer.max = 100
    }

    private fun setOnClickListener() {
        handler = Handler(Looper.getMainLooper())
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

        binding.sbPlayer.setOnClickListener { view ->
            val seekBar = view as SeekBar
            val playPosition = mediaPlayer.duration / 100 * seekBar.progress
            mediaPlayer.seekTo(playPosition)
            binding.tvCurrentTime.text = milliSecondsToTimer(mediaPlayer.currentPosition.toLong())

        }
    }

    private fun initializeNowPlaying(musicModel: MusicModel) {
        binding.tvSongTitle.text = musicModel.musicTitle
        binding.tvSongArtist.text = musicModel.musicArtist
        binding.tvTotalTime.text = milliSecondsToTimer(musicModel.duration ?: 0L)
        binding.rivAlbumArt.setImageURI(musicModel.getAlbumUri())
    }

    private fun prepareMediaPlayer(musicFileUri: Uri) {

    }

    private fun playMusic(musicFileUri: Uri) {
        mediaPlayer.setDataSource(this, musicFileUri)
        mediaPlayer.prepare()
        mediaPlayer.start()
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
            binding.sbPlayer.progress = (((mediaPlayer.currentPosition.div(mediaPlayer.duration)).times(
                100
            )).toFloat()).toInt()
            handler.postDelayed(updater, 1000)
        }
    }

    private fun milliSecondsToTimer(milliSeconds : Long) : String {
        var timerString : String = ""
        val secondsString : String

        val minutes : Int = (milliSeconds % (1000 * 60 * 60) / (1000 * 60)).toInt()
        val seconds : Int = ((milliSeconds % (1000 * 60 * 60)) % (1000 * 60) / 1000).toInt()

        if (seconds < 10) {
            secondsString = "0$seconds"
        } else {
            secondsString = "" + seconds
        }

        timerString = "$timerString$minutes:$secondsString"

        return timerString
    }

    override fun onClick(view: View?) {
        when(view?.id) {
            binding.ivBack.id -> finish()
        }
    }
}