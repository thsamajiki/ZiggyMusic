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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.hero.ziggymusic.playback.PlaybackQueueManager
import com.hero.ziggymusic.playback.currentMediaIds
import com.hero.ziggymusic.playback.currentPlaybackProgress
import com.hero.ziggymusic.playback.playbackProgressUpdates
import com.hero.ziggymusic.playback.toMediaItem
import com.hero.ziggymusic.service.MusicMediaControllerConnector
import com.hero.ziggymusic.service.MusicServiceController
import com.hero.ziggymusic.view.main.player.model.LastPlayedMedia
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    @Inject
    lateinit var playbackQueueManager: PlaybackQueueManager

    private var currentMusic: MusicModel? = null // 현재 재생 중인 음원
    private val playbackStateStore by lazy { PlaybackStateStore(requireContext()) }
    private var visualizerBarColor: Int? = null

    // position 저장 쓰로틀링으로 불필요한 prefs write를 줄인다.
    private var lastSavedAtMs: Long = 0L
    private var lastSavedPositionMs: Long = -1L
    private var isUserSeeking = false // 사용자가 재생바를 조작하는 동안 자동 진행률 갱신이 선택 위치를 덮어쓰지 않도록 추적

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
        observePlaybackProgress()
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

                        BottomSheetBehavior.STATE_DRAGGING -> {}
                        BottomSheetBehavior.STATE_HALF_EXPANDED -> {}
                        BottomSheetBehavior.STATE_SETTLING -> {}
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
                    }
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.musicList.observe(viewLifecycleOwner) { musicList ->
                if (_binding == null) return@observe
                if (musicList.isEmpty()) return@observe

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
                } else {
                    // 목록이 변경되면 현재 재생 상태를 유지한 채 Player 큐만 최신 목록으로 맞춘다.
                    playbackQueueManager.syncQueue(musicList)
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

    /**
     * 화면이 STARTED인 동안 재생 진행률을 관찰해 UI에 반영.
     * 주기적인 위치 갱신은 플레이어가 펼쳐진 상태에서만 활성화.
     */
    private fun observePlaybackProgress() {
        // 플레이어가 펼쳐진 상태에서만 주기적인 진행률 갱신을 허용.
        val updatesEnabled = vm.motionState
            .map { state ->
                state == PlayerMotionManager.State.EXPANDED
            }
            .distinctUntilChanged()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                player.playbackProgressUpdates(
                    updatesEnabled = updatesEnabled,
                ).collect { progress ->
                    updatePlaybackProgressUi(
                        duration = progress.durationMs,
                        position = progress.positionMs,
                    )

                    if (player.isPlaying) {
                        savePlaybackState(immediate = false)
                    }
                }
            }
        }
    }

    fun changeMusic(musicId: String) {
        val latestMusicList = vm.musicList.value.orEmpty()
        // 재생 요청 전에 큐를 최신 목록으로 동기화하여 새로 추가된 음원도 바로 재생되게 한다.
        val startedPlayback = playbackQueueManager.playMusic(
            musicList = latestMusicList,
            musicId = musicId
        )

        if (!startedPlayback) return

        playerModel.changedMusic(musicId)
        playerModel.currentMusic?.let { music ->
            updatePlayerView(music)
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
                isUserSeeking = true // 사용자 조작이 끝날 때까지 Flow의 자동 진행률 반영을 중단
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isUserSeeking = false // 사용자 조작이 끝났으므로 자동 진행률 반영을 다시 허용
                player.seekTo(seekBar.progress.toLong())
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
        // 마지막 곡에서 다음 버튼을 누른 경우의 Player 조작은 PlaybackQueueManager에 위임한다.
        val firstId = playbackQueueManager.moveToFirstTrackAndPauseIfAtEnd()
            ?: return false

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

        // 화면 재진입 등으로 initPlayView()가 여러 번 호출될 때 리스너 중복 등록을 막는다.
        playerListener?.let { player.removeListener(it) }

        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)

                // 재생 상태에 맞춰 재생 아이콘과 비주얼라이저 상태를 동기화
                syncPlayerUi()
                if (!isPlaying) {
                    // 일시정지/정지 시 마지막 재생 위치를 즉시 저장.
                    savePlaybackState(immediate = true)
                }
            }

            // 트랙 전환 시 저장 상태와 화면 정보를 새 트랙 기준으로 초기화
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
                // 새 트랙 정보가 반영되기 전 이전 트랙의 진행률이 노출되지 않도록 초기화
                updatePlaybackProgressUi(duration = 0L, position = 0L)

                // 재생 상태에 맞춰 재생 아이콘과 비주얼라이저 상태를 동기화
                syncPlayerUi()
            }

            // 재생, 종료, 버퍼링 상태를 처리
            override fun onPlaybackStateChanged(state: Int) {
                super.onPlaybackStateChanged(state)

                syncPlaybackProgressUi()

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

    // 플레이어의 현재 진행률을 읽어 재생바와 시간 UI를 즉시 동기화
    private fun syncPlaybackProgressUi() {
        val progress = player.currentPlaybackProgress()

        updatePlaybackProgressUi(
            duration = progress.durationMs,
            position = progress.positionMs,
        )
    }

    private fun updatePlaybackProgressUi(duration: Long, position: Long) {
        // 재생 시작 직전이나 트랙 전환 중 발생할 수 있는 음수 값을 UI 계산 전에 보정
        val durationMs = duration.coerceAtLeast(0L)
        val positionMs = position.coerceAtLeast(0L)

        // 초 경계 근처에서 시간 텍스트가 늦게 바뀌는 느낌을 줄이기 위한 표시 보정치
        val displayPositionMs = if (durationMs > 0L) {
            positionMs.coerceAtMost(durationMs)
        } else {
            positionMs
        }

        // 재생바가 초 단위로 끊기지 않도록 전체 길이와 현재 위치를 밀리초 단위로 반영한다.
        val seekBarMax = durationMs
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

        // 현재 위치가 전체 재생 시간과 SeekBar의 Int 범위를 넘지 않도록 보정한다.
        // 전체 재생 시간을 확인할 수 없는 경우 진행률을 0으로 초기화한다.
        val seekBarProgress = if (durationMs > 0L) {
            positionMs
                .coerceAtMost(durationMs)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        } else {
            0
        }

        // 드래그 중에는 Player의 주기적 갱신이 사용자가 선택한 위치를 덮어쓰지 않도록 한다.
        if (!isUserSeeking) {
            binding.sbPlayer.max = seekBarMax
            binding.sbPlayer.progress = seekBarProgress
        }

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

        binding.tvSongTitle.text = musicModel.title.orEmpty()
        binding.tvSongArtist.text = musicModel.artist.orEmpty()
        binding.tvSongAlbum.text = musicModel.album.orEmpty()
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

    private fun playMusic(musicList: List<MusicModel>, nowPlayMusic: MusicModel?) {
        if (nowPlayMusic == null) return

        val lastPlayedMedia = playbackStateStore.loadLastPlayedMedia()
        // 이전에 같은 음원을 듣고 있었다면 저장된 재생 위치부터 이어서 준비한다.
        val resumePositionMs =
            if (
                lastPlayedMedia?.type == PlaybackContentType.MUSIC &&
                lastPlayedMedia.id == nowPlayMusic.id
            ) {
                lastPlayedMedia.positionMs
            } else {
                0L
            }

        playerModel.updateCurrentMusic(nowPlayMusic)

        playbackStateStore.saveLastPlayedMedia(
            LastPlayedMedia(
                type = PlaybackContentType.MUSIC,
                id = nowPlayMusic.id,
                positionMs = resumePositionMs,
                playWhenReady = player.playWhenReady,
                updatedAtMs = System.currentTimeMillis()
            )
        )

        playbackQueueManager.prepareQueue(
            musicList = musicList,
            selectedMusic = nowPlayMusic,
            startPositionMs = resumePositionMs
        )

        updatePlayerView(nowPlayMusic)
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

        startVolumeObserver()   // 하드웨어 볼륨 변경 감지 시작
    }

    override fun onStop() {
        super.onStop()

        savePlaybackState(immediate = true)
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
