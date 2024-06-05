package com.hero.ziggymusic.view.main.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.ShuffleOrder
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
import kotlin.random.Random

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

    private lateinit var audioManager: AudioManager
    private var currentVolume: Int = 0  // 현재 볼륨
    private var previousVolume: Int = 0 // 이전 볼륨 저장

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

        audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        previousVolume = currentVolume

        initPlayView()
        initViewModel()
        initPlayControlButtons()
        initSeekBar()

//        val musicList = requireActivity().intent.getStringExtra("musicList") as Playlist?
        val currentPosition = requireActivity().intent.getIntExtra("currentPosition", player?.currentMediaItemIndex ?: 0)

        initPlayerBottomSheetManager()

        playerMotionManager = PlayerMotionManager(
            binding.constraintLayout,
            playerBottomSheetManager
        )

        viewLifecycleOwner.lifecycleScope.launch {
            playerViewModel.state
                .collect { state ->
                    playerMotionManager.changeState(state)
                }
        }

        initListeners()
    }

    private fun initListeners() {
        binding.root.setOnClickListener {
            val toggleState = when (playerViewModel.state.value) {
                PlayerMotionManager.State.COLLAPSED -> PlayerMotionManager.State.EXPANDED
                PlayerMotionManager.State.EXPANDED -> PlayerMotionManager.State.COLLAPSED
            }

            playerViewModel.changeState(toggleState)
        }

        toggleVolumeIcon()
        toggleRepeatModeIcon()
        toggleShuffleModeIcon()
    }

    private fun initPlayerBottomSheetManager() {
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
    }

    @OptIn(UnstableApi::class)
    private fun toggleShuffleModeIcon() {
        binding.ivShuffleMode.setOnClickListener {
            if (player.shuffleModeEnabled) { // 셔플 모드가 On일 때
                player.shuffleModeEnabled = false
                binding.ivShuffleMode.setImageResource(R.drawable.ic_shuffle_off)
            } else { // 셔플 모드가 Off일 때
                player.shuffleModeEnabled = true
                player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(playerViewModel.musicList.value.orEmpty().size, Random.nextLong()))
                binding.ivShuffleMode.setImageResource(R.drawable.ic_shuffle_on)
            }
        }
    }

    private fun toggleRepeatModeIcon() {
        binding.ivRepeatMode.setOnClickListener {
            if (player.repeatMode == Player.REPEAT_MODE_OFF) { // 반복 재생 모드 해제 상태일 때
                player.repeatMode = Player.REPEAT_MODE_ALL
                binding.ivRepeatMode.setImageResource(R.drawable.ic_repeat_all_on)
            } else if (player.repeatMode == Player.REPEAT_MODE_ALL) { // 전 곡 반복 재생 모드일 때
                player.repeatMode = Player.REPEAT_MODE_ONE
                binding.ivRepeatMode.setImageResource(R.drawable.ic_repeat_one_on)
            } else { // 한 곡 반복 재생 모드일 때
                player.repeatMode = Player.REPEAT_MODE_OFF
                binding.ivRepeatMode.setImageResource(R.drawable.ic_repeat_all)
            }
        }
    }

    private fun toggleVolumeIcon() {
        binding.ivVolume.setOnClickListener {
            val volumeDrawable = binding.ivVolume.drawable
            val volumeBitmap = drawableToBitmap(volumeDrawable)

            val muteDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_mute)

            if (muteDrawable != null) {
                val muteBitmap = drawableToBitmap(muteDrawable)

                if (areBitmapsEqual(volumeBitmap, muteBitmap)) {
                    binding.ivVolume.setImageResource(R.drawable.ic_volume)
                    currentVolume = previousVolume
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)
                    binding.sbVolume.progress = currentVolume

                } else {
                    binding.ivVolume.setImageResource(R.drawable.ic_mute)
                    val muteValue = AudioManager.ADJUST_MUTE
                    previousVolume = currentVolume
                    currentVolume = 0
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, muteValue, 0)
                    binding.sbVolume.progress = 0
                }
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
        changeVolume()

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

    private fun changeVolume() {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        previousVolume = currentVolume
        binding.sbVolume.progress = currentVolume

        binding.sbVolume.max = maxVolume

        binding.sbVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean,
            ) {
                if (fromUser) {
                    currentVolume = progress
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (seekBar.progress == 0) {
                    binding.ivVolume.setImageResource(R.drawable.ic_mute)
                } else {
                    binding.ivVolume.setImageResource(R.drawable.ic_volume)
                }

                binding.sbVolume.progress = seekBar.progress
                currentVolume = seekBar.progress
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)
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

                // 재생 반복 해제 모드 & 마지막 트랙 재생이 끝났을 때 -> 첫번째 트랙으로 이동 & 일시정지 상태
                if (player.repeatMode == Player.REPEAT_MODE_OFF &&
                    player.currentMediaItemIndex == player.mediaItemCount - 1 &&
                    state == Player.STATE_ENDED) {

                    player.seekTo(0, 0)
                    updatePlayerView(playerModel.currentMusic)
                    player.pause()
                }
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
        binding.sbPlayer.max = (duration / 1000).toInt() // 총 길이를 설정. 1000으로 나눠 작게
        binding.sbPlayer.progress = (position / 1000).toInt() // 동일하게 1000으로 나눠 작게

//        binding.sbPlayer.max = (duration / 1000).toInt()
//        binding.sbPlayer.progress = (position / 1000).toInt()

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

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }

        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }

    private fun areBitmapsEqual(bitmap1: Bitmap, bitmap2: Bitmap): Boolean {
        return bitmap1.sameAs(bitmap2)
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
