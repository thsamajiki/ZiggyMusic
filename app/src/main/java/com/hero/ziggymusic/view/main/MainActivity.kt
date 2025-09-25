package com.hero.ziggymusic.view.main

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.view.WindowInsets
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.navigation.NavigationBarView
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.PlayerModel
import com.hero.ziggymusic.databinding.ActivityMainBinding
import com.hero.ziggymusic.event.Event
import com.hero.ziggymusic.event.EventBus
import com.hero.ziggymusic.service.MusicService
import com.hero.ziggymusic.view.main.setting.SoundEQSettings
import com.hero.ziggymusic.view.main.setting.SettingFragment
import com.squareup.otto.Subscribe
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.hero.ziggymusic.view.main.musiclist.MusicListFragment
import com.hero.ziggymusic.view.main.myplaylist.MyPlaylistFragment

@AndroidEntryPoint
class MainActivity : AppCompatActivity(),
    NavigationBarView.OnItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private val vm by viewModels<MainViewModel>()
    private var title: String = ""

    @Inject
    lateinit var player: ExoPlayer
    private val playerModel: PlayerModel = PlayerModel.getInstance()
    private lateinit var playerController: PlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        initStatusBarColor()
        initBottomNavigationView()
        initViewModel()
        initPlayerController()
        initSoundEQSettings()
        initListeners()
        requestPermissions()

        EventBus.getInstance().register(this)
    }

    private fun initPlayerController() {
        playerController = PlayerController(
            this,
            binding.containerPlayer,
            supportFragmentManager,
            onStateChanged = { newState ->
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        binding.bottomNavMain.isGone = true
                        setPlayerExpandedMode(true)
                    }

                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        binding.bottomNavMain.isVisible = true
                        setPlayerExpandedMode(false)
                    }
                }
            })
    }

    private fun initListeners() {
        val titleArr = resources.getStringArray(R.array.title_array)

        binding.ivBack.setOnClickListener {
            supportFragmentManager.popBackStack()

            binding.ivBack.isInvisible = true
            binding.ivSetting.isVisible = true
            binding.ivSetting.isEnabled = true

            binding.tvMainTitle.text = title
        }

        binding.ivSetting.setOnClickListener {
            val settingFragment = supportFragmentManager.findFragmentByTag("setting")
                ?: SettingFragment.newInstance()

            supportFragmentManager.beginTransaction()
                .replace(binding.fcvMain.id, settingFragment, "setting")
                .addToBackStack("setting")
                .commit()
            supportFragmentManager.executePendingTransactions()

            binding.ivBack.isVisible = true
            binding.ivSetting.isInvisible = true
            binding.ivSetting.isEnabled = false

            binding.tvMainTitle.text = titleArr[2]
        }

        binding.bottomNavMain.setOnItemSelectedListener(this)
        binding.bottomNavMain.selectedItemId = R.id.menu_music_list
    }

    override fun onStart() {
        Log.d("onStart", "playerModel: $playerModel playerModel.currentMusic: ${playerModel.currentMusic}")
        musicServiceStart()

        super.onStart()
    }

    private fun initViewModel() {
        with(vm) {
            lifecycleScope.launch {
                musicList.observe(this@MainActivity) { musicList ->
                    playerModel.replaceMusicList(musicList)
                }
            }
        }
    }

    fun playMusic(musicId: String) {
        playerController.changeMusic(musicId)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val titleArr = resources.getStringArray(R.array.title_array)
        val transaction = supportFragmentManager.beginTransaction()
        when (item.itemId) {
            R.id.menu_music_list -> {
                val fragment = supportFragmentManager.findFragmentByTag("music_list")
                    ?: MusicListFragment.newInstance()
                transaction.replace(binding.fcvMain.id, fragment, "music_list").commit()
                binding.tvMainTitle.text = titleArr[0]
                title = titleArr[0]
            }
            R.id.menu_my_play_list -> {
                val fragment = supportFragmentManager.findFragmentByTag("my_play_list")
                    ?: MyPlaylistFragment.newInstance()
                transaction.replace(binding.fcvMain.id, fragment, "my_play_list").commit()
                binding.tvMainTitle.text = titleArr[1]
                title = titleArr[1]
            }
        }
        return true
    }

    // 미니 플레이어에서 사용하는 버튼에 리스너 세팅
    private fun setPlayerListener() {
        player.addListener(object : Player.Listener {
            // 미디어 아이템이 바뀔 때
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)

                val newMusicKey: String = mediaItem?.mediaId ?: return
                playerModel.changedMusic(newMusicKey)
            }
        })
    }

    // 서비스를 시작하는 메서드
    private fun musicServiceStart() {
        val serviceIntent = Intent(this, MusicService::class.java)
        startForegroundService(serviceIntent)
    }

    @Subscribe
    fun doEvent(event: Event) {
        val currentMusic = playerModel.currentMusic

        when(event.getEvent()) {
            "PLAY_NEW_MUSIC" -> { // 새로운 음원이 재생
                musicServiceStart()
            }
            "PLAY", "PAUSE" -> { // 재생(기존 음원), 일시 정지
                Log.d("MainActivity", "doEvent - PLAY PAUSE: ${player.isPlaying}")
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
                musicServiceStart()
            }
            "SKIP_PREV" -> { // Notification 에서 이전 곡 버튼을 누를 시
                Log.d("MainActivity", "doEvent - SKIP_PREV: ${player.isPlaying}")
                player.run {
                    val prevIndex = if (currentMediaItemIndex - 1 in 0 until mediaItemCount) {
                        currentMediaItemIndex - 1
                    } else {
                        // 전 곡 반복 재생 모드에서 0번 트랙에서 뒤로 갈 때 마지막 트랙으로 이동
                        if (player.repeatMode == Player.REPEAT_MODE_ALL) {
                            mediaItemCount - 1
                        } else {
                            0
                        }
                    }

                    seekTo(prevIndex, 0)
                }

                setPlayerListener()
                musicServiceStart()
            }
            "SKIP_NEXT" -> { // Notification 에서 다음 곡 버튼을 누를 시
                Log.d("MainActivity", "doEvent - SKIP_NEXT: ${player.isPlaying}")
                player.run {
                    val nextIndex = if (currentMediaItemIndex + 1 in 0 until mediaItemCount) {
                        currentMediaItemIndex + 1
                    } else {
                        0
                    }
                    seekTo(nextIndex, 0)
                }
                setPlayerListener()
                musicServiceStart()
            }
        }
    }

    private fun initStatusBarColor() {
        // statusBar 컬러를 toolBar 컬러와 동일하게 맞추기 위함
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
            window.decorView.setOnApplyWindowInsetsListener { view, insets ->
                val statusBarInsets = insets.getInsets(WindowInsets.Type.statusBars())
                view.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_black))

                insets
            }
        }
    }

    private fun initBottomNavigationView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavMain) { v, insets ->
            v.post {
                val loc = IntArray(2)
                v.getLocationOnScreen(loc)
            }
            insets
        }
        ViewCompat.requestApplyInsets(binding.bottomNavMain)
    }

    private fun requestPermissions() {
        val needs = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                needs += Manifest.permission.READ_MEDIA_AUDIO
            }
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                needs += Manifest.permission.POST_NOTIFICATIONS
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                needs += Manifest.permission.READ_EXTERNAL_STORAGE
            }
        }
        if (needs.isEmpty()) {
            playerController.startPlayer()
        } else {
            permissionLauncher.launch(needs.toTypedArray())
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            result[Manifest.permission.READ_MEDIA_AUDIO] == true
        else
            result[Manifest.permission.READ_EXTERNAL_STORAGE] == true

        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            result[Manifest.permission.POST_NOTIFICATIONS] == true
        else
            true

        if (!audioGranted) {
            // 오디오 권한 미허용
            if (shouldShowAudioRationale()) {
                showAudioPermissionDialog()
            } else {
                // 다시 묻지 않음(영구 거부) 또는 최초 즉시 거부(일부 OEM)
                showPermissionDeniedPermanentlyDialog(forNotification = false)
            }
            return@registerForActivityResult
        }

        // 오디오 권한 허용됨 -> 핵심 기능 시작
        playerController.startPlayer()

        // 알림 권한 선택적 처리
        if (!notifGranted) {
            if (shouldShowNotificationRationale()) {
                showNotificationPermissionDialog()
            } else {
                // 사용자가 알림을 영구 거부한 경우(또는 최초 거부) 안내
                showPermissionDeniedPermanentlyDialog(forNotification = true)
            }
        }
    }

    // Rationale 필요 여부 판단
    private fun shouldShowAudioRationale(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun shouldShowNotificationRationale(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
    }

    // 오디오 권한 Dialog
    private fun showAudioPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("오디오 권한 필요")
            .setMessage("기기 내부 음악 파일을 재생하려면 오디오(미디어) 읽기 권한이 필요합니다.")
            .setPositiveButton("다시 요청") { d, _ ->
                d.dismiss()
                val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                else
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                permissionLauncher.launch(perms)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // 알림 권한 Dialog
    private fun showNotificationPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("알림 권한 안내")
            .setMessage("백그라운드 재생 상태를 알림으로 표시하려면 알림 권한이 있으면 좋습니다. 허용하지 않아도 재생은 됩니다.")
            .setPositiveButton("요청") { d, _ ->
                d.dismiss()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
            }
            .setNegativeButton("나중에", null)
            .show()
    }

    // 영구 거부(설정 이동) 안내
    private fun showPermissionDeniedPermanentlyDialog(forNotification: Boolean) {
        val (title, msg) = if (forNotification) {
            "알림 권한 비활성화" to "설정에서 알림을 허용하면 백그라운드 재생 상태를 쉽게 확인할 수 있습니다. 지금 이동하시겠습니까?"
        } else {
            "오디오 권한 거부됨" to "음악을 재생할 수 없습니다. 설정에서 권한을 허용한 후 다시 시도하세요."
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton("설정 열기") { d, _ ->
                d.dismiss()
                openAppSettings()
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    // 앱 세부 설정 화면 이동
    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:$packageName".toUri()
        ).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun setPlayerExpandedMode(isExpanded: Boolean) {
        if (isExpanded) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.containerPlayer) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(
                    0,
                    systemBars.top,  // 상단 상태바
                    0,
                    systemBars.bottom  // 하단 시스템 바 (네비게이션 바 포함)
                )
                insets
            }
            ViewCompat.requestApplyInsets(binding.containerPlayer)
        } else {
            ViewCompat.setOnApplyWindowInsetsListener(binding.containerPlayer, null)
            binding.containerPlayer.setPadding(0, 0, 0, 0)
        }
    }

    @OptIn(UnstableApi::class)
    private fun initSoundEQSettings() {
        if (player.audioSessionId != 0) {
            SoundEQSettings.init(player.audioSessionId)
        } else {
            player.addListener(object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    if (audioSessionId != 0) {
                        SoundEQSettings.init(audioSessionId)
                        player.removeListener(this)
                    }
                }
            })
        }
    }

    override fun onDestroy() {
        EventBus.getInstance().unregister(this)

        super.onDestroy()
    }
}
