package com.hero.ziggymusic.view.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.viewpager2.widget.ViewPager2
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

@AndroidEntryPoint
class MainActivity : AppCompatActivity(),
    View.OnClickListener,
    NavigationBarView.OnItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private val permissionUnder33 = Manifest.permission.READ_EXTERNAL_STORAGE
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val permissionOver33 = Manifest.permission.READ_MEDIA_AUDIO
    private val requestReadCode = 99

    private var title: String = ""

    private val viewModel by viewModels<MainViewModel>()

    @Inject
    lateinit var player: ExoPlayer
    private val playerModel: PlayerModel = PlayerModel.getInstance()
    private lateinit var playerController: PlayerController

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        initViewModel()
        EventBus.getInstance().register(this)

        playerController = PlayerController(
            this,
            binding.playerContainer,
            supportFragmentManager,
            onStateChanged = { newState ->
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        binding.mainBottomNav.isGone = true
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        binding.mainBottomNav.isVisible = true
                    }
                }
            })

        if (!isPermitted()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(this, arrayOf(permissionOver33), requestReadCode)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(permissionUnder33), requestReadCode)
            }
        } else {
            // 2가지 동작이 권한이 있을 때에만 호출되도록
            setFragmentAdapter()
            playerController.startPlayer()
        }

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

        setupListeners()
    }

    private fun setupListeners() {
        val titleArr = resources.getStringArray(R.array.title_array)

        binding.ivBack.setOnClickListener {
            val transaction = supportFragmentManager.beginTransaction()
            transaction.remove(supportFragmentManager.findFragmentById(R.id.frameLayout)!!).commit()
            supportFragmentManager.popBackStack()

            binding.ivBack.isInvisible = true
            binding.ivSetting.isVisible = true
            binding.ivSetting.isEnabled = true
            binding.mainViewPager.isVisible = true

            binding.tvMainTitle.text = title
        }

        binding.ivSetting.setOnClickListener {
            val intent = SettingFragment.newInstance()
            val transaction = supportFragmentManager.beginTransaction()
            transaction.add(R.id.frameLayout, intent).commit()
            supportFragmentManager.executePendingTransactions()

            binding.ivBack.isVisible = true
            binding.ivSetting.isInvisible = true
            binding.ivSetting.isEnabled = false
            binding.mainViewPager.isInvisible = true

            binding.tvMainTitle.text = titleArr[2]
        }

        binding.mainBottomNav.setOnItemSelectedListener(this)
    }

    override fun onStart() {
        Log.d("onStart", "playerModel: $playerModel playerModel.currentMusic: ${playerModel.currentMusic}")
        musicServiceStart()

        super.onStart()
    }

    private fun setFragmentAdapter() {
        val fragmentAdapter = FragmentAdapter(this)
        binding.mainViewPager.adapter = fragmentAdapter

        val titleArr = resources.getStringArray(R.array.title_array)

        binding.mainViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int,
            ) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels)
            }

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.mainBottomNav.menu.getItem(position).isChecked = true
                binding.tvMainTitle.text = titleArr[position]
                title = titleArr[position]
            }

            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
            }
        })
    }

    private fun initViewModel() {
        with(viewModel) {
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
        when (item.itemId) {
            R.id.menu_music_list -> {
                binding.mainViewPager.currentItem = 0
            }
            R.id.menu_my_play_list -> {
                binding.mainViewPager.currentItem = 1
            }
        }
        return false
    }

    // 미니 플레이어에서 사용하는 버튼에 리스너 세팅
    private fun setPlayerListener() {
        player.addListener(object : Player.Listener {
            // 미디어 아이템이 바뀔 때
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)

                val newMusicKey: String = mediaItem?.mediaId ?: return
                playerModel.changedMusic(newMusicKey)

                Log.d("onMediaItemTransition", "player.isPlaying: ${player.isPlaying}")
            }

            // 재생, 재생 완료, 버퍼링 상태 ...
            override fun onPlaybackStateChanged(state: Int) {
                super.onPlaybackStateChanged(state)
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestReadCode) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "권한 요청을 승인해야만 앱을 실행할 수 있습니다.", Toast.LENGTH_SHORT).show()
                EventBus.getInstance().post(Event("PERMISSION_DENIED"))
            } else {
                setFragmentAdapter()
                playerController.startPlayer()
            }
        }
    }

    private fun isPermitted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permissionUnder33
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        EventBus.getInstance().unregister(this)
        // -> 액티비티 종료/재생성 Notification 클릭 등과 무관하게 백그라운드 재생 유지
        super.onDestroy()
    }

    override fun onClick(view: View?) {
    }
}
