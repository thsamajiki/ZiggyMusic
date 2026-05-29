package com.hero.ziggymusic.view.main.player

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanResult
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.core.app.ActivityCompat
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
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.database.music.entity.PlayerModel
import com.hero.ziggymusic.databinding.FragmentPlayerBinding
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
import com.hero.ziggymusic.service.MusicMediaControllerConnector
import com.hero.ziggymusic.service.MusicServiceController
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

    private var currentMusic: MusicModel? = null // ?ÑÏû¨ ?¨ÏÉù Ï§ëÏù∏ ?åÏõê
    private val playbackStateStore by lazy { PlaybackStateStore(requireContext()) }

    // position ?Ä???§Î°ú?Ä(?àÎ¨¥ ?êÏ£º prefs write Î∞©Ï?)
    private var lastSavedAtMs: Long = 0L
    private var lastSavedPositionMs: Long = -1L

    private val saveIntervalMs = 3_000L   // 3Ï¥?Í∞ÑÍ≤© ?Ä???ÑÏóÖ?êÏÑú ?îÌïú ?òÏ?)
    private val saveMinDeltaMs = 1_000L   // ?ÑÏπò 1Ï¥??¥ÏÉÅ Î≥Ä?àÏùÑ ?åÎßå ?Ä??

    private lateinit var playerMotionManager: PlayerMotionManager
    private lateinit var playerBottomSheetManager: PlayerBottomSheetManager
    private lateinit var mediaControllerConnector: MusicMediaControllerConnector
    private var albumGradientManager: MusicAlbumArtGradientManager? = null
    private var latestAlbumBitmap: Bitmap? = null
    private var lastRenderedMusicId: String? = null

    private lateinit var audioManager: AudioManager
    private var currentVolume: Int = 0  // ?ÑÏû¨ Î≥ºÎ•®
    private var previousVolume: Int = 0 // ?¥Ï†Ñ Î≥ºÎ•® ?Ä??

    private var volumeObserver: ContentObserver? = null // ?úÏä§??Î≥ºÎ•® Î≥ÄÍ≤??¥Î≤§???µÏ?Î≤?

    private val musicKey: String
        get() = requireArguments().getString(EXTRA_MUSIC_FILE_ID).orEmpty()

    private val updateSeekRunnable = Runnable {
        updatePlaybackProgress()
    }

    private val updateBluetoothRunnable = Runnable {
        updateBluetoothIcon()
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (addedDevices.any { it.isBluetoothOrWiredAudioDevice() }) {
                updateBluetoothIcon()
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.isBluetoothOrWiredAudioDevice() }) {
                updateBluetoothIcon()
            }
        }
    }

    private var isAudioDeviceCallbackRegistered = false

    private val bluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grantResults ->
            if (grantResults.isNotEmpty() && grantResults.values.all { it }) {
                handleBluetoothClick()
            } else {
                showToast("Î∏îÎ£®?¨Ïä§ Í∂åÌïú???ÑÏöî?©Îãà??")
            }
        }

    private val companionDeviceChooserLauncher: ActivityResultLauncher<IntentSenderRequest> =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

            val selectedDevice = result.data?.extractSelectedBluetoothDevice()
            if (selectedDevice == null) {
                showToast("?†ÌÉù??Î∏îÎ£®?¨Ïä§ Í∏∞Í∏∞Î•??ïÏù∏?????ÜÏäµ?àÎã§.")
                return@registerForActivityResult
            }

            requestBluetoothPairing(selectedDevice)
        }

    private val bluetoothBondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return

            val device = intent.getBluetoothDeviceExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
            val pendingAddress = pendingPairingDeviceAddress ?: return
            if (!isPairingTarget(device, pendingAddress)) return

            val deviceName = getBluetoothDeviceLogName(device)
            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
            when (bondState) {
                BluetoothDevice.BOND_BONDING -> {
                    Log.d("Bluetooth", "Bluetooth pairing in progress: $deviceName ($pendingAddress)")
                }

                BluetoothDevice.BOND_BONDED -> {
                    if (_binding == null) return
                    binding.bluetooth.setImageResource(R.drawable.ic_airpods)
                    pendingPairingDeviceAddress = null
                    showToast("Î∏îÎ£®?¨Ïä§ ?òÏñ¥ÎßÅÏù¥ ?ÑÎ£å?òÏóà?µÎãà??")
                    Log.d("Bluetooth", "Bluetooth pairing completed: $deviceName ($pendingAddress)")
                }

                BluetoothDevice.BOND_NONE -> {
                    pendingPairingDeviceAddress = null
                    showToast("Î∏îÎ£®?¨Ïä§ ?òÏñ¥ÎßÅÏóê ?§Ìå®?àÏäµ?àÎã§.")
                    Log.w("Bluetooth", "Bluetooth pairing failed or canceled: $deviceName ($pendingAddress)")
                    updateBluetoothIcon()
                }
            }
        }
    }

    private val companionDeviceManagerCallback = object : CompanionDeviceManager.Callback() {
        override fun onAssociationPending(intentSender: IntentSender) {
            launchCompanionDeviceChooser(intentSender)
        }

        @Deprecated("onDeviceFound was renamed to onAssociationPending in API 33.")
        override fun onDeviceFound(intentSender: IntentSender) {
            launchCompanionDeviceChooser(intentSender)
        }

        override fun onFailure(error: CharSequence?) {
            val errorMessage = error?.toString()
            if (errorMessage.isCompanionDeviceChooserCancellation()) return

            Log.w("Bluetooth", "Companion device association failed: $errorMessage")
            showToast("Î∏îÎ£®?¨Ïä§ Í∏∞Í∏∞ Î™©Î°ù??Î∂àÎü¨?§Ï? Î™ªÌñà?µÎãà??")
        }
    }

    private var pendingPairingDeviceAddress: String? = null
    private var isBluetoothBondStateReceiverRegistered = false

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

        // DataBinding Î≥Ä??music)Í∞Ä null?¥Î©¥ Î¶¨Î∞î?∏Îî© ?Ä?¥Î∞ç???úÎ™©/?ÑÌã∞?§Ìä∏Í∞Ä Îπ?Í∞íÏúºÎ°???ù¥??Í∞ÑÌóê??Î¨∏Ï†úÍ∞Ä Î∞úÏÉù?????àÏùå.
        // (?®Î≤î?ÑÌä∏??ImageView???¥Î? Î°úÎìú?òÏñ¥ ?®ÏïÑ?àÏñ¥??'?çÏä§?∏Îßå ?¨ÎùºÏß?Í≤?Ï≤òÎüº Î≥¥ÏûÑ)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.music = playerModel.currentMusic
        binding.executePendingBindings()

        initMediaController()
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
            handleBluetoothClick()
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
            playerBottomSheetManager
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

                        // MotionLayout Î∞∞Í≤Ω ?úÍ±∞ -> containerPlayer Î∞∞Í≤Ω(Í∑∏Îùº?∞Ïù¥????Î≥¥Ïù¥?ÑÎ°ù ??
                        binding.motionLayout.background = null

                        if (latestAlbumBitmap != null) {
                            albumGradientManager?.applyGradients(latestAlbumBitmap!!, binding.albumBackground)
                        } else {
                            // ?®Î≤î ?ÑÌä∏Í∞Ä ?ÜÏùÑ ??(Î¶¨Ïä§?∏Ïóê???¥Î¶≠?òÏó¨ ÏßÑÏûÖ??Í≤ΩÏö∞ ??
                            // Í∑∏Îùº?∞Ïù¥???Ä???ïÏã§?òÍ≤å dark_black Î∞∞Í≤Ω???ÅÏö©?òÍ≥†, Í∏∞Ï°¥ Í∑∏Îùº?∞Ïù¥???úÍ±∞
                            albumGradientManager?.resetToDarkBackground(binding.albumBackground)
                        }
                    } else {
                        // ?åÎ†à?¥Ïñ¥Í∞Ä ?´ÌûàÎ©?Collapsed) ?¨Î™Ö?¥ÏßÑ Î∞∞Í≤Ω???§Ïãú ?êÎûò Í≤Ä?Ä?âÏúºÎ°?Î≥µÍµ¨
                        binding.motionLayout.setBackgroundResource(R.color.dark_black)
                        binding.albumBackground.setBackgroundResource(R.color.dark_black)

                        stopSeekUpdates()
                    }
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.musicList.observe(viewLifecycleOwner) { musicList ->
                if (_binding == null) return@observe

                playerModel.replaceMusicList(musicList)

                // ?ÑÏû¨ ExoPlayerÍ∞Ä ?¨ÏÉù Ï§ëÏù∏ Í≥°Ïùò idÎ•??∞ÏÑ† ?¨Ïö©
                val currentMediaId = player.currentMediaItem?.mediaId
                val nowMusic = musicList.find { it.id == currentMediaId }
                    ?: musicList.find { it.id == musicKey }
                    ?: musicList.getOrNull(0)

                // PlayerModel, UI ?ôÍ∏∞??
                if (nowMusic != null) {
                    playerModel.updateCurrentMusic(nowMusic)
                    updatePlayerView(nowMusic)
                }

                // ?¥Î? ?¨ÏÉù Ï§ëÏù∏ Í≥°Ïù¥ ?àÏúºÎ©?playMusic???§Ïãú ?∏Ï∂ú?òÏ? ?äÏùå
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

        syncPlayerUi()
        startSeekUpdates() // ?¨Í∑∏?ºÏö¥??ÏßÑÏûÖ ÏßÅÌõÑ ÏßÑÌñâÎ∞??úÏûë

        // ?îÎ©¥ ?åÏ†Ñ/?¨ÏßÑ???±ÏúºÎ°?initPlayView()Í∞Ä ?¨Îü¨ Î≤??∏Ï∂ú?òÎ©¥, Í∏∞Ï°¥ Î¶¨Ïä§???ÑÏóê ??Î¶¨Ïä§?àÍ? Í≥ÑÏÜç Ï∂îÍ??òÏñ¥ ÏΩúÎ∞±??Ï§ëÎ≥µ ?§Ìñâ??-> ?¥Î? Î∞©Ï?
        playerListener?.let { player.removeListener(it) }

        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)

                // ?¨ÏÉù ?ÑÏù¥ÏΩ?Î∞?ÎπÑÏ£º?ºÎùº?¥Ï? ?ôÍ∏∞??
                syncPlayerUi()
                if (isPlaying) {
                    startSeekUpdates()
                } else {
                    // ?ºÏãú?ïÏ?/?ïÏ??òÎäî ?úÍ∞Ñ ?ÑÏû¨ position ?Ä??
                    savePlaybackState(immediate = true)
                    stopSeekUpdates()
                }
            }

            // ÎØ∏Îîî???ÑÏù¥?úÏù¥ Î∞îÎÄ???
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)

                val newMusicKey: String = mediaItem?.mediaId ?: return
                // ?∏Îûô??Î∞îÎÄåÎ©¥ ???∏Îûô??position=0?ºÎ°ú ?Ä??ÏßÅÌõÑ)
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

                // ?∏Îûô ?ÑÌôò ???úÎ™©/?ÑÌã∞?§Ìä∏/?®Î≤î ?ÑÌä∏, ?¨ÏÉù/?ºÏãú?ïÏ? ?ÑÏù¥ÏΩ??ôÍ∏∞??
                syncPlayerUi()
                if (player.isPlaying) {
                    binding.root.postDelayed(updateSeekRunnable, SEEK_UPDATE_AFTER_TRANSITION_MS)
                }
            }

            // ?¨ÏÉù, ?¨ÏÉù ?ÑÎ£å, Î≤ÑÌçºÎß??ÅÌÉú ...
            override fun onPlaybackStateChanged(state: Int) {
                super.onPlaybackStateChanged(state)

                updatePlaybackProgress()

                // ?¨ÏÉù Î∞òÎ≥µ ?¥Ï†ú Î™®Îìú & ÎßàÏ?Îß??∏Îûô ?¨ÏÉù???ùÎÇ¨????-> Ï≤´Î≤àÏß??∏Îûô?ºÎ°ú ?¥Îèô & ?ºÏãú?ïÏ? ?ÅÌÉú
                if (player.repeatMode == Player.REPEAT_MODE_OFF &&
                    player.currentMediaItemIndex == player.mediaItemCount - 1 &&
                    state == Player.STATE_ENDED
                ) {
                    // ?êÎèô ?¨ÏÉù Î∞©Ï?
                    player.playWhenReady = false

                    // Ï≤??∏Îûô?ºÎ°ú ?¥Îèô
                    player.seekTo(0, 0)

                    // PlayerModel Î∞?UI ?ôÍ∏∞??
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
        if (!isAdded || _binding == null) return // Î∑∞Í? Ï§ÄÎπÑÎêòÏßÄ ?äÏïòÍ±∞ÎÇò ?åÍ¥¥???ÅÌÉúÎ©??ÑÎ¨¥ ?ëÏóÖ???òÏ? ?äÏùå

        val isPlaying = player.isPlaying

        // ?åÎ†à?¥Ïñ¥Í∞Ä ?¨ÏÉù ?êÎäî ?ºÏãú?ïÏ? ?????¨ÏÉù/?ºÏãú?ïÏ? Î≤ÑÌäº ?ÑÏù¥ÏΩ??ÑÌôò?òÍ≥† ?†ÎãàÎ©îÏù¥???¨ÏÉù/?ïÏ?
        binding.ivPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause_button else R.drawable.ic_play_button
        )
        if (isPlaying) binding.animationViewVisualizer.playAnimation()
        else binding.animationViewVisualizer.pauseAnimation()
    }

    private fun startSeekUpdates() {
        if (_binding == null) return

        binding.root.removeCallbacks(updateSeekRunnable)

        updatePlaybackProgress() // Ï¶âÏãú 1??Í∞±Ïã†?òÍ≥†, ?¥Î??êÏÑú ?§Ïùå Ï£ºÍ∏∞Î•??àÏïΩ
    }

    private fun stopSeekUpdates() {
        if (_binding == null) return

        binding.root.removeCallbacks(updateSeekRunnable)
    }

    private fun updatePlaybackProgress() {
        if (_binding == null) return
        val player = this.player
        val duration = if (player.duration >= 0) player.duration else 0 // ?ÑÏ≤¥ ?åÏïÖ Í∏∏Ïù¥
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
                .coerceIn(MIN_SEEK_UPDATE_DELAY_MS, MAX_SEEK_UPDATE_DELAY_MS) // ?§Ïùå??updatePlaybackProgress()Î•??§Ïãú ?§Ìñâ?òÎäî??Í±∏Î¶¨???úÍ∞Ñ (ms)
            view.postDelayed(updateSeekRunnable, delay)
        }
    }

    private fun updatePlaybackProgressUi(duration: Long, position: Long) {
        // ?¨ÏÉù ?úÏûë ÏßÅÏ†Ñ, ?∏Îûô ?ÑÌôò Ï§ëÏóê ?§Ïñ¥?????àÎäî ?åÏàò Í∞íÏùÑ UI Í≥ÑÏÇ∞ ?ÑÏóê Î≥¥Ï†ï
        val durationMs = duration.coerceAtLeast(0L)
        val positionMs = position.coerceAtLeast(0L)

        // Ï¥?Í≤ΩÍ≥Ñ Í∑ºÏ≤ò?êÏÑú ?úÍ∞Ñ ?çÏä§?∏Í? ??≤å Î∞îÎÄåÎäî ?êÎÇå??Ï§ÑÏù¥Í∏??ÑÌïú ?úÏãú???ÑÏπò
        val displayPositionMs = if (durationMs > 0L) {
            (positionMs + POSITION_DISPLAY_OFFSET_MS).coerceAtMost(durationMs)
        } else {
            positionMs
        }

        binding.sbPlayer.max = (durationMs / ONE_SECOND_MS).toInt() // Ï¥?Í∏∏Ïù¥Î•??§Ï†ï. 1000?ºÎ°ú ?òÎà† ?ëÍ≤å
        binding.sbPlayer.progress = (positionMs / ONE_SECOND_MS).toInt() // ?ôÏùº?òÍ≤å 1000?ºÎ°ú ?òÎà† ?ëÍ≤å

        binding.tvCurrentPlayTime.text = String.format(
            Locale.KOREA,
            "%02d:%02d",
            TimeUnit.MINUTES.convert(displayPositionMs, TimeUnit.MILLISECONDS), // ?ÑÏû¨ Î∂?
            (displayPositionMs / ONE_SECOND_MS) % 60 // Î∂??®ÏúÑÎ•??úÏô∏???ÑÏû¨ Ï¥?
        )

        binding.tvTotalTime.text = String.format(
            Locale.KOREA,
            "%02d:%02d",
            TimeUnit.MINUTES.convert(durationMs, TimeUnit.MILLISECONDS), // ?ÑÏ≤¥ Î∂?
            (durationMs / ONE_SECOND_MS) % 60 // Î∂??®ÏúÑÎ•??úÏô∏??Ï¥?
        )
    }

    private fun updatePlayerView(musicModel: MusicModel?) {
        if (_binding == null) return

        if (musicModel == null) {
            lastRenderedMusicId = null
            binding.root.removeCallbacks(startPlayerTextMarqueeRunnable)
            binding.tvSongTitle.isSelected = false

            // ?ÅÌÉú Ï¥àÍ∏∞???ÑÏöî ??
            binding.music = null
            binding.executePendingBindings()

            binding.tvSongTitle.text = ""
            binding.tvSongArtist.text = ""
            binding.tvSongAlbum.text = ""

            latestAlbumBitmap = null
            albumGradientManager?.resetToDarkBackground(binding.albumBackground, animate = true)
            binding.ivAlbumArt.setImageResource(R.drawable.ic_no_album_image)
            return
        }

        // XML?êÏÑú tvSongTitle/tvSongArtist/tvSongAlbum Î∞?ivAlbumArtÍ∞Ä DataBinding(@{music.*})Î°?Í∑∏Î†§ÏßÄÍ≥??àÏúºÎØÄÎ°?
        // music Î≥Ä?òÎ? Í∞±Ïã†?òÏ? ?äÏúºÎ©?Î¶¨Î∞î?∏Îî© ?Ä?¥Î∞ç???çÏä§?∏Í? null(?êÎäî Îπ?Î¨∏Ïûê??Î°???ùº ???àÏùå.
        if (lastRenderedMusicId == musicModel.id) {
            return
        }
        lastRenderedMusicId = musicModel.id

        binding.music = musicModel
        binding.executePendingBindings()
        updatePlayerTextMarquee(vm.motionState.value)

        latestAlbumBitmap = null

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
                    // ?§Ìå® ???åÎ†à?¥Ïä§?Ä???§Ï†ï Î∞?Í∑∏Îùº?∞Ïù¥???úÍ±∞(?êÎäî Í∏∞Î≥∏ Ï≤òÎ¶¨)
                    latestAlbumBitmap = null

                    if (vm.motionState.value == PlayerMotionManager.State.EXPANDED) {
                        albumGradientManager?.resetToDarkBackground(
                            binding.albumBackground,
                            animate = true
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
                        albumGradientManager?.applyGradients(resource, binding.albumBackground)
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
            // ?∏Îûô ?ïÎ≥¥ Î∞òÏòÅ ÏßÅÌõÑ ?àÏù¥?ÑÏõÉ???àÏ†ï????marqueeÎ•??úÏûë?úÎã§.
            binding.root.postDelayed(startPlayerTextMarqueeRunnable, PLAYER_TEXT_MARQUEE_START_DELAY_MS)
        } else {
            binding.tvSongTitle.isSelected = false
        }
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

            ProgressiveMediaSource.Factory(defaultDataSourceFactory) // ÎØ∏Îîî???ïÎ≥¥Î•?Í∞Ä?∏Ïò§???¥Îûò??
                .createMediaSource(mediaItem)
        }

        val playIndex = musicList.indexOf(nowPlayMusic)

        // ÎßàÏ?Îß??Ä???ÅÌÉú Î°úÎìú(?àÏúºÎ©?position Î≥µÏõê)
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
            seekTo(max(playIndex, 0), resumePositionMs) // ÎßàÏ?Îß??¨ÏÉù ?úÍ∞Ñ(Ï¥?Î∂Ä???úÏûë
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
            if (player.shuffleModeEnabled) { // ?îÌîå Î™®ÎìúÍ∞Ä On????
                player.shuffleModeEnabled = false
                binding.ivShuffleMode.setImageResource(R.drawable.ic_shuffle_off)
            } else { // ?îÌîå Î™®ÎìúÍ∞Ä Off????
                player.shuffleModeEnabled = true
                player.shuffleOrder = ShuffleOrder.DefaultShuffleOrder(vm.musicList.value.orEmpty().size, Random.nextLong())
                binding.ivShuffleMode.setImageResource(R.drawable.ic_shuffle_on)
            }
        }
    }

    private fun toggleRepeatModeIcon() {
        binding.ivRepeatMode.setOnClickListener {
            when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> { // Î∞òÎ≥µ ?¨ÏÉù Î™®Îìú ?¥Ï†ú ?ÅÌÉú????
                    player.repeatMode = Player.REPEAT_MODE_ALL
                    binding.ivRepeatMode.setImageResource(R.drawable.ic_repeat_all_on)
                }
                Player.REPEAT_MODE_ALL -> { // ??Í≥?Î∞òÎ≥µ ?¨ÏÉù Î™®Îìú????
                    player.repeatMode = Player.REPEAT_MODE_ONE
                    binding.ivRepeatMode.setImageResource(R.drawable.ic_repeat_one_on)
                }
                else -> { // ??Í≥?Î∞òÎ≥µ ?¨ÏÉù Î™®Îìú????
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

    private fun handleBluetoothClick() {
        val bluetoothAdapter = getBluetoothAdapter()
        if (bluetoothAdapter == null) {
            showToast("??Í∏∞Í∏∞??Î∏îÎ£®?¨Ïä§Î•?ÏßÄ?êÌïòÏßÄ ?äÏäµ?àÎã§.")
            return
        }

        val missingPermissions = getMissingBluetoothPermissions()
        if (missingPermissions.isNotEmpty()) {
            bluetoothPermissionLauncher.launch(missingPermissions.toTypedArray())
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            showToast("Î∏îÎ£®?¨Ïä§Î•?ÏºúÏ£º?∏Ïöî.")
            return
        }

        showBluetoothDeviceChooser()
    }

    private fun getBluetoothAdapter(): BluetoothAdapter? {
        return ContextCompat.getSystemService(
            requireContext(),
            BluetoothManager::class.java
        )?.adapter
    }

    private fun getMissingBluetoothPermissions(): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyList()

        return listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        ).filter { permission ->
            ActivityCompat.checkSelfPermission(requireContext(), permission) !=
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showBluetoothDeviceChooser() {
        val companionDeviceManager = ContextCompat.getSystemService(
            requireContext(),
            CompanionDeviceManager::class.java
        )

        if (companionDeviceManager == null) {
            showToast("Î∏îÎ£®?¨Ïä§ Í∏∞Í∏∞ Î™©Î°ù??Î∂àÎü¨?????ÜÏäµ?àÎã§.")
            return
        }

        val requestBuilder = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().build())
            .setSingleDevice(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestBuilder.setDisplayName("ZiggyMusic")
        }

        val request = requestBuilder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            companionDeviceManager.associate(
                request,
                ContextCompat.getMainExecutor(requireContext()),
                companionDeviceManagerCallback
            )
        } else {
            companionDeviceManager.associate(
                request,
                companionDeviceManagerCallback,
                Handler(Looper.getMainLooper())
            )
        }
    }

    private fun launchCompanionDeviceChooser(intentSender: IntentSender) {
        try {
            val request = IntentSenderRequest.Builder(intentSender).build()
            companionDeviceChooserLauncher.launch(request)
        } catch (e: IntentSender.SendIntentException) {
            Log.e("Bluetooth", "Failed to launch companion device chooser", e)
            showToast("Î∏îÎ£®?¨Ïä§ Í∏∞Í∏∞ Î™©Î°ù???¥Ï? Î™ªÌñà?µÎãà??")
        }
    }

    private fun requestBluetoothPairing(device: BluetoothDevice) {
        if (_binding == null) return

        if (getMissingBluetoothPermissions().isNotEmpty()) {
            bluetoothPermissionLauncher.launch(getMissingBluetoothPermissions().toTypedArray())
            return
        }

        registerBluetoothBondStateReceiver()
        val deviceAddress = getBluetoothDeviceAddressIfPermitted(device) ?: run {
            showToast("Î∏îÎ£®?¨Ïä§ Í∏∞Í∏∞ ?ïÎ≥¥Î•??ïÏù∏?????ÜÏäµ?àÎã§.")
            return
        }

        pendingPairingDeviceAddress = deviceAddress
        cancelBluetoothDiscoveryIfPermitted()

        when (getBluetoothBondStateIfPermitted(device)) {
            null -> {
                pendingPairingDeviceAddress = null
                showToast("Î∏îÎ£®?¨Ïä§ Í∂åÌïú???ÑÏöî?©Îãà??")
            }

            BluetoothDevice.BOND_BONDED -> {
                if (isBluetoothAudioDeviceConnected(audioManager)) {
                    binding.bluetooth.setImageResource(R.drawable.ic_airpods)
                } else {
                    showToast("?¥Î? ?òÏñ¥ÎßÅÎêú Í∏∞Í∏∞?ÖÎãà?? Î∏îÎ£®?¨Ïä§ ?§Ï†ï?êÏÑú ?∞Í≤∞?¥Ï£º?∏Ïöî.")
                    openBluetoothSettings()
                    updateBluetoothIcon()
                }
                pendingPairingDeviceAddress = null
                Log.d("Bluetooth", "Bluetooth device is already paired: $deviceAddress")
            }

            BluetoothDevice.BOND_BONDING -> {
                Log.d("Bluetooth", "Bluetooth device is already bonding: $deviceAddress")
            }

            else -> {
                val pairingStarted = createBluetoothBondIfPermitted(device)
                Log.d("Bluetooth", "Bluetooth pairing requested: $deviceAddress, started=$pairingStarted")
                if (!pairingStarted) {
                    pendingPairingDeviceAddress = null
                    showToast("Î∏îÎ£®?¨Ïä§ ?òÏñ¥ÎßÅÏùÑ ?úÏûë?òÏ? Î™ªÌñà?µÎãà??")
                    updateBluetoothIcon()
                }
            }
        }
    }

    private fun getBluetoothDeviceAddressIfPermitted(device: BluetoothDevice): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        return try {
            device.address
        } catch (e: SecurityException) {
            Log.w("Bluetooth", "Missing permission while reading bluetooth device address", e)
            null
        }
    }

    private fun getBluetoothDeviceLogName(device: BluetoothDevice): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return "Unknown device"
        }

        return try {
            device.name ?: "Unknown device"
        } catch (e: SecurityException) {
            Log.w("Bluetooth", "Missing permission while reading bluetooth device name", e)
            "Unknown device"
        }
    }

    private fun getBluetoothBondStateIfPermitted(device: BluetoothDevice): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        return try {
            device.bondState
        } catch (e: SecurityException) {
            Log.w("Bluetooth", "Missing permission while reading bluetooth bond state", e)
            null
        }
    }

    private fun createBluetoothBondIfPermitted(device: BluetoothDevice): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return try {
            device.createBond()
        } catch (e: SecurityException) {
            Log.w("Bluetooth", "Missing permission while creating bluetooth bond", e)
            false
        }
    }

    private fun isPairingTarget(device: BluetoothDevice, pendingAddress: String): Boolean {
        return getBluetoothDeviceAddressIfPermitted(device) == pendingAddress
    }

    private fun cancelBluetoothDiscoveryIfPermitted() {
        if (!hasBluetoothScanPermission()) return

        try {
            getBluetoothAdapter()?.cancelDiscovery()
        } catch (e: SecurityException) {
            Log.w("Bluetooth", "Missing permission while canceling bluetooth discovery", e)
        }
    }

    private fun hasBluetoothScanPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openBluetoothSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }.onFailure { e ->
            Log.e("Bluetooth", "Failed to open bluetooth settings", e)
        }
    }

    private fun registerBluetoothBondStateReceiver() {
        if (isBluetoothBondStateReceiverRegistered) return

        ContextCompat.registerReceiver(
            requireContext(),
            bluetoothBondStateReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        isBluetoothBondStateReceiverRegistered = true
    }

    private fun unregisterBluetoothBondStateReceiver() {
        if (!isBluetoothBondStateReceiverRegistered) return

        requireContext().unregisterReceiver(bluetoothBondStateReceiver)
        isBluetoothBondStateReceiverRegistered = false
    }

    private fun Intent.extractSelectedBluetoothDevice(): BluetoothDevice? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val associatedDevice = getAssociationInfoExtra(
                CompanionDeviceManager.EXTRA_ASSOCIATION
            )?.associatedDevice

            val selectedDevice = associatedDevice?.bluetoothDevice
                ?: associatedDevice?.bleDevice?.device

            if (selectedDevice != null) return selectedDevice
        }

        @Suppress("DEPRECATION")
        val legacyDevice = getBluetoothDeviceExtra(CompanionDeviceManager.EXTRA_DEVICE)
        if (legacyDevice != null) return legacyDevice

        @Suppress("DEPRECATION")
        val legacyLeDevice = getBleScanResultExtra(
            CompanionDeviceManager.EXTRA_DEVICE
        )?.device

        return legacyLeDevice
    }

    private fun Intent.getBluetoothDeviceExtra(name: String): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(name)
        }
    }

    private fun Intent.getAssociationInfoExtra(name: String): AssociationInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, AssociationInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(name)
        }
    }

    private fun Intent.getBleScanResultExtra(name: String): ScanResult? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, ScanResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(name)
        }
    }

    private fun String?.isCompanionDeviceChooserCancellation(): Boolean {
        if (isNullOrBlank()) return false

        val normalizedMessage = lowercase(Locale.US)
        return "cancel" in normalizedMessage ||
                "cancelled" in normalizedMessage ||
                "canceled" in normalizedMessage
    }

    private fun showToast(message: String) {
        context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
    }

    private fun updateBluetoothIcon() {
        if (_binding == null) return
        try {
            // AudioManager.getDevices()Î•??úÏö©?òÏó¨ Î∏îÎ£®?¨Ïä§ Î∞??†ÏÑ† ?§Îîî??Í∏∞Í∏∞ Ï≤¥ÌÅ¨ (Android 6.0 ?¥ÏÉÅ)
            val hasBluetoothDevice = isBluetoothAudioDeviceConnected(audioManager)
            val hasWiredDevice = isWiredAudioDeviceConnected(audioManager)

            // Î∏îÎ£®?¨Ïä§ ?§Îîî??Í∏∞Í∏∞Í∞Ä ?∞Í≤∞?òÏñ¥ ?àÍ≥† ?†ÏÑ† Í∏∞Í∏∞???ÜÏùÑ ??
            if (hasBluetoothDevice && !hasWiredDevice) {
                binding.bluetooth.setImageResource(R.drawable.ic_airpods)
                return
            }

            // Î∏îÎ£®?¨Ïä§ Í∂åÌïú Ï≤¥ÌÅ¨ ??Ï∂îÍ? ?ïÏù∏
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
                        // ?∞Í≤∞??Î∏îÎ£®?¨Ïä§ Í∏∞Í∏∞Í∞Ä ?àÎäîÏßÄ ?ïÏù∏
                        val isBluetoothConnected = checkBluetoothConnection(manager)

                        if (isBluetoothConnected) {
                            binding.bluetooth.setImageResource(R.drawable.ic_airpods)
                            return
                        }
                    }
                }
            }

            // Í∏∞Î≥∏Í∞? Î∏îÎ£®?¨Ïä§Í∞Ä ?∞Í≤∞?òÏ? ?äÏùå
            binding.bluetooth.setImageResource(R.drawable.ic_airplay)
        } catch (e: Exception) {
            Log.e("Bluetooth", "Error updating bluetooth icon", e)
            binding.bluetooth.setImageResource(R.drawable.ic_airplay)
        }
    }


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

            // Î∏îÎ£®?¨Ïä§ profile connections
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

        } catch (e: Exception) {
            Log.e("Bluetooth", "Error checking bluetooth connection", e)
        }

        return false
    }

    // AudioDeviceInfoÎ•??¥Ïö©??Î∏îÎ£®?¨Ïä§ ?§Îîî??Í∏∞Í∏∞ ?êÏ?
    private fun isBluetoothAudioDeviceConnected(audioManager: AudioManager): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (device in devices) {
            when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    -> {
                    return true
                }
                else -> {}
            }
        }

        return false
    }

    // AudioDeviceInfoÎ•??¥Ïö©???†ÏÑ† ?§Îîî??Í∏∞Í∏∞ ?êÏ?
    private fun isWiredAudioDeviceConnected(audioManager: AudioManager): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (device in devices) {
            when (device.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    -> {
                    return true
                }
                else -> {}
            }
        }

        return false
    }

    override fun onResume() {
        super.onResume()
        // ?îÎ©¥???§Ïãú Î≥¥Ïùº ??Î∏îÎ£®?¨Ïä§ ?ÅÌÉú ?ÖÎç∞?¥Ìä∏
        if (_binding != null) {
            updateBluetoothIcon()
            scheduleBluetoothUpdate()
        }
        registerAudioDeviceCallback()

        // ?¨Í∑∏?ºÏö¥??Î≥µÍ? ??Î∞òÎìú??Î£®ÌîÑ ?¨Ïãú??
        startSeekUpdates()
        startVolumeObserver()   // ?òÎìú?®Ïñ¥ Î≥ºÎ•® Î≥ÄÍ≤?Í∞êÏ? ?úÏûë
    }

    override fun onStop() {
        super.onStop()

        savePlaybackState(immediate = true)
        stopSeekUpdates()
        binding.root.removeCallbacks(updateBluetoothRunnable)
        unregisterAudioDeviceCallback()
        stopVolumeObserver()    // ?òÎìú?®Ïñ¥ Î≥ºÎ•® Î≥ÄÍ≤?Í∞êÏ? Ï§ëÏ?
    }

    override fun onDestroyView() {
        // View ?åÍ¥¥ ÏßÅÏ†Ñ ÎßàÏ?Îß??ÅÌÉú ?Ä???àÏ†ÑÎß?
        savePlaybackState(immediate = true)
        // Î¶¨Ïä§??Ï∞∏Ï°∞Î°??∏Ìïú Î©îÎ™®Î¶??òÎ™Ö ?ÑÏàò Î∞©Ï?
        playerListener?.let { player.removeListener(it) }
        playerListener = null

        // Surface Î¶¨ÏÜå???ÑÏàò(Fragment??ViewÍ∞Ä ?åÍ¥¥???§Ïóê???åÎ†à?¥Ïñ¥Í∞Ä Í∑?SurfaceÎ•?Î∂ôÏû°Í≥??àÏñ¥ ?¥Ï†ú?òÏ? ?äÎäî ?ÅÌô©)Î•?Î∞©Ï??òÍ∏∞ ?ÑÌï¥ Î∑∞Ïóê???åÎ†à?¥Ïñ¥Î•?Î∂ÑÎ¶¨
        _binding?.vPlayer?.player = null
        stopSeekUpdates()
        binding.root.removeCallbacks(updateBluetoothRunnable)
        unregisterAudioDeviceCallback()
        binding.root.removeCallbacks(startPlayerTextMarqueeRunnable)
        unregisterBluetoothBondStateReceiver()

        // ?®Î≤î ÎπÑÌä∏Îß??¥Ï†ú?òÏó¨ Î©îÎ™®Î¶??ÑÏàò Î∞©Ï? (Glide???òÌï¥ Í¥ÄÎ¶¨ÎêòÎØÄÎ°??òÎèô recycle() ?∏Ï∂ú?Ä ?òÏ? ?äÏùå)
        latestAlbumBitmap = null

        // ?®Î≤î Í∑∏Îùº?∞Ïù¥??Îß§Îãà?Ä ?¥Ï†ú
        albumGradientManager = null
        mediaControllerConnector.release()

        lastRenderedMusicId = null // ?åÎçîÎß?Ï∫êÏãúÎ•?Ï¥àÍ∏∞??

        _binding = null
        super.onDestroyView()
    }

    private fun scheduleBluetoothUpdate() {
        if (_binding == null) return

        binding.root.removeCallbacks(updateBluetoothRunnable)
        binding.root.postDelayed(updateBluetoothRunnable, BLUETOOTH_STATUS_UPDATE_DELAY_MS)
    }

    private fun registerAudioDeviceCallback() {
        if (isAudioDeviceCallbackRegistered) return

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        isAudioDeviceCallbackRegistered = true
    }

    private fun unregisterAudioDeviceCallback() {
        if (!isAudioDeviceCallbackRegistered) return

        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        isAudioDeviceCallbackRegistered = false
    }

    private fun AudioDeviceInfo.isBluetoothOrWiredAudioDevice(): Boolean {
        return when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
                -> true

            else -> false
        }
    }

    // ?úÏä§??Î≥ºÎ•® ??UI ?ôÍ∏∞??
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

    // Î≥ºÎ•® ?µÏ?Î≤??±Î°ù/?¥Ï†ú
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
            // ?±Î°ù ÏßÅÌõÑ 1???ôÍ∏∞??
            updateVolumeFromSystem()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register volumeObserver", t)
            // ?±Î°ù ?§Ìå® ???ÑÏàò Î∞©Ï?
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

        // ?¨ÏÉù ?úÍ∞Ñ Í≥ÑÏÇ∞??Í∏∞Î≥∏ ?®ÏúÑ
        private const val ONE_SECOND_MS = 1_000L

        // ?úÍ∞Ñ ?çÏä§???úÏãúÎ•?Ï¥?Í≤ΩÍ≥Ñ????Í∞ÄÍπùÍ≤å ÎßûÏ∂îÍ∏??ÑÌïú Î≥¥Ï†ïÍ∞?
        private const val POSITION_DISPLAY_OFFSET_MS = 80L

        // ?§Ïùå Ï¥àÍ? ÏßÄ??ÏßÅÌõÑ UIÎ•?Í∞±Ïã†?òÍ∏∞ ?ÑÌïú ÏßÄ?∞Í∞í
        private const val SEEK_UPDATE_BOUNDARY_OFFSET_MS = 50L

        // ?§Ïùå Í∞±Ïã†???àÎ¨¥ ?êÏ£º ?åÍ±∞??1Ï¥??òÍ≤å Î∞ÄÎ¶¨Ï? ?äÎèÑÎ°??úÌïú?òÎäî Î≤îÏúÑ
        private const val MIN_SEEK_UPDATE_DELAY_MS = 100L // ?ÑÎ¨¥Î¶?Îπ®Îùº??
        private const val MAX_SEEK_UPDATE_DELAY_MS = 1_000L // ?ÑÎ¨¥Î¶???ñ¥??

        // ?êÎèô ?§Ïùå Í≥??ÑÌôò ÏßÅÌõÑ ???∏Îûô position???§Ïãú ?ΩÍ∏∞ ?ÑÍπåÏßÄ Í∏∞Îã§Î¶¨Îäî ?úÍ∞Ñ
        private const val SEEK_UPDATE_AFTER_TRANSITION_MS = 100L

        private const val PLAYER_TEXT_MARQUEE_START_DELAY_MS = 700L
        private const val BLUETOOTH_STATUS_UPDATE_DELAY_MS = 1_000L

        fun newInstance(musicId: String): PlayerFragment =
            PlayerFragment().apply {
                arguments = Bundle().apply {
                    putString(EXTRA_MUSIC_FILE_ID, musicId)
                }
            }
    }
}
