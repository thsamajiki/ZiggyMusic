package com.hero.ziggymusic.view.main.player

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.ShuffleOrder
import com.bumptech.glide.Glide
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
import javax.inject.Inject
import kotlin.math.max
import kotlin.random.Random
import androidx.core.graphics.createBitmap
import androidx.fragment.app.activityViewModels
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.hero.ziggymusic.service.MusicMediaControllerConnector
import com.hero.ziggymusic.service.MusicServiceController
import com.hero.ziggymusic.view.main.player.model.LastPlayedMedia
import java.util.Locale

@AndroidEntryPoint
class PlayerFragment : Fragment() {
    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private var playerModel: PlayerModel = PlayerModel.getInstance()
    private val vm by activityViewModels<PlayerViewModel>()
    private var playerListener: Player.Listener? = null

    @Inject
    lateinit var player: ExoPlayer

    private var currentMusic: MusicModel? = null // 현재 재생 중인 음원
    private val playbackStateStore by lazy { PlaybackStateStore(requireContext()) }
    private var visualizerBarColor: Int? = null

    // position 저장 쓰로틀링으로 불필요한 prefs write를 줄인다.
    private var lastSavedAtMs: Long = 0L
    private var lastSavedPositionMs: Long = -1L

    private val saveIntervalMs = 3_000L   // 최소 3초 간격으로 저장
    private val saveMinDeltaMs = 1_000L   // 위치가 1초 이상 변했을 때만 저장

    private lateinit var playerMotionManager: PlayerMotionManager
    private lateinit var playerBottomSheetManager: PlayerBottomSheetManager
    private lateinit var mediaControllerConnector: MusicMediaControllerConnector
    private lateinit var playerBluetoothManager: PlayerBluetoothManager
    private var albumGradientManager: MusicAlbumArtGradientManager? = null
    private var latestAlbumBitmap: Bitmap? = null
    private var lastRenderedMusicId: String? = null

    private lateinit var audioManager: AudioManager
    private var currentVolume: Int = 0  // 현재 볼륨
    private var previousVolume: Int = 0 // 이전 볼륨 저장

    private var volumeObserver: ContentObserver? = null // 시스템 볼륨 변경 감지 observer

    private val musicKey: String
        get() = requireArguments().getString(EXTRA_MUSIC_FILE_ID).orEmpty()

    private val updateSeekRunnable = Runnable {
        updatePlaybackProgress()
    }

    private val startPlayerTextMarqueeRunnable = Runnable {
        if (_binding == null || vm.motionState.value != PlayerMotionManager.State.EXPANDED) return@Runnable
        binding.tvSongTitle.isSelected = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initMediaController()
        initAudioManager()
        initBluetoothManager()
        initPlayView()
        initPlayControlButtons()
        initSeekBar()
        initPlayerManager()
        initViewModel()
        initListeners()
    }

    private fun initAudioManager() {
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        previousVolume = currentVolume
    }

    private fun initBluetoothManager() {
        playerBluetoothManager = PlayerBluetoothManager(
            fragment = this,
            audioManager = audioManager,
            rootViewProvider = { _binding?.root },
            setBluetoothIcon = { resId -> _binding?.bluetooth?.setImageResource(resId) },
            onMessage = { message -> context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() } }
        )
    }

    private fun initMediaController() {
        mediaControllerConnector = MusicMediaControllerConnector(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            mediaControllerConnector.connect()
        }
    }

    private fun initListeners() {
        binding.root.setOnClickListener {
            if (vm.motionState.value == PlayerMotionManager.State.COLLAPSED) {
                vm.changeState(PlayerMotionManager.State.EXPANDED)
            }
        }

        binding.bluetooth.setOnClickListener {
            playerBluetoothManager.handleBluetoothClick()
        }

        toggleVolumeIcon()
        toggleRepeatModeIcon()
        toggleShuffleModeIcon()
    }

    private fun initPlayerManager() {
        playerBottomSheetManager = PlayerBottomSheetManager(
            viewLifecycleOwner.lifecycle,
            binding.motionLayout,
            object : BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            vm.changeState(PlayerMotionManager.State.EXPANDED)
                        }

                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            vm.changeState(PlayerMotionManager.State.COLLAPSED)
                        }

                        BottomSheetBehavior.STATE_HIDDEN -> {
                            playerBottomSheetManager.collapse()
                        }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    playerMotionManager.updateProgress(slideOffset)
                }
            }
        )

        playerMotionManager = PlayerMotionManager(
            binding.motionLayout,
            playerBottomSheetManager,
            onProgressChanged = ::updateAlbumArtCornerRadius
        )

        binding.motionLayout.post {
            if (_binding == null) return@post
            playerMotionManager.changeState(vm.motionState.value)
        }
    }

    private fun initViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            vm.motionState
                .collect { state ->
                    playerMotionManager.changeState(state)

                    binding.tvSongTitle.gravity =
                        if (state == PlayerMotionManager.State.EXPANDED) {
                            Gravity.CENTER
                        } else {
                            Gravity.START or Gravity.CENTER_VERTICAL
                        }

                    binding.tvSongArtist.gravity =
                        if (state == PlayerMotionManager.State.EXPANDED) {
                            Gravity.CENTER
                        } else {
                            Gravity.START or Gravity.CENTER_VERTICAL
                        }

                    updatePlayerTextMarquee(state)

                    if (state == PlayerMotionManager.State.EXPANDED) {
                        startSeekUpdates()

                        // MotionLayout 배경을 제거해 containerPlayer의 그라데이션 배경이 보이게
                        binding.motionLayout.background = null

                        if (latestAlbumBitmap != null) {
                            albumGradientManager?.applyGradients(
                                latestAlbumBitmap!!,
                                binding.albumBackground
                            ) { visualizerColor ->
                                updateVisualizerBarColor(visualizerColor)
                            }
                        } else {
                            // 앨범 아트가 없으면 기본 어두운 배경으로 되돌림
                            // 기존 그라데이션도 함께 제거
                            albumGradientManager?.resetToDarkBackground(binding.albumBackground)
                        }
                    } else {
                        // 플레이어가 접히면 root gradient 위에 반투명 surface로 표시한다.
                        binding.motionLayout.setBackgroundResource(R.color.player_collapsed_scrim)
                        binding.albumBackground.setBackgroundResource(R.color.dark_black)

                        stopSeekUpdates()
                    }
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.musicList.observe(viewLifecycleOwner) { musicList ->
                if (_binding == null) return@observe

                playerModel.replaceMusicList(musicList)

                // 현재 ExoPlayer가 재생 중인 곡의 id를 우선 사용
                val currentMediaId = player.currentMediaItem?.mediaId
                val nowMusic = musicList.find { it.id == currentMediaId }
                    ?: musicList.find { it.id == musicKey }
                    ?: musicList.getOrNull(0)

                // PlayerModel과 UI를 동기화
                if (nowMusic != null) {
                    playerModel.updateCurrentMusic(nowMusic)
                    updatePlayerView(nowMusic)
                }

                // 이미 재생 중인 곡이 있으면 playMusic을 다시 호출하지 않는다.
                if (player.currentMediaItem == null && nowMusic != null) {
                    playMusic(musicList, nowMusic)
                }
            }
        }

        player.currentMediaItem?.mediaId?.let { mediaId ->
            val music = vm.musicList.value?.find { it.id == mediaId }
            if (music != null) {
                playerModel.updateCurrentMusic(music)
                updatePlayerView(music)
            }
        }
    }

    fun changeMusic(musicId: String) {
        val findIndex = vm.musicList.value.orEmpty()
            .indexOfFirst {
                it.id == musicId
            }

        if (findIndex != -1) {
            player.seekTo(findIndex, 0)
            player.play()
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
                player.seekTo(seekBar.progress * 1000L)
                savePlaybackState(immediate = true)
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
        binding.ivPlayPause.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                mediaControllerConnector.withController { controller ->
                    if (player.isPlaying) {
                        controller.pause()
                    } else {
                        controller.play()
                    }
                }
            }
        }

        binding.ivNext.setOnClickListener {
            if (moveToFirstTrack()) {
                MusicServiceController.refreshIfRunning(
                    context = requireContext(),
                    mediaId = player.currentMediaItem?.mediaId
                )
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                mediaControllerConnector.withController { controller ->
                    controller.seekToNext()
                }
            }
        }

        binding.ivPrevious.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                mediaControllerConnector.withController { controller ->
                    controller.seekToPrevious()
                }
            }
        }
    }

    private fun moveToFirstTrack(): Boolean {
        if (player.repeatMode != Player.REPEAT_MODE_OFF) return false
        if (player.mediaItemCount <= 0) return false
        if (player.currentMediaItemIndex != player.mediaItemCount - 1) return false

        player.playWhenReady = false
        player.seekTo(0, 0)
        player.pause()

        val firstId = player.getMediaItemAt(0).mediaId
        playerModel.changedMusic(firstId)
        playbackStateStore.saveLastPlayedMedia(
            LastPlayedMedia(
                type = PlaybackContentType.MUSIC,
                id = firstId,
                positionMs = 0L,
                playWhenReady = false,
                updatedAtMs = System.currentTimeMillis()
            )
        )
        updatePlayerView(playerModel.currentMusic)
        updatePlaybackProgress()
        syncPlayerUi()

        return true
    }

    private fun initPlayView() {
        binding.vPlayer.player = player

        albumGradientManager = MusicAlbumArtGradientManager(requireActivity())

        binding.tvSongTitle.text = playerModel.currentMusic?.title.orEmpty()
        binding.tvSongArtist.text = playerModel.currentMusic?.artist.orEmpty()
        binding.tvSongAlbum.text = playerModel.currentMusic?.album.orEmpty()

        resetVisualizerBarColor()
        syncPlayerUi()
        startSeekUpdates() // 포그라운드 진입 직후 진행바를 시작

        // 화면 재진입 등으로 initPlayView()가 여러 번 호출될 때 리스너 중복 등록을 막는다.
        playerListener?.let { player.removeListener(it) }

        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)

                // 재생 아이콘과 비주얼라이저 상태를 동기화
                syncPlayerUi()
                if (isPlaying) {
                    startSeekUpdates()
                } else {
                    // 일시정지/정지 시 현재 position을 저장
                    savePlaybackState(immediate = true)
                    stopSeekUpdates()
                }
            }

            // 미디어 아이템이 바뀔 때 상태를 갱신
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)

                val newMusicKey: String = mediaItem?.mediaId ?: return
                // 트랙이 바뀌면 새 트랙 position을 0으로 저장
                playbackStateStore.saveLastPlayedMedia(
                    LastPlayedMedia(
                        type = PlaybackContentType.MUSIC,
                        id = newMusicKey,
                        positionMs = 0L,
                        playWhenReady = player.playWhenReady,
                        updatedAtMs = System.currentTimeMillis()
                    )
                )
                playerModel.changedMusic(newMusicKey)

                latestAlbumBitmap = null

                updatePlayerView(playerModel.currentMusic)
                binding.root.removeCallbacks(updateSeekRunnable)
                updatePlaybackProgressUi(duration = 0L, position = 0L)

                // 트랙 전환 후 제목, 아티스트, 앨범 아트와 재생 UI를 동기화
                syncPlayerUi()
                if (player.isPlaying) {
                    binding.root.postDelayed(updateSeekRunnable, SEEK_UPDATE_AFTER_TRANSITION_MS)
                }
            }

            // 재생, 종료, 버퍼링 상태를 처리
            override fun onPlaybackStateChanged(state: Int) {
                super.onPlaybackStateChanged(state)

                updatePlaybackProgress()

                // 반복 해제 상태에서 마지막 트랙이 끝나면 첫 트랙으로 이동하고 일시정지
                if (player.repeatMode == Player.REPEAT_MODE_OFF &&
                    player.currentMediaItemIndex == player.mediaItemCount - 1 &&
                    state == Player.STATE_ENDED
                ) {
                    // 자동 재생을 방지
                    player.playWhenReady = false

                    // 첫 트랙으로 이동
                    player.seekTo(0, 0)

                    // PlayerModel과 UI를 동기화
                    if (player.mediaItemCount > 0) {
                        val firstId = player.getMediaItemAt(0).mediaId
                        playerModel.changedMusic(firstId)
                        playbackStateStore.saveLastPlayedId(PlaybackContentType.MUSIC, firstId)
                        updatePlayerView(playerModel.currentMusic)
                    }
                    player.pause()
                }
            }
        }

        player.addListener(playerListener!!)
    }

    private fun syncPlayerUi() {
        if (!isAdded || _binding == null) return // 뷰가 준비되지 않았거나 파괴된 상태면 작업하지 않는다.

        val isPlaying = player.isPlaying

        // 재생 상태에 맞춰 버튼 아이콘과 비주얼라이저 애니메이션을 갱신
        binding.ivPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause_button else R.drawable.ic_play_button
        )
        binding.animationViewVisualizer.setPlaying(isPlaying)
    }

    private fun updateVisualizerBarColor(color: Int) {
        if (_binding == null) return
        if (visualizerBarColor == color) return

        visualizerBarColor = color
        binding.animationViewVisualizer.setBarColor(color)
    }

    private fun resetVisualizerBarColor() {
        if (!isAdded || _binding == null) return
        updateVisualizerBarColor(ContextCompat.getColor(requireContext(), R.color.dark_gray))
    }

    private fun startSeekUpdates() {
        if (_binding == null) return

        binding.root.removeCallbacks(updateSeekRunnable)

        updatePlaybackProgress() // 즉시 한 번 갱신하고 다음 주기를 예약
    }

    private fun stopSeekUpdates() {
        if (_binding == null) return

        binding.root.removeCallbacks(updateSeekRunnable)
    }

    private fun updatePlaybackProgress() {
        if (_binding == null) return
        val player = this.player
        val duration = if (player.duration >= 0) player.duration else 0 // 전체 음악 길이
        val position = player.currentPosition

        updatePlaybackProgressUi(duration, position)

        if (player.isPlaying) {
            savePlaybackState(immediate = false)
        }

        val view = binding.root
        view.removeCallbacks(updateSeekRunnable)

        if (player.isPlaying) {
            val currentPositionMs = position.coerceAtLeast(0L)
            val delay = (ONE_SECOND_MS - (currentPositionMs % ONE_SECOND_MS) + SEEK_UPDATE_BOUNDARY_OFFSET_MS)
                .coerceIn(MIN_SEEK_UPDATE_DELAY_MS, MAX_SEEK_UPDATE_DELAY_MS) // 다음 갱신까지 기다릴 시간(ms)
            view.postDelayed(updateSeekRunnable, delay)
        }
    }

    private fun updatePlaybackProgressUi(duration: Long, position: Long) {
        // 재생 시작 직전이나 트랙 전환 중 발생할 수 있는 음수 값을 UI 계산 전에 보정
        val durationMs = duration.coerceAtLeast(0L)
        val positionMs = position.coerceAtLeast(0L)

        // 초 경계 근처에서 시간 텍스트가 늦게 바뀌는 느낌을 줄이기 위한 표시 보정치
        val displayPositionMs = if (durationMs > 0L) {
            (positionMs + POSITION_DISPLAY_OFFSET_MS).coerceAtMost(durationMs)
        } else {
            positionMs
        }

        binding.sbPlayer.max = (durationMs / ONE_SECOND_MS).toInt() // 전체 길이를 초 단위로 설정
        binding.sbPlayer.progress = (positionMs / ONE_SECOND_MS).toInt() // 현재 위치를 초 단위로 설정

        binding.tvCurrentPlayTime.text = String.format(
            Locale.KOREA,
            "%02d:%02d",
            TimeUnit.MINUTES.convert(displayPositionMs, TimeUnit.MILLISECONDS), // 현재 분
            (displayPositionMs / ONE_SECOND_MS) % 60 // 현재 초
        )

        binding.tvTotalTime.text = String.format(
            Locale.KOREA,
            "%02d:%02d",
            TimeUnit.MINUTES.convert(durationMs, TimeUnit.MILLISECONDS), // 전체 분
            (durationMs / ONE_SECOND_MS) % 60 // 전체 초
        )
    }

    private fun updatePlayerView(musicModel: MusicModel?) {
        if (_binding == null) return

        if (musicModel == null) {
            lastRenderedMusicId = null
            binding.root.removeCallbacks(startPlayerTextMarqueeRunnable)
            binding.tvSongTitle.isSelected = false

            binding.tvSongTitle.text = ""
            binding.tvSongArtist.text = ""
            binding.tvSongAlbum.text = ""

            latestAlbumBitmap = null
            resetVisualizerBarColor()
            albumGradientManager?.resetToDarkBackground(binding.albumBackground, animate = true)
            binding.ivAlbumArt.setImageResource(R.drawable.placeholder_album_art)

            return
        }

        if (lastRenderedMusicId == musicModel.id) {
            return
        }
        lastRenderedMusicId = musicModel.id

        binding.tvSongTitle.text = musicModel?.title.orEmpty()
        binding.tvSongArtist.text = musicModel?.artist.orEmpty()
        binding.tvSongAlbum.text = musicModel?.album.orEmpty()
        updatePlayerTextMarquee(vm.motionState.value)

        latestAlbumBitmap = null

        // Expanded 상태에서 Decode -> Startup/Collapsed 상태에서 로드할 때 나중에 stretch되지 않게 함
        val albumArtSize = resources.getDimensionPixelSize(R.dimen.album_art_size_expanded)
        val albumUri = musicModel.getAlbumUri()

        if (albumUri == null) {
            Glide.with(binding.ivAlbumArt.context).clear(binding.ivAlbumArt)
            binding.ivAlbumArt.setImageResource(R.drawable.placeholder_album_art)

            latestAlbumBitmap = null
            resetVisualizerBarColor()

            if (vm.motionState.value == PlayerMotionManager.State.EXPANDED) {
                albumGradientManager?.resetToDarkBackground(
                    binding.albumBackground,
                    animate = false
                )
            } else {
                binding.albumBackground.setBackgroundResource(R.color.dark_black)
            }

            return
        }

        Glide.with(binding.ivAlbumArt.context)
            .asBitmap()
            .load(albumUri)
            .override(albumArtSize, albumArtSize)
            .placeholder(binding.ivAlbumArt.drawable)
            .error(R.drawable.placeholder_album_art)
            .fallback(R.drawable.placeholder_album_art)
            .dontAnimate()
            .listener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<Bitmap>,
                    isFirstResource: Boolean
                ): Boolean {
                    // 로드 실패 시 플레이스홀더와 기본 배경으로 되돌린다.
                    latestAlbumBitmap = null
                    resetVisualizerBarColor()

                    if (vm.motionState.value == PlayerMotionManager.State.EXPANDED) {
                        albumGradientManager?.resetToDarkBackground(
                            binding.albumBackground,
                            animate = false
                        )
                    } else {
                        binding.albumBackground.setBackgroundResource(R.color.dark_black)
                    }

                    return false
                }

                override fun onResourceReady(
                    resource: Bitmap,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<Bitmap>,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    latestAlbumBitmap = resource

                    if (vm.motionState.value == PlayerMotionManager.State.EXPANDED) {
                        albumGradientManager?.applyGradients(
                            resource,
                            binding.albumBackground
                        ) { visualizerColor ->
                            updateVisualizerBarColor(visualizerColor)
                        }
                    } else {
                        binding.albumBackground.setBackgroundResource(R.color.dark_black)
                    }

                    return false
                }
            })
            .into(binding.ivAlbumArt)
    }

    private fun updatePlayerTextMarquee(state: PlayerMotionManager.State) {
        binding.root.removeCallbacks(startPlayerTextMarqueeRunnable)

        if (state == PlayerMotionManager.State.EXPANDED) {
            binding.tvSongTitle.isSelected = false
            // 트랙 정보 반영 직후 레이아웃이 안정된 뒤 marquee를 시작
            binding.root.postDelayed(startPlayerTextMarqueeRunnable, PLAYER_TEXT_MARQUEE_START_DELAY_MS)
        } else {
            binding.tvSongTitle.isSelected = false
        }
    }

    private fun updateAlbumArtCornerRadius(progress: Float) {
        val expandedRadius = resources.getDimension(R.dimen.album_art_corner_radius)
        val expandedSize = resources.getDimension(R.dimen.album_art_size_expanded)
        val collapsedSize = resources.getDimension(R.dimen.album_art_size_collapsed)
        val scaledCollapsedRadius = expandedRadius * (collapsedSize / expandedSize)
        val minCollapsedRadius = expandedRadius * MIN_COLLAPSED_ALBUM_ART_RADIUS_RATIO
        val collapsedRadius = max(scaledCollapsedRadius, minCollapsedRadius)
        val coercedProgress = progress.coerceIn(0f, 1f)
        val currentRadius = collapsedRadius + (expandedRadius - collapsedRadius) * coercedProgress

        binding.ivAlbumArt.shapeAppearanceModel = binding.ivAlbumArt.shapeAppearanceModel
            .toBuilder()
            .setAllCornerSizes(currentRadius)
            .build()
    }

    private fun savePlaybackState(immediate: Boolean = false) {
        val music = playerModel.currentMusic ?: return
        val now = System.currentTimeMillis()
        val position = player.currentPosition.coerceAtLeast(0L)

        if (!immediate) {
            val timeOk = (now - lastSavedAtMs) >= saveIntervalMs
            val deltaOk = kotlin.math.abs(position - lastSavedPositionMs) >= saveMinDeltaMs
            if (!timeOk || !deltaOk) return
        }

        lastSavedAtMs = now
        lastSavedPositionMs = position

        playbackStateStore.saveLastPlayedMedia(
            LastPlayedMedia(
                type = PlaybackContentType.MUSIC,
                id = music.id,
                positionMs = position,
                playWhenReady = player.playWhenReady,
                updatedAtMs = now
            )
        )
    }

    @OptIn(UnstableApi::class)
    private fun playMusic(musicList: List<MusicModel>, nowPlayMusic: MusicModel?) {
        if (nowPlayMusic != null) {
            currentMusic = nowPlayMusic
            playerModel.updateCurrentMusic(nowPlayMusic)
            playbackStateStore.saveLastPlayedId(PlaybackContentType.MUSIC, nowPlayMusic.id)
        }

        val musicMediaItems = musicList.map { music ->
            val defaultDataSourceFactory =
                DefaultDataSource.Factory(requireContext())
            val musicFileUri = music.getMusicFileUri()
            val mediaItem = MediaItem.Builder()
                .setMediaId(music.id)
                .setUri(musicFileUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(music.title)
                        .setArtist(music.artist)
                        .setAlbumTitle(music.album)
                        .setArtworkUri(music.getAlbumUri())
                        .build()
                )
                .build()

            ProgressiveMediaSource.Factory(defaultDataSourceFactory)
                .createMediaSource(mediaItem)
        }

        val playIndex = musicList.indexOf(nowPlayMusic)

        // 마지막 재생 상태를 로드하고 position을 복원
        val lastPlayed = playbackStateStore.loadLastPlayedMedia()
        val resumePositionMs =
            if (lastPlayed != null &&
                lastPlayed.type == PlaybackContentType.MUSIC &&
                lastPlayed.id.isNotBlank() &&
                nowPlayMusic != null &&
                lastPlayed.id == nowPlayMusic.id
            ) {
                lastPlayed.positionMs.coerceAtLeast(0L)
            } else {
                0L
            }

        player.run {
            setMediaSources(musicMediaItems)
            prepare()
            seekTo(max(playIndex, 0), resumePositionMs) // 마지막 재생 위치에서 시작
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }

        val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }

    private fun areBitmapsEqual(bitmap1: Bitmap, bitmap2: Bitmap): Boolean {
        return bitmap1.sameAs(bitmap2)
    }

    @OptIn(UnstableApi::class)
    private fun toggleShuffleModeIcon() {
        binding.ivShuffleMode.setOnClickListener {
            if (player.shuffleModeEnabled) { // 셔플 모드가 켜진 상태
                player.shuffleModeEnabled = false
                binding.ivShuffleMode.setImageResource(R.drawable.ic_shuffle_off)
            } else { // 셔플 모드가 꺼진 상태
                player.shuffleModeEnabled = true
                player.shuffleOrder = ShuffleOrder.DefaultShuffleOrder(vm.musicList.value.orEmpty().size, Random.nextLong())
                binding.ivShuffleMode.setImageResource(R.drawable.ic_shuffle_on)
            }
        }
    }

    private fun toggleRepeatModeIcon() {
        binding.ivRepeatMode.setOnClickListener {
            when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> { // 반복 재생 해제 상태
                    player.repeatMode = Player.REPEAT_MODE_ALL
                    binding.ivRepeatMode.setImageResource(R.drawable.ic_repeat_all_on)
                }
                Player.REPEAT_MODE_ALL -> { // 전체 반복 재생 상태
                    player.repeatMode = Player.REPEAT_MODE_ONE
                    binding.ivRepeatMode.setImageResource(R.drawable.ic_repeat_one_on)
                }
                else -> { // 한 곡 반복 재생 상태
                    player.repeatMode = Player.REPEAT_MODE_OFF
                    binding.ivRepeatMode.setImageResource(R.drawable.ic_repeat_all)
                }
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
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                    if (maxVolume <= 0) {
                        binding.ivVolume.setImageResource(R.drawable.ic_mute)
                        currentVolume = 0
                        binding.sbVolume.max = 0
                        binding.sbVolume.progress = 0
                        return@setOnClickListener
                    }

                    val restoredVolume = previousVolume.coerceIn(1, maxVolume)

                    binding.ivVolume.setImageResource(R.drawable.ic_volume)
                    currentVolume = restoredVolume
                    binding.sbVolume.max = maxVolume
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoredVolume, 0)
                    binding.sbVolume.progress = restoredVolume
                } else {
                    val currentSystemVolume =
                        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val maxVolume =
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                    if (currentSystemVolume > 0) {
                        previousVolume = currentSystemVolume
                    }

                    binding.ivVolume.setImageResource(R.drawable.ic_mute)
                    currentVolume = 0
                    binding.sbVolume.max = maxVolume.coerceAtLeast(0)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                    binding.sbVolume.progress = 0
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 화면이 다시 보일 때 블루투스 상태를 갱신
        if (_binding != null) {
            playerBluetoothManager.updateBluetoothIcon()
            playerBluetoothManager.refreshBluetoothIcon()
        }
        playerBluetoothManager.registerAudioDeviceCallback()

        // 포그라운드 복귀 시 진행 루프를 다시 시작
        startSeekUpdates()
        startVolumeObserver()   // 하드웨어 볼륨 변경 감지 시작
    }

    override fun onStop() {
        super.onStop()

        savePlaybackState(immediate = true)
        stopSeekUpdates()
        playerBluetoothManager.unregisterAudioDeviceCallback()
        stopVolumeObserver()    // 하드웨어 볼륨 변경 감지 중지
    }

    override fun onDestroyView() {
        // View 파괴 직전 마지막 상태를 저장
        savePlaybackState(immediate = true)
        // 리스너 참조로 인한 메모리 누수를 방지
        playerListener?.let { player.removeListener(it) }
        playerListener = null

        // View가 파괴된 뒤 player가 Surface를 붙잡지 않도록 분리
        _binding?.vPlayer?.player = null
        stopSeekUpdates()
        playerBluetoothManager.release()
        binding.root.removeCallbacks(startPlayerTextMarqueeRunnable)

        // 앨범 비트맵 참조를 해제 Glide가 관리하므로 recycle()은 호출하지 않는다.
        latestAlbumBitmap = null

        // 앨범 그라데이션 매니저 해제
        albumGradientManager = null
        mediaControllerConnector.release()

        lastRenderedMusicId = null // 렌더 캐시 초기화

        _binding = null
        super.onDestroyView()
    }

    // 시스템 볼륨과 UI를 동기화
    private fun updateVolumeFromSystem() {
        if (_binding == null) return
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        binding.sbVolume.max = max
        binding.sbVolume.progress = current
        currentVolume = current

        binding.ivVolume.setImageResource(
            if (current == 0) R.drawable.ic_mute else R.drawable.ic_volume
        )
    }

    // 볼륨 observer 등록/해제
    private fun startVolumeObserver() {
        val resolver = context?.contentResolver
        if (volumeObserver != null) return

        volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                updateVolumeFromSystem()
            }
        }

        try {
            resolver?.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                volumeObserver!!
            )
            // 등록 직후 한 번 동기화
            updateVolumeFromSystem()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register volumeObserver", t)
            // 등록 실패 시 누수를 방지
            volumeObserver = null
        }
    }

    private fun stopVolumeObserver() {
        val resolver = context?.contentResolver
        val observer = volumeObserver ?: return

        try {
            resolver?.unregisterContentObserver(observer)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to unregister volumeObserver", e)
        } finally {
            volumeObserver = null
        }
    }

    companion object {
        const val TAG = "PlayerFragment"
        const val EXTRA_MUSIC_FILE_ID: String = "id"

        // 재생 시간 계산의 기본 단위
        private const val ONE_SECOND_MS = 1_000L

        // 시간 텍스트 표시를 초 경계에 가깝게 맞추기 위한 보정값
        private const val POSITION_DISPLAY_OFFSET_MS = 80L

        // 다음 초가 지난 직후 UI를 갱신하기 위한 지연값
        private const val SEEK_UPDATE_BOUNDARY_OFFSET_MS = 50L

        // 다음 갱신이 너무 잦거나 1초보다 늦게 밀리지 않도록 제한하는 범위
        private const val MIN_SEEK_UPDATE_DELAY_MS = 100L // 너무 빠르지 않게
        private const val MAX_SEEK_UPDATE_DELAY_MS = 1_000L // 너무 늦지 않게

        // 자동 다음 곡 전환 직후 새 트랙 position을 다시 읽기까지 기다리는 시간
        private const val SEEK_UPDATE_AFTER_TRANSITION_MS = 100L

        private const val PLAYER_TEXT_MARQUEE_START_DELAY_MS = 700L

        private const val MIN_COLLAPSED_ALBUM_ART_RADIUS_RATIO = 0.5f

        fun newInstance(musicId: String): PlayerFragment =
            PlayerFragment().apply {
                arguments = Bundle().apply {
                    putString(EXTRA_MUSIC_FILE_ID, musicId)
                }
            }
    }
}
