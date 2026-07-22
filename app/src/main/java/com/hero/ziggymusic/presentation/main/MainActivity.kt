package com.hero.ziggymusic.presentation.main

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
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.navigation.NavigationBarView
import com.hero.ziggymusic.R
import com.hero.ziggymusic.playback.state.PlayerStateHolder
import com.hero.ziggymusic.databinding.ActivityMainBinding
import com.hero.ziggymusic.presentation.main.setting.AudioSettingsFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import com.hero.ziggymusic.playback.queue.PlaybackQueueSource
import com.hero.ziggymusic.playback.service.MusicService
import com.hero.ziggymusic.playback.service.MusicServiceController
import com.hero.ziggymusic.presentation.main.model.MainTitle
import com.hero.ziggymusic.presentation.main.musictracks.MusicTracksFragment
import com.hero.ziggymusic.presentation.main.favorites.FavoriteMusicTracksFragment
import com.hero.ziggymusic.playback.model.PlaybackContentType
import com.hero.ziggymusic.data.local.preferences.LastPlaybackStore
import com.hero.ziggymusic.presentation.main.player.manager.PlayerController
import com.hero.ziggymusic.presentation.main.player.manager.PlayerMotionManager
import com.hero.ziggymusic.presentation.main.player.viewmodel.PlayerViewModel
import com.hero.ziggymusic.domain.music.model.MusicTracksSortOrder
import com.hero.ziggymusic.presentation.main.favorites.viewmodel.FavoriteMusicTracksViewModel
import com.hero.ziggymusic.presentation.main.musictracks.viewmodel.MusicTracksViewModel
import com.hero.ziggymusic.presentation.main.popup.MusicTracksSortMenuPopup
import com.hero.ziggymusic.presentation.main.setting.AppSettingsFragment
import com.hero.ziggymusic.presentation.main.setting.WebPageFragment
import com.hero.ziggymusic.presentation.main.setting.LicenseNoticesFragment
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), NavigationBarView.OnItemSelectedListener {
    private lateinit var binding: ActivityMainBinding
    private val vm by viewModels<MainViewModel>()
    private val playerVm by viewModels<PlayerViewModel>()
    private val musicTracksVm by viewModels<MusicTracksViewModel>()
    private val favoriteMusicTracksVm by viewModels<FavoriteMusicTracksViewModel>()

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
                    BottomSheetBehavior.STATE_SETTLING
                        -> {
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
                is MusicTracksFragment -> vm.setTitle(MainTitle.MusicTracks)
                is FavoriteMusicTracksFragment -> vm.setTitle(MainTitle.FavoriteTracks)
                is AppSettingsFragment -> vm.setTitle(MainTitle.AppSettings)
                is AudioSettingsFragment -> vm.setTitle(MainTitle.AudioSettings)
                is WebPageFragment -> vm.setTitle(MainTitle.WebPage(currentFragment.titleResId))
                is LicenseNoticesFragment -> vm.setTitle(MainTitle.LicenseNotices)
            }
        }

        binding.ivSortMusicTracks.setOnClickListener {
            when (vm.currentTitle.value) {
                MainTitle.MusicTracks -> {
                    showMusicTracksSortMenu(
                        selectedSortOrder =
                            musicTracksVm.sortOrder.value
                                ?: MusicTracksSortOrder.TITLE_ASCENDING,
                        dateAddedLabelResId = R.string.sort_added_to_music_tracks_date,
                        onSelected = musicTracksVm::setMusicTrackSortOrder,
                    )
                }

                MainTitle.FavoriteTracks -> {
                    showMusicTracksSortMenu(
                        selectedSortOrder =
                            favoriteMusicTracksVm.sortOrder.value
                                ?: MusicTracksSortOrder.DATE_ADDED_DESCENDING,
                        dateAddedLabelResId = R.string.sort_added_to_favorite_music_tracks_date,
                        onSelected = favoriteMusicTracksVm::setSortOrder,
                    )
                }

                else -> Unit
            }
        }

        // 현재 메인 탭을 숨기고 설정 화면을 백스택 위에 표시한다.
        binding.ivAppSettings.setOnClickListener {
            vm.requestOpenAppSettings()
        }

        initMainTabFragments()

        binding.bottomNavMain.setOnItemSelectedListener(this)

        // 현재 탭에서 열린 설정 흐름은 탭 재선택 시 닫는다.
        binding.bottomNavMain.setOnItemReselectedListener { item ->
            if (popSettingsBackStack()) {
                when (item.itemId) {
                    R.id.menu_music_track_list -> vm.setTitle(MainTitle.MusicTracks)
                    R.id.menu_favorite_music_tracks -> vm.setTitle(MainTitle.FavoriteTracks)
                }
            }
        }

        prepareFavoritesTabAfterFirstDraw()
    }

    // 현재 메인 탭을 숨기고 앱 설정을 설정 흐름의 첫 화면으로 표시한다.
    private fun showAppSettingsFragment() {
        // 설정 화면이 이미 표시 중이면 중복 실행하지 않는다.
        val existingAppSettingsFragment =
            supportFragmentManager.findFragmentByTag(TAG_APP_SETTINGS)

        if (
            existingAppSettingsFragment != null &&
            existingAppSettingsFragment.isAdded &&
            !existingAppSettingsFragment.isHidden
        ) {
            return
        }

        val currentMainFragment =
            findCurrentMainTabFragment()
                ?: return

        val appSettingsFragment = existingAppSettingsFragment ?: AppSettingsFragment.newInstance()

        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .hide(currentMainFragment)
            .setMaxLifecycle(
                currentMainFragment,
                Lifecycle.State.STARTED
            )
            .apply {
                if (appSettingsFragment.isAdded) {
                    show(appSettingsFragment)
                } else {
                    add(
                        binding.fcvMain.id,
                        appSettingsFragment,
                        TAG_APP_SETTINGS
                    )
                }
            }
            .setMaxLifecycle(
                appSettingsFragment,
                Lifecycle.State.RESUMED
            )
            .setPrimaryNavigationFragment(appSettingsFragment)
            .addToBackStack(TAG_APP_SETTINGS)
            .commit()
    }

    // 앱 설정 위에 음향 설정을 쌓아 뒤로가기로 앱 설정에 복귀할 수 있게 한다.
    private fun showAudioSettingsFragment() {
        val existingAudioSettingsFragment =
            supportFragmentManager.findFragmentByTag(TAG_AUDIO_SETTINGS)

        if (
            existingAudioSettingsFragment != null &&
            existingAudioSettingsFragment.isAdded &&
            !existingAudioSettingsFragment.isHidden
        ) {
            return
        }

        var appSettingsFragment = supportFragmentManager.findFragmentByTag(TAG_APP_SETTINGS)

        if (appSettingsFragment == null) {
            showAppSettingsFragment()
            supportFragmentManager.executePendingTransactions()

            appSettingsFragment = supportFragmentManager.findFragmentByTag(TAG_APP_SETTINGS) ?: return
        }

        val audioSettingsFragment =
            existingAudioSettingsFragment ?: AudioSettingsFragment.newInstance()

        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .hide(appSettingsFragment)
            .setMaxLifecycle(appSettingsFragment, Lifecycle.State.STARTED)
            .apply {
                if (audioSettingsFragment.isAdded) {
                    show(audioSettingsFragment)
                } else {
                    add(binding.fcvMain.id, audioSettingsFragment, TAG_AUDIO_SETTINGS)
                }
            }
            .setMaxLifecycle(audioSettingsFragment, Lifecycle.State.RESUMED)
            .setPrimaryNavigationFragment(audioSettingsFragment)
            .addToBackStack(TAG_AUDIO_SETTINGS)
            .commit()
    }

    private fun showWebPageFragment(
        tag: String,
        url: String,
        titleResId: Int,
    ) {
        val existingWebPageFragment = supportFragmentManager.findFragmentByTag(tag)

        if (
            existingWebPageFragment != null &&
            existingWebPageFragment.isAdded &&
            !existingWebPageFragment.isHidden
        ) {
            return
        }

        var appSettingsFragment = supportFragmentManager.findFragmentByTag(TAG_APP_SETTINGS)

        if (appSettingsFragment == null) {
            showAppSettingsFragment()
            supportFragmentManager.executePendingTransactions()

            appSettingsFragment = supportFragmentManager.findFragmentByTag(TAG_APP_SETTINGS) ?: return
        }

        val webPageFragment =
            existingWebPageFragment ?: WebPageFragment.newInstance(
                url = url,
                titleResId = titleResId
            )

        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .hide(appSettingsFragment)
            .setMaxLifecycle(appSettingsFragment, Lifecycle.State.STARTED)
            .apply {
                if (webPageFragment.isAdded) {
                    show(webPageFragment)
                } else {
                    add(binding.fcvMain.id, webPageFragment, tag)
                }
            }
            .setMaxLifecycle(webPageFragment, Lifecycle.State.RESUMED)
            .setPrimaryNavigationFragment(webPageFragment)
            .addToBackStack(tag)
            .commit()
    }

    private fun showLicenseNoticesFragment() {
        val existingOpenSourceLicensesFragment =
            supportFragmentManager.findFragmentByTag(TAG_LICENSE_NOTICES)

        if (
            existingOpenSourceLicensesFragment != null &&
            existingOpenSourceLicensesFragment.isAdded &&
            !existingOpenSourceLicensesFragment.isHidden
        ) {
            return
        }

        var appSettingsFragment = supportFragmentManager.findFragmentByTag(TAG_APP_SETTINGS)

        if (appSettingsFragment == null) {
            showAppSettingsFragment()
            supportFragmentManager.executePendingTransactions()

            appSettingsFragment = supportFragmentManager.findFragmentByTag(TAG_APP_SETTINGS) ?: return
        }

        val licenseNoticesFragment =
            existingOpenSourceLicensesFragment ?: LicenseNoticesFragment.newInstance()

        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .hide(appSettingsFragment)
            .setMaxLifecycle(appSettingsFragment, Lifecycle.State.STARTED)
            .apply {
                if (licenseNoticesFragment.isAdded) {
                    show(licenseNoticesFragment)
                } else {
                    add(binding.fcvMain.id, licenseNoticesFragment, TAG_LICENSE_NOTICES)
                }
            }
            .setMaxLifecycle(licenseNoticesFragment, Lifecycle.State.RESUMED)
            .setPrimaryNavigationFragment(licenseNoticesFragment)
            .addToBackStack(TAG_LICENSE_NOTICES)
            .commit()
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onResume() {
        super.onResume()

        playerVm.refreshFavoriteTrackOrderForCurrentLanguage()
        startPlayerIfAudioPermissionGranted()
    }

    private fun initViewModel() {
        with(vm) {
            lifecycleScope.launch {
                musicTracks.observe(this@MainActivity) { musicTracks ->
                    playerStateHolder.replaceMusicTrackList(musicTracks)
                }

                // Title과 UI 상태를 한번에 관찰
                currentTitle.observe(this@MainActivity) { mainTitle ->
                    binding.tvMainTitle.text = getString(mainTitle.resId)
                    binding.ivBack.isVisible = mainTitle.showBackButton
                    binding.ivSortMusicTracks.isVisible = mainTitle.showMusicTrackSortButton
                    binding.ivSortMusicTracks.isEnabled = mainTitle.showMusicTrackSortButton
                    binding.ivAppSettings.isVisible = mainTitle.showAppSettingsButton
                    binding.ivAppSettings.isEnabled = mainTitle.showAppSettingsButton
                }
            }

            navigationEvent.observe(this@MainActivity) { event ->
                when (event.getContentIfNotHandled()) {
                    is MainNavigationCommand.AppSettings -> showAppSettingsFragment()
                    is MainNavigationCommand.AudioSettings -> showAudioSettingsFragment()
                    is MainNavigationCommand.TermsOfService -> showWebPageFragment(
                        tag = TAG_TERMS_OF_SERVICE,
                        url = TERMS_OF_SERVICE_URL,
                        titleResId = R.string.settings_terms_of_service
                    )
                    is MainNavigationCommand.PrivacyPolicy -> showWebPageFragment(
                        tag = TAG_PRIVACY_POLICY,
                        url = PRIVACY_POLICY_URL,
                        titleResId = R.string.settings_privacy_policy
                    )
                    is MainNavigationCommand.LicenseNotices -> showLicenseNoticesFragment()
                    null -> Unit
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
        queueSource: PlaybackQueueSource = PlaybackQueueSource.MUSIC_TRACKS
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
        // 다른 탭으로 이동하기 전에 설정 흐름을 정리해 이전 설정 화면 재노출을 막는다.
        // 설정 흐름 종료 직후에는 목표 탭을 즉시 표시해 중간 화면 노출을 방지한다.

        return when (item.itemId) {
            R.id.menu_music_track_list -> {
                showMainTab(
                    tag = TAG_MUSIC_LIST,
                    createFragment = { MusicTracksFragment.newInstance() },
                    executeNow = popSettingsBackStack()
                )
                vm.setTitle(MainTitle.MusicTracks)
                true
            }

            R.id.menu_favorite_music_tracks -> {
                showMainTab(
                    tag = TAG_FAVORITES,
                    createFragment = { FavoriteMusicTracksFragment.newInstance() },
                    executeNow = popSettingsBackStack()
                )
                vm.setTitle(MainTitle.FavoriteTracks)
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
                    binding.bottomNavMain.selectedItemId = R.id.menu_favorite_music_tracks
                    vm.setTitle(MainTitle.FavoriteTracks)
                }

                else -> {
                    binding.bottomNavMain.selectedItemId = R.id.menu_music_track_list
                    vm.setTitle(MainTitle.MusicTracks)
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

        binding.bottomNavMain.selectedItemId = R.id.menu_music_track_list
    }

    // 현재 탭을 숨기고 요청한 메인 탭을 표시한다.
    private fun showMainTab(
        tag: String,
        createFragment: () -> Fragment,
        executeNow: Boolean = false
    ) {
        val currentFragment = findCurrentMainTabFragment()
        val targetFragment =
            supportFragmentManager.findFragmentByTag(tag) ?: createFragment()

        // 현재 표시 중인 탭을 다시 선택한 경우
        if (currentFragment === targetFragment) {
            return
        }

        val transaction = supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .apply {
                if (currentFragment != null) {
                    hide(currentFragment)
                    setMaxLifecycle(currentFragment, Lifecycle.State.STARTED)
                }

                if (targetFragment.isAdded) {
                    show(targetFragment)
                } else {
                    add(binding.fcvMain.id, targetFragment, tag)
                }

                setMaxLifecycle(targetFragment, Lifecycle.State.RESUMED)
                setPrimaryNavigationFragment(targetFragment)
            }

        if (executeNow) {
            transaction.commitNow()
        } else {
            transaction.commit()
        }
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

        val favoriteMusicTracksFragment = FavoriteMusicTracksFragment.newInstance()

        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .add(
                binding.fcvMain.id,
                favoriteMusicTracksFragment,
                TAG_FAVORITES
            )
            .hide(favoriteMusicTracksFragment)
            .setMaxLifecycle(
                favoriteMusicTracksFragment,
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

    private fun showMusicTracksSortMenu(
        selectedSortOrder: MusicTracksSortOrder,
        @StringRes dateAddedLabelResId: Int,
        onSelected: (MusicTracksSortOrder) -> Unit,
    ) {
        MusicTracksSortMenuPopup(
            anchorView = binding.ivSortMusicTracks,
            selectedSortOrder = selectedSortOrder,
            dateAddedLabelResId = dateAddedLabelResId,
            onSortOrderSelected = onSelected,
        ).show()
    }

    // 하단 탭 이동 시 설정 계열 백스택을 한 번에 제거한다.
    private fun popSettingsBackStack(): Boolean {
        val hasAppSettingsBackStack = (0 until supportFragmentManager.backStackEntryCount)
            .any { index ->
                supportFragmentManager.getBackStackEntryAt(index).name == TAG_APP_SETTINGS
            }

        if (hasAppSettingsBackStack) {
            // 앱 설정부터 그 위에 쌓인 하위 설정 화면까지 함께 제거한다.
            supportFragmentManager.popBackStackImmediate(
                TAG_APP_SETTINGS,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
            return true
        }

        val hasAudioSettingsBackStack = (0 until supportFragmentManager.backStackEntryCount)
            .any { index ->
                supportFragmentManager.getBackStackEntryAt(index).name == TAG_AUDIO_SETTINGS
            }

        if (hasAudioSettingsBackStack) {
            supportFragmentManager.popBackStackImmediate(
                TAG_AUDIO_SETTINGS,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
            return true
        }

        return false
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
                openSystemSettings()
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

    // 시스템 설정 화면 이동
    private fun openSystemSettings() {
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

    companion object {
        private const val TAG_MUSIC_LIST = "music_list"
        private const val TAG_FAVORITES = "favorites"
        private const val TAG_APP_SETTINGS = "app_settings"
        private const val TAG_AUDIO_SETTINGS = "audio_settings"
        private const val TAG_PRIVACY_POLICY = "privacy_policy"
        private const val TAG_TERMS_OF_SERVICE = "terms_of_service"
        private const val TAG_LICENSE_NOTICES = "license_notices"

        private const val PRIVACY_POLICY_URL =
            "https://thsamajiki.github.io/ziggymusic-privacy-policy.html"

        private const val TERMS_OF_SERVICE_URL =
            "https://thsamajiki.github.io/ziggymusic-terms-of-service.html"
    }
}
