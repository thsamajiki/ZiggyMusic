package com.hero.ziggymusic.view.main

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.navigation.NavigationBarView
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.PlayerStateHolder
import com.hero.ziggymusic.databinding.ActivityMainBinding
import com.hero.ziggymusic.view.main.setting.AudioEffectManager
import com.hero.ziggymusic.view.main.setting.SettingsFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.hero.ziggymusic.playback.PlaybackQueueSource
import com.hero.ziggymusic.service.MusicService
import com.hero.ziggymusic.service.MusicServiceController
import com.hero.ziggymusic.view.main.model.MainTitle
import com.hero.ziggymusic.view.main.musiclist.MusicTracksFragment
import com.hero.ziggymusic.view.main.favorites.FavoriteTracksFragment
import com.hero.ziggymusic.view.main.player.PlaybackContentType
import com.hero.ziggymusic.view.main.player.LastPlaybackStore
import com.hero.ziggymusic.view.main.player.PlayerMotionManager
import com.hero.ziggymusic.view.main.player.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), NavigationBarView.OnItemSelectedListener {
    private lateinit var binding: ActivityMainBinding
    private val vm by viewModels<MainViewModel>()
    private val playerVm by viewModels<PlayerViewModel>()

    @Inject
    lateinit var player: ExoPlayer
    private val playerStateHolder: PlayerStateHolder = PlayerStateHolder.getInstance()
    private lateinit var playerController: PlayerController
    private val lastPlaybackStore by lazy { LastPlaybackStore(this) }

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
    }

    private fun initPlayerController() {
        playerController = PlayerController(
            this,
            binding.containerPlayer,
            supportFragmentManager,
            onStateChanged = { newState ->
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED,
                    BottomSheetBehavior.STATE_DRAGGING,
                    BottomSheetBehavior.STATE_SETTLING -> {
                        binding.bottomNavMain.isGone = true
                        if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                            playerVm.changeState(PlayerMotionManager.State.EXPANDED)
                        }
                    }

                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        binding.bottomNavMain.isVisible = true
                        playerVm.changeState(PlayerMotionManager.State.COLLAPSED)
                    }
                }
            })
    }

    private fun initListeners() {
        binding.ivBack.setOnClickListener {
            supportFragmentManager.popBackStack()
        }

        // 백스택 변화에 맞춰 현재 화면의 타이틀과 상단 버튼 상태를 동기화한다.
        supportFragmentManager.addOnBackStackChangedListener {
            val currentFragment =
                supportFragmentManager.fragments
                    .lastOrNull { fragment ->
                        fragment.id == binding.fcvMain.id &&
                                fragment.isAdded &&
                                !fragment.isHidden
                    }

            when (currentFragment) {
                is MusicTracksFragment -> vm.setTitle(MainTitle.MusicList)
                is FavoriteTracksFragment -> vm.setTitle(MainTitle.Favorites)
                is SettingsFragment -> vm.setTitle(MainTitle.Setting)
            }
        }

        // 현재 메인 탭을 숨기고 설정 화면을 백스택 위에 표시한다.
        binding.ivSetting.setOnClickListener {
            // 설정 화면이 이미 표시 중이면 중복 실행하지 않는다.
            val existingSettingFragment =
                supportFragmentManager.findFragmentByTag(TAG_SETTING)

            if (
                existingSettingFragment != null &&
                existingSettingFragment.isAdded &&
                !existingSettingFragment.isHidden
            ) {
                return@setOnClickListener
            }

            val currentMainFragment =
                findCurrentMainTabFragment()
                    ?: return@setOnClickListener

            val settingsFragment = existingSettingFragment ?: SettingsFragment.newInstance()

            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .hide(currentMainFragment)
                .setMaxLifecycle(
                    currentMainFragment,
                    Lifecycle.State.STARTED
                )
                .apply {
                    if (settingsFragment.isAdded) {
                        show(settingsFragment)
                    } else {
                        add(
                            binding.fcvMain.id,
                            settingsFragment,
                            TAG_SETTING
                        )
                    }
                }
                .setMaxLifecycle(
                    settingsFragment,
                    Lifecycle.State.RESUMED
                )
                .setPrimaryNavigationFragment(settingsFragment)
                .addToBackStack(TAG_SETTING)
                .commit()
        }

        initMainTabFragments()

        binding.bottomNavMain.setOnItemSelectedListener(this)
        binding.bottomNavMain.setOnItemReselectedListener {
            val settingFragment = supportFragmentManager.findFragmentByTag(TAG_SETTING)

            if (
                settingFragment != null &&
                settingFragment.isAdded &&
                !settingFragment.isHidden
            ) {
                supportFragmentManager.popBackStackImmediate()
            }
        }

        prepareFavoritesTabAfterFirstDraw()
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onResume() {
        super.onResume()

        startPlayerIfAudioPermissionGranted()
    }

    private fun initViewModel() {
        with(vm) {
            lifecycleScope.launch {
                musicList.observe(this@MainActivity) { musicList ->
                    playerStateHolder.replaceMusicList(musicList)
                }

                // Title과 UI 상태를 한번에 관찰
                currentTitle.observe(this@MainActivity) { mainTitle ->
                    binding.tvMainTitle.text = getString(mainTitle.resId)
                    binding.ivBack.isVisible = mainTitle.showBackButton
                    binding.ivSetting.isVisible = mainTitle.showSettingButton
                    binding.ivSetting.isEnabled = mainTitle.showSettingButton
                }
            }
        }

        lifecycleScope.launch {
            playerVm.motionState
                .map { it == PlayerMotionManager.State.EXPANDED }
                .distinctUntilChanged()
                .collect { expanded ->
                    setPlayerExpandedMode(expanded)
                }
        }
    }

    fun playMusic(
        id: String,
        queueSource: PlaybackQueueSource = PlaybackQueueSource.MUSIC_LIST
    ) {
        // 요청한 음악으로 실제 재생 전환이 성공했을 때만 이후 처리를 계속한다는 가드 역할
        val changedMusic = playerController.changeMusic(
            id = id,
            queueSource = queueSource
        )

        if (!changedMusic) {
            return
        }

        MusicServiceController.dispatchAction(
            context = this,
            action = MusicService.ACTION_REFRESH_NOTIFICATION,
            mediaId = id
        )
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val settingFragment = supportFragmentManager.findFragmentByTag(TAG_SETTING)

        // 설정 화면 위에서 탭을 누르면 설정을 닫고 선택한 탭으로 돌아간다.
        if (
            settingFragment != null &&
            settingFragment.isAdded &&
            !settingFragment.isHidden
        ) {
            supportFragmentManager.popBackStackImmediate()
        }

        return when (item.itemId) {
            R.id.menu_music_list -> {
                showMainTab(
                    tag = TAG_MUSIC_LIST,
                    createFragment = { MusicTracksFragment.newInstance() }
                )
                vm.setTitle(MainTitle.MusicList)
                true
            }

            R.id.menu_favorites -> {
                showMainTab(
                    tag = TAG_FAVORITES,
                    createFragment = { FavoriteTracksFragment.newInstance() }
                )
                vm.setTitle(MainTitle.Favorites)
                true
            }

            else -> false
        }
    }

    // 복원된 화면이 있으면 UI 상태를 맞추고, 없으면 음악 탭을 초기 화면으로 표시한다.
    private fun initMainTabFragments() {
        val restoredFragment = findCurrentMainTabFragment()

        if (restoredFragment != null) {
            when (restoredFragment.tag) {
                TAG_FAVORITES -> {
                    binding.bottomNavMain.selectedItemId = R.id.menu_favorites
                    vm.setTitle(MainTitle.Favorites)
                }

                else -> {
                    binding.bottomNavMain.selectedItemId = R.id.menu_music_list
                    vm.setTitle(MainTitle.MusicList)
                }
            }
        }

        val musicTracksFragment =
            supportFragmentManager.findFragmentByTag(TAG_MUSIC_LIST)
                ?: MusicTracksFragment.newInstance()

        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .apply {
                if (musicTracksFragment.isAdded) {
                    show(musicTracksFragment)
                } else {
                    add(
                        binding.fcvMain.id,
                        musicTracksFragment,
                        TAG_MUSIC_LIST
                    )
                }

                setMaxLifecycle(
                    musicTracksFragment,
                    Lifecycle.State.RESUMED
                )
                setPrimaryNavigationFragment(musicTracksFragment)
            }
            .commitNow()

        binding.bottomNavMain.selectedItemId = R.id.menu_music_list
    }

    // 현재 탭을 숨기고 요청한 메인 탭을 표시한다.
    private fun showMainTab(
        tag: String,
        createFragment: () -> Fragment
    ) {
        val fragmentManager = supportFragmentManager
        val currentFragment = findCurrentMainTabFragment()
        val targetFragment =
            fragmentManager.findFragmentByTag(tag) ?: createFragment()

        // 현재 표시 중인 탭을 다시 선택한 경우
        if (currentFragment === targetFragment) {
            return
        }

        fragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .apply {
                if (currentFragment != null) {
                    hide(currentFragment)
                    setMaxLifecycle(
                        currentFragment,
                        Lifecycle.State.STARTED
                    )
                }

                if (targetFragment.isAdded) {
                    show(targetFragment)
                } else {
                    add(
                        binding.fcvMain.id,
                        targetFragment,
                        tag
                    )
                }

                setMaxLifecycle(
                    targetFragment,
                    Lifecycle.State.RESUMED
                )
                setPrimaryNavigationFragment(targetFragment)
            }
            .commit()
    }

    private fun findCurrentMainTabFragment(): Fragment? {
        return supportFragmentManager.fragments
            .lastOrNull { fragment ->
                fragment.id == binding.fcvMain.id &&
                        fragment.isAdded &&
                        !fragment.isHidden &&
                        (
                                fragment.tag == TAG_MUSIC_LIST ||
                                        fragment.tag == TAG_FAVORITES
                                )
            }
    }

    // 첫 화면 렌더링 이후 즐겨찾기 탭을 미리 준비한다.
    private fun prepareFavoritesTabAfterFirstDraw() {
        binding.root.doOnPreDraw {
            binding.root.post {
                prepareFavoritesTabFragment()
            }
        }
    }

    // 즐겨찾기 탭 프래그먼트를 숨긴 상태로 미리 추가한다.
    private fun prepareFavoritesTabFragment() {
        if (isFinishing || isDestroyed) {
            return
        }

        // 화면 상태가 이미 저장된 이후에는 Fragment 트랜잭션을 실행하지 않는다.
        if (supportFragmentManager.isStateSaved) {
            return
        }

        // 화면 회전 복원이나 이전 초기화로 이미 존재하면 재생성하지 않는다.
        if (supportFragmentManager.findFragmentByTag(TAG_FAVORITES) != null) {
            return
        }

        val favoriteTracksFragment = FavoriteTracksFragment.newInstance()

        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .add(
                binding.fcvMain.id,
                favoriteTracksFragment,
                TAG_FAVORITES
            )
            .hide(favoriteTracksFragment)
            .setMaxLifecycle(
                favoriteTracksFragment,
                Lifecycle.State.STARTED
            )
            .commitNow()
    }

    private fun startMusicServiceIfNotificationAllowed() {
        if (!hasNotificationPermission()) return

        MusicServiceController.dispatchAction(
            context = this,
            action = MusicService.ACTION_REFRESH_NOTIFICATION
        )
    }

    private fun initStatusBarColor() {
        val systemBarColor = ContextCompat.getColor(this, R.color.main_dark_gradient_start)
        window.statusBarColor = systemBarColor
        window.navigationBarColor = systemBarColor

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
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
            if (!hasAudioPermission()) {
                needs += Manifest.permission.READ_MEDIA_AUDIO
            }
            if (!hasNotificationPermission()) {
                needs += Manifest.permission.POST_NOTIFICATIONS
            }
        } else {
            if (!hasAudioPermission()) {
                needs += Manifest.permission.READ_EXTERNAL_STORAGE
            }
        }
        if (needs.isEmpty()) {
            startPlayerIfAudioPermissionGranted()
        } else {
            permissionLauncher.launch(needs.toTypedArray())
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val audioGranted = hasAudioPermission()
        val notifGranted = hasNotificationPermission()

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
        startPlayerIfAudioPermissionGranted()

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

    private fun startPlayerIfAudioPermissionGranted() {
        if (!hasAudioPermission()) return

        playerController.startPlayer(
            lastPlaybackStore.loadLastPlayedId(PlaybackContentType.MUSIC).orEmpty()
        )
        startMusicServiceIfNotificationAllowed()
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

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
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

    fun setPlayerExpandedMode(isExpanded: Boolean) {
        // 1. Edge-to-Edge 설정
        WindowCompat.setDecorFitsSystemWindows(window, !isExpanded)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
        }

        // 2. 상태바 아이콘 색상 설정
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }

        // 3. 배경색 및 Insets 처리
        val defaultSystemBarColor = ContextCompat.getColor(this, R.color.main_dark_gradient_start)
        window.statusBarColor = if (isExpanded) Color.TRANSPARENT else defaultSystemBarColor
        window.navigationBarColor = defaultSystemBarColor
        binding.containerPlayer.setBackgroundColor(
            if (isExpanded) {
                ContextCompat.getColor(this, R.color.dark_black)
            } else {
                Color.TRANSPARENT
            }
        )

        if (isExpanded) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.containerPlayer) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(0, systemBars.top, 0, systemBars.bottom)
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
        val prefs = getSharedPreferences(SettingsFragment.TAG, MODE_PRIVATE)

        if (player.audioSessionId != 0) {
            AudioEffectManager.init(player.audioSessionId)
            AudioEffectManager.setEnabledFromPrefs(prefs)
        } else {
            player.addListener(object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    if (audioSessionId != 0) {
                        AudioEffectManager.init(audioSessionId)
                        AudioEffectManager.setEnabledFromPrefs(prefs)
                        player.removeListener(this)
                    }
                }
            })
        }
    }

    companion object {
        private const val TAG_MUSIC_LIST = "music_list"
        private const val TAG_FAVORITES = "favorites"
        private const val TAG_SETTING = "setting"
    }
}
