package com.hero.ziggymusic.view.main.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.database.music.entity.PlayerModel
import com.hero.ziggymusic.databinding.FragmentPlayerBinding

import com.hero.ziggymusic.view.main.player.viewmodel.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.max

@AndroidEntryPoint
class PlayerFragment : Fragment(), View.OnClickListener {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private var playerModel: PlayerModel = PlayerModel()
    private val playerViewModel by viewModels<PlayerViewModel>()

    private var player: ExoPlayer? = null

    private lateinit var playerMotionManager: PlayerMotionManager
    private lateinit var playerBottomSheetManager: PlayerBottomSheetManager

    private val musicKey: String
        get() = requireArguments().getString(EXTRA_MUSIC_FILE_KEY).orEmpty()

    private val updateSeekRunnable = Runnable {
        updateSeek()
    }

    companion object {
        const val TAG = "PlayerFragment"
        const val EXTRA_MUSIC_FILE_KEY: String = "id"
        fun newInstance(musicKey: String): PlayerFragment =
            PlayerFragment().apply {
                arguments = Bundle().apply {
                    putString(EXTRA_MUSIC_FILE_KEY, musicKey)
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initPlayView()
        initViewModel()
        initPlayControlButtons()
        initSeekBar()

        playerBottomSheetManager = PlayerBottomSheetManager(
            viewLifecycleOwner.lifecycle,
            binding.constraintLayout,
            object : BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            playerViewModel.changeState(PlayerMotionManager.State.EXPANDED)
                        }
                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            playerViewModel.changeState(PlayerMotionManager.State.COLLAPSED)
                        }
                        BottomSheetBehavior.STATE_HIDDEN -> {
                            playerBottomSheetManager.collapse()
                        }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                }
            }
        )

        playerMotionManager = PlayerMotionManager(
            binding.constraintLayout,
            playerBottomSheetManager
        )

        binding.root.setOnClickListener {
            val toggleState = when (playerViewModel.state.value) {
                PlayerMotionManager.State.COLLAPSED -> PlayerMotionManager.State.EXPANDED
                PlayerMotionManager.State.EXPANDED -> PlayerMotionManager.State.COLLAPSED
            }

            playerViewModel.changeState(toggleState)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            playerViewModel.state
                .collect { state ->
                    playerMotionManager.changeState(state)
                }
        }
    }

    private fun initViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            playerViewModel.musicList.observe(viewLifecycleOwner) { musicList ->
                playerModel.replaceMusicList(musicList)
                val nowMusic = musicList.find {
                    it.id == musicKey
                } ?: musicList.getOrNull(0)

                if (musicList.isNotEmpty()) {
                    playMusic(musicList, nowMusic)
                }
            }
        }
    }

    fun changeMusic(musicId: String) {
        val findIndex = playerViewModel.musicList.value.orEmpty()
            .indexOfFirst {
                it.id == musicId
            }

        if (findIndex != -1) {
            player?.seekTo(findIndex, 0)
            player?.play()
        }
    }

    private fun initSeekBar() {
        binding.sbPlayer.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean,
            ) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                player?.seekTo(seekBar.progress * 1000L)
            }
        })
    }

    private fun initPlayControlButtons() {
        binding.ivPlayPause.setOnClickListener {
            val player = this.player ?: return@setOnClickListener

            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }

        binding.ivNext.setOnClickListener {
            player?.run {
                val nextIndex = if (currentMediaItemIndex + 1 in 0 until mediaItemCount) {
                    currentMediaItemIndex + 1
                } else {
                    0
                }

                seekTo(nextIndex, 0)
            }
        }

        binding.ivPrevious.setOnClickListener {
            player?.run {
                val prevIndex = if (currentMediaItemIndex -1 in 0 until mediaItemCount) {
                    currentMediaItemIndex - 1
                } else {
                    0
                }

                seekTo(prevIndex, 0)
            }
        }

        binding.ivPlaylist.setOnClickListener {
            playerViewModel.changeState(PlayerMotionManager.State.COLLAPSED)
        }
    }

    private fun initPlayView() {
        player = ExoPlayer.Builder(requireContext()).build()
        binding.vPlayer.player = player

        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)

                if (isPlaying) {
                    binding.ivPlayPause.setImageResource(R.drawable.ic_pause_button)
                } else {
                    binding.ivPlayPause.setImageResource(R.drawable.ic_play_button)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)

                val newMusicKey: String = mediaItem?.mediaId ?: return
                playerModel.changedMusic(newMusicKey)

                updatePlayerView(playerModel.currentMusic)
            }

            override fun onPlaybackStateChanged(state: Int) {
                super.onPlaybackStateChanged(state)

                updateSeek()
            }
        })

    }

    private fun updateSeek() {
        val player = this.player ?: return
        val duration = if (player.duration >= 0) player.duration else 0
        val position = player.currentPosition

        updateSeekUi(duration, position)

        val state = player.playbackState

        val view = binding.root
        view.removeCallbacks(updateSeekRunnable)

        if (state != Player.STATE_IDLE && state != Player.STATE_ENDED) {
            view.postDelayed(updateSeekRunnable, 1000)
        }

    }

    private fun updateSeekUi(duration: Long, position: Long) {
        binding.sbPlayList.max = (duration / 1000).toInt()
        binding.sbPlayList.progress = (position / 1000).toInt()

        binding.sbPlayer.max = (duration / 1000).toInt()
        binding.sbPlayer.progress = (position / 1000).toInt()

        binding.tvCurrentPlayTime.text = String.format(
            "%02d:%02d",
            TimeUnit.MINUTES.convert(position, TimeUnit.MILLISECONDS),
            (position / 1000) % 60
        )

        binding.tvTotalTime.text = String.format(
            "%02d:%02d",
            TimeUnit.MINUTES.convert(duration, TimeUnit.MILLISECONDS),
            (duration / 1000) % 60
        )
    }

    private fun updatePlayerView(musicModel: MusicModel?) {
        musicModel ?: return

        binding.tvSongTitle.text = musicModel.musicTitle
        binding.tvSongArtist.text = musicModel.musicArtist

        Glide.with(binding.ivAlbumArt.context)
            .load(musicModel.getAlbumUri())
            .transform(RoundedCorners(12))
            .into(binding.ivAlbumArt)
    }


    private fun playMusic(musicList: List<MusicModel>, nowPlayMusic: MusicModel?) {
        if (nowPlayMusic != null) {
            playerModel.updateCurrentMusic(nowPlayMusic)
        }

        val musicMediaItems = musicList.map { music ->
            val defaultDataSourceFactory =
                DefaultDataSource.Factory(requireContext())
            val musicFileUri = music.getMusicFileUri()
            val mediaItem = MediaItem.Builder()
                .setMediaId(music.id)
                .setUri(musicFileUri)
                .build()

            val mediaSource = ProgressiveMediaSource.Factory(defaultDataSourceFactory)
                .createMediaSource(mediaItem)
            mediaSource
        }

        val playIndex = musicList.indexOf(nowPlayMusic)

        player?.run {
            setMediaSources(musicMediaItems)
            prepare()
            seekTo(max(playIndex, 0), 0)
        }
    }

    override fun onStop() {
        super.onStop()

        player?.pause()
        binding.root.removeCallbacks(updateSeekRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()

        _binding = null
        player?.release()
    }

    override fun onClick(view: View?) {
    }
}