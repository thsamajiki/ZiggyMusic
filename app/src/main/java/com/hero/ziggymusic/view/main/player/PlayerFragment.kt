package com.hero.ziggymusic.view.main.player

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.annotation.OptIn
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.core.content.ContextCompat.startForegroundService
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.ShuffleOrder
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.database.music.entity.PlayerModel
import com.hero.ziggymusic.databinding.FragmentPlayerBinding
import com.hero.ziggymusic.service.MusicService
import com.hero.ziggymusic.service.MusicService.Companion.PLAY
import com.hero.ziggymusic.service.MusicService.Companion.PAUSE
import com.hero.ziggymusic.service.MusicService.Companion.SKIP_PREV
import com.hero.ziggymusic.service.MusicService.Companion.SKIP_NEXT
import com.hero.ziggymusic.view.main.player.viewmodel.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.max
import kotlin.random.Random
import androidx.core.graphics.createBitmap
import androidx.fragment.app.activityViewModels
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.hero.ziggymusic.view.main.player.model.LastPlayedMedia

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

    // position 저장 스로틀(너무 자주 prefs write 방지)
    private var lastSavedAtMs: Long = 0L
    private var lastSavedPositionMs: Long = -1L

    private val saveIntervalMs = 3_000L   // 3초 간격 저장(현업에서 흔한 수준)
    private val saveMinDeltaMs = 1_000L   // 위치 1초 이상 변했을 때만 저장

    private lateinit var playerMotionManager: PlayerMotionManager
    private lateinit var playerBottomSheetManager: PlayerBottomSheetManager
    private var albumGradientManager: MusicAlbumArtGradientManager? = null
    private var latestAlbumBitmap: Bitmap? = null

    private lateinit var audioManager: AudioManager
    private var currentVolume: Int = 0  // 현재 볼륨
    private var previousVolume: Int = 0 // 이전 볼륨 저장

    private var volumeObserver: ContentObserver? = null // 시스템 볼륨 변경 이벤트 옵저버

    private val musicKey: String
        get() = requireArguments().getString(EXTRA_MUSIC_FILE_ID).orEmpty()

    private val updateSeekRunnable = Runnable {
        updateSeek()
    }

    private val updateBluetoothRunnable = Runnable {
        updateBluetoothIcon()
        scheduleBluetoothUpdate()
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

        // DataBinding 변수(music)가 null이면 리바인딩 타이밍에 제목/아티스트가 빈 값으로 덮이는 간헐적 문제가 발생할 수 있음.
        // (앨범아트는 ImageView에 이미 로드되어 남아있어서 '텍스트만 사라진 것'처럼 보임)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.music = playerModel.currentMusic
        binding.executePendingBindings()

        initAudioManager()
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

    private fun initListeners() {
        binding.root.setOnClickListener {
            val toggleState = when (vm.motionState.value) {
                PlayerMotionManager.State.COLLAPSED -> PlayerMotionManager.State.EXPANDED
                PlayerMotionManager.State.EXPANDED -> PlayerMotionManager.State.COLLAPSED
            }

            vm.changeState(toggleState)
        }

        toggleVolumeIcon()
        toggleRepeatModeIcon()
        toggleShuffleModeIcon()
    }

    private fun initPlayerManager() {
        playerBottomSheetManager = PlayerBottomSheetManager(
            viewLifecycleOwner.lifecycle,
            binding.constraintLayout,
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
                }
            }
        )

        playerMotionManager = PlayerMotionManager(
            binding.constraintLayout,
            playerBottomSheetManager
        )
    }

    private fun initViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            vm.motionState
                .collect { state ->
                    playerMotionManager.changeState(state)

                    if (state == PlayerMotionManager.State.EXPANDED) {
                        startSeekUpdates()

                        // MotionLayout 배경 제거 -> containerPlayer 배경(그라데이션)이 보이도록 함
                        binding.constraintLayout.background = null

                        binding.albumBackground.setBackgroundResource(R.color.dark_black)

                        if (latestAlbumBitmap != null) {
                            albumGradientManager?.applyGradients(latestAlbumBitmap!!, binding.albumBackground)
                        } else {
                            // 앨범 아트가 없을 때 (리스트에서 클릭하여 진입한 경우 등)
                            // 그라데이션 대신 확실하게 dark_black 배경을 적용하고, 기존 그라데이션 제거
                            requireActivity().window.decorView.setBackgroundColor(ContextCompat.getColor(requireActivity(), R.color.dark_black))
                            requireActivity().findViewById<View>(R.id.containerPlayer)
                                ?.setBackgroundColor(ContextCompat.getColor(requireActivity(), R.color.dark_black))
                        }
                    } else {
                        // 플레이어가 닫히면(Collapsed) 투명해진 배경을 다시 원래 검은색으로 복구
                        binding.constraintLayout.setBackgroundResource(R.color.dark_black)
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

                // PlayerModel, UI 동기화
                if (nowMusic != null) {
                    playerModel.updateCurrentMusic(nowMusic)
                    updatePlayerView(nowMusic)
                }

                // 이미 재생 중인 곡이 있으면 playMusic을 다시 호출하지 않음
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
        // 재생 or 일시정지 버튼
        binding.ivPlayPause.setOnClickListener {
            val action =
                if (player.isPlaying) PAUSE
                else PLAY
            sendServiceAction(action)
        }

        binding.ivNext.setOnClickListener {
            sendServiceAction(SKIP_NEXT)
        }

        binding.ivPrevious.setOnClickListener {
            sendServiceAction(SKIP_PREV)
        }
    }

    private fun sendServiceAction(action: String) {
        val appCtx = requireContext().applicationContext
        val intent = Intent(appCtx, MusicService::class.java).apply {
            this.action = action
        }
        // 서비스가 이미 실행 중이면 일반 startService로 전달되고, O+에서 미실행이면 FG로 승격됨
        startForegroundService(appCtx, intent)
    }

    private fun initPlayView() {
        binding.vPlayer.player = player

        albumGradientManager = MusicAlbumArtGradientManager(requireActivity())

        syncPlayerUi()
        startSeekUpdates() // 포그라운드 진입 직후 진행바 시작

        // 화면 회전/재진입 등으로 initPlayView()가 여러 번 호출되면, 기존 리스너 위에 새 리스너가 계속 추가되어 콜백이 중복 실행됨 -> 이를 방지
        playerListener?.let { player.removeListener(it) }

        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)

                // 재생 아이콘 및 비주얼라이저 동기화
                syncPlayerUi()
                if (isPlaying) {
                    startSeekUpdates()
                } else {
                    // 일시정지/정지되는 순간 현재 position 저장
                    savePlaybackState(immediate = true)
                    stopSeekUpdates()
                }
            }

            // 미디어 아이템이 바뀔 때
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)

                val newMusicKey: String = mediaItem?.mediaId ?: return
                // 트랙이 바뀌면 새 트랙의 position=0으로 저장(직후)
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
                binding.albumBackground.setBackgroundResource(R.color.dark_black)

                updatePlayerView(playerModel.currentMusic)

                // 트랙 전환 시 제목/아티스트/앨범 아트, 재생/일시정지 아이콘 동기화
                syncPlayerUi()
            }

            // 재생, 재생 완료, 버퍼링 상태 ...
            override fun onPlaybackStateChanged(state: Int) {
                super.onPlaybackStateChanged(state)

                updateSeek()

                // 재생 반복 해제 모드 & 마지막 트랙 재생이 끝났을 때 -> 첫번째 트랙으로 이동 & 일시정지 상태
                if (player.repeatMode == Player.REPEAT_MODE_OFF &&
                    player.currentMediaItemIndex == player.mediaItemCount - 1 &&
                    state == Player.STATE_ENDED
                ) {
                    // 자동 재생 방지
                    player.playWhenReady = false

                    // 첫 트랙으로 이동
                    player.seekTo(0, 0)

                    // PlayerModel 및 UI 동기화
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
        if (!isAdded || _binding == null) return // 뷰가 준비되지 않았거나 파괴된 상태면 아무 작업도 하지 않음

        val isPlaying = player.isPlaying

        // 플레이어가 재생 또는 일시정지 될 때 재생/일시정지 버튼 아이콘 전환하고 애니메이션 재생/정지
        binding.ivPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause_button else R.drawable.ic_play_button
        )
        if (isPlaying) binding.animationViewVisualizer.playAnimation()
        else binding.animationViewVisualizer.pauseAnimation()
    }

    private fun startSeekUpdates() {
        if (_binding == null) return

        binding.root.removeCallbacks(updateSeekRunnable)

        updateSeek() // 즉시 1회 갱신하고, 내부에서 다음 주기를 예약
    }

    private fun stopSeekUpdates() {
        if (_binding == null) return

        binding.root.removeCallbacks(updateSeekRunnable)
    }

    private fun updateSeek() {
        if (_binding == null) return
        val player = this.player
        val duration = if (player.duration >= 0) player.duration else 0 // 전체 음악 길이
        val position = player.currentPosition

        updateSeekUi(duration, position)

        if (player.isPlaying) {
            savePlaybackState(immediate = false)
        }

        val view = binding.root
        view.removeCallbacks(updateSeekRunnable)

        if (player.isPlaying) {
            view.postDelayed(updateSeekRunnable, 1000L)
        }
    }

    private fun updateSeekUi(duration: Long, position: Long) {
        binding.sbPlayer.max = (duration / 1000).toInt() // 총 길이를 설정. 1000으로 나눠 작게
        binding.sbPlayer.progress = (position / 1000).toInt() // 동일하게 1000으로 나눠 작게

        binding.tvCurrentPlayTime.text = String.format(
            Locale.KOREA,
            "%02d:%02d",
            TimeUnit.MINUTES.convert(position, TimeUnit.MILLISECONDS), // 현재 분
            (position / 1000) % 60 // 분 단위를 제외한 현재 초
        )

        binding.tvTotalTime.text = String.format(
            Locale.KOREA,
            "%02d:%02d",
            TimeUnit.MINUTES.convert(duration, TimeUnit.MILLISECONDS), // 전체 분
            (duration / 1000) % 60 // 분 단위를 제외한 초
        )
    }

    private fun updatePlayerView(musicModel: MusicModel?) {
        if (_binding == null) return

        if (musicModel == null) {
            // 상태 초기화(필요 시)
            binding.music = null
            binding.executePendingBindings()

            binding.tvSongTitle.text = ""
            binding.tvSongArtist.text = ""
            binding.tvSongAlbum.text = ""

            latestAlbumBitmap = null
            binding.albumBackground.setBackgroundResource(R.color.dark_black)
            binding.ivAlbumArt.setImageResource(R.drawable.ic_no_album_image)
            return
        }

        // XML에서 tvSongTitle/tvSongArtist/tvSongAlbum 및 ivAlbumArt가 DataBinding(@{music.*})로 그려지고 있으므로,
        // music 변수를 갱신하지 않으면 리바인딩 타이밍에 텍스트가 null(또는 빈 문자열)로 덮일 수 있음.
        binding.music = musicModel
        binding.executePendingBindings()

        binding.tvSongTitle.text = musicModel.title
        binding.tvSongArtist.text = musicModel.artist
        binding.tvSongAlbum.text = musicModel.album

        latestAlbumBitmap = null
        binding.albumBackground.setBackgroundResource(R.color.dark_black)

        Glide.with(binding.ivAlbumArt.context)
            .asBitmap()
            .load(musicModel.getAlbumUri())
            .override(binding.ivAlbumArt.layoutParams.width, binding.ivAlbumArt.layoutParams.height)
            .error(R.drawable.ic_no_album_image)
            .fallback(R.drawable.ic_no_album_image)
            .transform(RoundedCorners(12))
            .listener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<Bitmap>,
                    isFirstResource: Boolean
                ): Boolean {
                    // 실패 시 플레이스홀더 설정 및 그라데이션 제거(또는 기본 처리)
                    latestAlbumBitmap = null

                    binding.albumBackground.setBackgroundResource(R.color.dark_black)

                    // 이전 곡이 그라데이션이었다면 여기서 리셋해줘야 StatusBar 뒤쪽도 검은색이 됨.
                    if (vm.motionState.value == PlayerMotionManager.State.EXPANDED) {
                        requireActivity().window.decorView.setBackgroundColor(ContextCompat.getColor(requireActivity(), R.color.dark_black))
                        requireActivity().findViewById<View>(R.id.containerPlayer)
                            ?.setBackgroundColor(ContextCompat.getColor(requireActivity(), R.color.dark_black))
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
                        albumGradientManager?.applyGradients(resource, binding.albumBackground)
                    } else {
                        binding.albumBackground.setBackgroundResource(R.color.dark_black)
                    }

                    return false
                }
            })
            .into(binding.ivAlbumArt)
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
                .build()

            ProgressiveMediaSource.Factory(defaultDataSourceFactory) // 미디어 정보를 가져오는 클래스
                .createMediaSource(mediaItem)
        }

        val playIndex = musicList.indexOf(nowPlayMusic)

        // 마지막 저장 상태 로드(있으면 position 복원)
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
            seekTo(max(playIndex, 0), resumePositionMs) // 마지막 재생 시간(초)부터 시작
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
            if (player.shuffleModeEnabled) { // 셔플 모드가 On일 때
                player.shuffleModeEnabled = false
                binding.ivShuffleMode.setImageResource(R.drawable.ic_shuffle_off)
            } else { // 셔플 모드가 Off일 때
                player.shuffleModeEnabled = true
                player.shuffleOrder = ShuffleOrder.DefaultShuffleOrder(vm.musicList.value.orEmpty().size, Random.nextLong())
                binding.ivShuffleMode.setImageResource(R.drawable.ic_shuffle_on)
            }
        }
    }

    private fun toggleRepeatModeIcon() {
        binding.ivRepeatMode.setOnClickListener {
            when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> { // 반복 재생 모드 해제 상태일 때
                    player.repeatMode = Player.REPEAT_MODE_ALL
                    binding.ivRepeatMode.setImageResource(R.drawable.ic_repeat_all_on)
                }
                Player.REPEAT_MODE_ALL -> { // 전 곡 반복 재생 모드일 때
                    player.repeatMode = Player.REPEAT_MODE_ONE
                    binding.ivRepeatMode.setImageResource(R.drawable.ic_repeat_one_on)
                }
                else -> { // 한 곡 반복 재생 모드일 때
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

    private fun updateBluetoothIcon() {
        if (_binding == null) return
        Log.d("Bluetooth", "Updating bluetooth icon...")

        try {
            // AudioManager.getDevices()를 활용하여 블루투스 및 유선 오디오 기기 체크 (Android 6.0 이상)
            val hasBluetoothDevice = isBluetoothAudioDeviceConnected(audioManager)
            val hasWiredDevice = isWiredAudioDeviceConnected(audioManager)

            Log.d(
                "Bluetooth",
                "Audio device detect - Bluetooth: $hasBluetoothDevice, Wired: $hasWiredDevice"
            )

            // 블루투스 오디오 기기가 연결되어 있고 유선 기기는 없을 때
            if (hasBluetoothDevice && !hasWiredDevice) {
                binding.bluetooth.setImageResource(R.drawable.ic_airpods)
                Log.d("Bluetooth", "Bluetooth audio is active - showing airpods icon")
                return
            }

            // 블루투스 권한 체크 후 추가 확인
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val bluetoothManager = ContextCompat.getSystemService(
                    requireContext(),
                    BluetoothManager::class.java
                )

                bluetoothManager?.let { manager ->
                    val bluetoothAdapter = manager.adapter

                    if (bluetoothAdapter?.isEnabled == true) {
                        // 연결된 블루투스 기기가 있는지 확인
                        val isBluetoothConnected = checkBluetoothConnection(manager)

                        if (isBluetoothConnected) {
                            binding.bluetooth.setImageResource(R.drawable.ic_airpods)
                            Log.d(
                                "Bluetooth",
                                "Bluetooth device connected via profile - showing airpods icon"
                            )
                            return
                        }
                    }
                }
            }

            // 기본값: 블루투스가 연결되지 않음
            binding.bluetooth.setImageResource(R.drawable.ic_airplay)
            Log.d("Bluetooth", "No bluetooth connection detected - showing airplay icon")

        } catch (e: Exception) {
            Log.e("Bluetooth", "Error updating bluetooth icon", e)
            binding.bluetooth.setImageResource(R.drawable.ic_airplay)
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkBluetoothConnection(bluetoothManager: BluetoothManager): Boolean {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        try {
            val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

            // 블루투스 profile connections
            val a2dpConnected =
                bluetoothAdapter?.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothAdapter.STATE_CONNECTED
            val headsetConnected =
                bluetoothAdapter?.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED

            if (a2dpConnected || headsetConnected) {
                Log.d(
                    "Bluetooth",
                    "Audio Profile connected: A2DP=$a2dpConnected, HEADSET=$headsetConnected"
                )
                return true
            }

            // GATT connections 검사
            val bluetoothDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
            bluetoothDevices.forEach { device ->
                val connectionState =
                    bluetoothManager.getConnectionState(device, BluetoothGatt.GATT)
                if (connectionState == BluetoothGatt.STATE_CONNECTED) {
                    Log.d("Bluetooth", "Connected GATT device found: ${device.name}")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e("Bluetooth", "Error checking bluetooth connection", e)
        }

        return false
    }

    // AudioDeviceInfo를 이용한 블루투스 오디오 기기 탐지
    private fun isBluetoothAudioDeviceConnected(audioManager: AudioManager): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (device in devices) {
            when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    -> {
                    Log.d("Bluetooth", "Bluetooth output device connected: type=${device.type}")
                    return true
                }
            }
        }

        return false
    }

    // AudioDeviceInfo를 이용한 유선 오디오 기기 탐지
    private fun isWiredAudioDeviceConnected(audioManager: AudioManager): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (device in devices) {
            when (device.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    -> {
                    Log.d("Bluetooth", "Wired output device connected: type=${device.type}")
                    return true
                }
            }
        }

        return false
    }

    override fun onResume() {
        super.onResume()
        // 화면이 다시 보일 때 블루투스 상태 업데이트
        if (_binding != null) {
            updateBluetoothIcon()
            scheduleBluetoothUpdate()
        }

        // 포그라운드 복귀 시 반드시 루프 재시작
        startSeekUpdates()
        startVolumeObserver()   // 하드웨어 볼륨 변경 감지 시작
    }

    override fun onStop() {
        super.onStop()

        savePlaybackState(immediate = true)
        stopSeekUpdates()
        binding.root.removeCallbacks(updateBluetoothRunnable)
        stopVolumeObserver()    // 하드웨어 볼륨 변경 감지 중지
    }

    override fun onDestroyView() {
        // View 파괴 직전 마지막 상태 저장(안전망)
        savePlaybackState(immediate = true)
        // 리스너 참조로 인한 메모리/수명 누수 방지
        playerListener?.let { player.removeListener(it) }
        playerListener = null

        // Surface 리소스 누수(Fragment의 View가 파괴된 뒤에도 플레이어가 그 Surface를 붙잡고 있어 해제되지 않는 상황)를 방지하기 위해 뷰에서 플레이어를 분리
        _binding?.vPlayer?.player = null
        stopSeekUpdates()
        binding.root.removeCallbacks(updateBluetoothRunnable)

        // 앨범 비트맵 해제하여 메모리 누수 방지 (Glide에 의해 관리되므로 수동 recycle() 호출은 하지 않음)
        latestAlbumBitmap = null

        // 앨범 그라데이션 매니저 해제
        albumGradientManager = null

        _binding = null
        super.onDestroyView()
    }

    private fun scheduleBluetoothUpdate() {
        if (_binding == null) return

        binding.root.removeCallbacks(updateBluetoothRunnable)
    }

    // 시스템 볼륨 → UI 동기화
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

    // 볼륨 옵저버 등록/해제
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
            // 등록 직후 1회 동기화
            updateVolumeFromSystem()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register volumeObserver", t)
            // 등록 실패 시 누수 방지
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

        fun newInstance(musicId: String): PlayerFragment =
            PlayerFragment().apply {
                arguments = Bundle().apply {
                    putString(EXTRA_MUSIC_FILE_ID, musicId)
                }
            }
    }
}
