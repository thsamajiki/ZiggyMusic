package com.hero.ziggymusic.view.main.player

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.hero.ziggymusic.R
import com.hero.ziggymusic.ZiggyMusicApp
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.database.music.entity.PlayerModel
import com.hero.ziggymusic.databinding.FragmentPlayerBinding
import com.hero.ziggymusic.event.Event
import com.hero.ziggymusic.event.EventBus
import com.hero.ziggymusic.view.main.player.viewmodel.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.max

@AndroidEntryPoint
class PlayerFragment : Fragment(), View.OnClickListener {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private var playerModel: PlayerModel = PlayerModel.getInstance()
    private val playerViewModel by viewModels<PlayerViewModel>()

//    private var player: ExoPlayer? = null
    private val player by lazy {
        (context?.applicationContext as ZiggyMusicApp).exoPlayer
    }
    private var currentMusic: MusicModel? = null // 현재 재생 중인 음원

    private lateinit var playerMotionManager: PlayerMotionManager
    private lateinit var playerBottomSheetManager: PlayerBottomSheetManager

    private val musicKey: String
        get() = requireArguments().getString(EXTRA_MUSIC_FILE_KEY).orEmpty()

    private val updateSeekRunnable = Runnable {
        updateSeek()
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

//        val musicList = requireActivity().intent.getStringExtra("musicList") as Playlist?
        val currentPosition = requireActivity().intent.getIntExtra("currentPosition", player?.currentMediaItemIndex ?: 0)

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
//                    binding.constraintLayout.progress = slideOffset
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
                Log.d("initViewModel", "playerModel: $playerModel")
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

        Log.d("changeMusic", "findIndex: $findIndex")

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
        // 재생 or 일시정지 버튼
        binding.ivPlayPause.setOnClickListener {
            val player = this.player ?: return@setOnClickListener

            if (player.isPlaying) {
                EventBus.getInstance().post(Event("PAUSE"))
            } else {
                EventBus.getInstance().post(Event("PLAY"))
            }
        }

        binding.ivNext.setOnClickListener {
            EventBus.getInstance().post(Event("SKIP_NEXT"))
        }

        binding.ivPrevious.setOnClickListener {
            EventBus.getInstance().post(Event("SKIP_PREV"))
        }

//        binding.ivPlaylist.setOnClickListener {
//            playerViewModel.changeState(PlayerMotionManager.State.COLLAPSED)
//        }
    }

    private fun initPlayView() {
//        player = ExoPlayer.Builder(requireContext()).build()
        binding.vPlayer.player = player
        binding.animationViewVisualizer.pauseAnimation()

        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)

                // 플레이어가 재생 또는 일시정지 될 떄
                if (isPlaying) {
                    binding.ivPlayPause.setImageResource(R.drawable.ic_pause_button)
                    binding.animationViewVisualizer.playAnimation()
                } else {
                    binding.ivPlayPause.setImageResource(R.drawable.ic_play_button)
                    binding.animationViewVisualizer.pauseAnimation()
                }
            }

            // 미디어 아이템이 바뀔 때
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)

                val newMusicKey: String = mediaItem?.mediaId ?: return
                playerModel.changedMusic(newMusicKey)

                Log.d("onMediaItemTransition", "playerModel.currentMusic: ${playerModel.currentMusic}")
                updatePlayerView(playerModel.currentMusic)
            }

            // 재생, 재생 완료, 버퍼링 상태 ...
            override fun onPlaybackStateChanged(state: Int) {
                super.onPlaybackStateChanged(state)

                updateSeek()
            }
        })
    }

    private fun updateSeek() {
        val player = this.player ?: return
        val duration = if (player.duration >= 0) player.duration else 0 // 전체 음악 길이
        val position = player.currentPosition

        updateSeekUi(duration, position)

        val state = player.playbackState

        val view = binding.root
        view.removeCallbacks(updateSeekRunnable)
        // 재생 중일때 (대 중이 아니거나, 재생이 끝나지 않은 경우)
        if (state != Player.STATE_IDLE && state != Player.STATE_ENDED) {
            view.postDelayed(updateSeekRunnable, 1000) // 1초에 한번씩 실행
        }
    }

    private fun updateSeekUi(duration: Long, position: Long) {
        binding.sbPlayList.max = (duration / 1000).toInt() // 총 길이를 설정. 1000으로 나눠 작게
        binding.sbPlayList.progress = (position / 1000).toInt() // 동일하게 1000으로 나눠 작게

        binding.sbPlayer.max = (duration / 1000).toInt()
        binding.sbPlayer.progress = (position / 1000).toInt()

        binding.tvCurrentPlayTime.text = String.format(
            "%02d:%02d",
            TimeUnit.MINUTES.convert(position, TimeUnit.MILLISECONDS), // 현재 분
            (position / 1000) % 60 // 분 단위를 제외한 현재 초
        )

        binding.tvTotalTime.text = String.format(
            "%02d:%02d",
            TimeUnit.MINUTES.convert(duration, TimeUnit.MILLISECONDS), // 전체 분
            (duration / 1000) % 60 // 분 단위를 제외한 초
        )
    }

    private fun updatePlayerView(musicModel: MusicModel?) {
        musicModel ?: return

        binding.tvSongTitle.text = musicModel.title
        binding.tvSongArtist.text = musicModel.artist

        Glide.with(binding.ivAlbumArt.context)
            .load(musicModel.getAlbumUri())
            .override(binding.ivAlbumArt.layoutParams.width, binding.ivAlbumArt.layoutParams.height)
            .error(R.drawable.ic_no_album_image)
            .fallback(R.drawable.ic_no_album_image)
            .transform(RoundedCorners(12))
            .into(binding.ivAlbumArt)

        binding.tvSongAlbum.text = musicModel.album
    }


    @OptIn(UnstableApi::class)
    private fun playMusic(musicList: List<MusicModel>, nowPlayMusic: MusicModel?) {
        if (nowPlayMusic != null) {
            currentMusic = nowPlayMusic
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

            val mediaSource = ProgressiveMediaSource.Factory(defaultDataSourceFactory) // 미디어 정보를 가져오는 클래스
                .createMediaSource(mediaItem)
            mediaSource
        }

        val playIndex = musicList.indexOf(nowPlayMusic)

        player?.run {
            setMediaSources(musicMediaItems)
            prepare()
            seekTo(max(playIndex, 0), 0) // positionsMs=0 초 부터 시작
//            play()
        }
    }

    override fun onStop() {
        super.onStop()

        player?.pause()
        EventBus.getInstance().post(Event("STOP"))
        binding.root.removeCallbacks(updateSeekRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()

        _binding = null
        player?.release()
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

    override fun onClick(view: View?) {
    }
}