package com.hero.ziggymusic.view.main

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.navigation.NavigationBarView
import com.hero.ziggymusic.R
import com.hero.ziggymusic.ZiggyMusicApp
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.database.music.entity.PlayerModel
import com.hero.ziggymusic.databinding.ActivityMainBinding
import com.hero.ziggymusic.event.Event
import com.hero.ziggymusic.event.EventBus
import com.hero.ziggymusic.service.MusicService
import com.hero.ziggymusic.view.main.player.PlayerFragment
import com.squareup.otto.Subscribe
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity(),
    View.OnClickListener,
    NavigationBarView.OnItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private val permission = Manifest.permission.READ_EXTERNAL_STORAGE
    private var REQ_READ = 99

    private val viewModel by viewModels<MainViewModel>()

//    private var player: ExoPlayer? = null
    private val player by lazy {
        (applicationContext as ZiggyMusicApp).exoPlayer
    }
    private val playerModel: PlayerModel = PlayerModel.getInstance()
    private lateinit var playerController: PlayerController

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
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQ_READ)
        } else {
            // 2가지 동작이 권한이 있을 때에만 호출되도록
            setFragmentAdapter()
            playerController.startPlayer()
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
//            R.id.menu_setting -> {
//                val settingFragment = SettingFragment()
//                binding.mainViewPager.currentItem = 2
//                supportFragmentManager.beginTransaction().replace(R.id.main_view_pager, settingFragment).commit()
//            }
        }
        return false
    }

    // 미니 플레이어에서 사용하는 버튼에 리스너 세팅
    private fun setPlayerListener() {
//        player = ExoPlayer.Builder(this).build()

        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)

                // 플레이어가 재생 또는 일시정지 될 떄
                if (isPlaying) {
//                    binding.btnMiniPlay.setImageResource(R.drawable.ic_pause_button)
                } else {
//                    binding.btnMiniPlay.setImageResource(R.drawable.ic_play_button)
                }
            }

            // 미디어 아이템이 바뀔 때
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)

                val newMusicKey: String = mediaItem?.mediaId ?: return
                playerModel.changedMusic(newMusicKey)

//                updatePlayerView(playerModel.currentMusic)
            }

            // 재생, 재생 완료, 버퍼링 상태 ...
            override fun onPlaybackStateChanged(state: Int) {
                super.onPlaybackStateChanged(state)

//                updateSeek()
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
                Log.d("MainActivity", "doEvent - SKIP_PREV: $player")
                player?.run {
                    val prevIndex = if (currentMediaItemIndex - 1 in 0 until mediaItemCount) {
                        currentMediaItemIndex - 1
                    } else {
                        0 // 0번에서 뒤로 갈 때
                    }
                    seekTo(prevIndex, 0)
                }
                setPlayerListener()
                musicServiceStart()
            }
            "SKIP_NEXT" -> { // Notification 에서 다음 곡 버튼을 누를 시
                player?.run {
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
            "STOP" -> { // 정지
            }
            "PERMISSION_DENIED" -> { // 권한 거부
                finish()
            }
//            else -> {
//                musicServiceStart()
//            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_READ) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "권한 요청을 승인해야만 앱을 실행할 수 있습니다.", Toast.LENGTH_SHORT).show()
                EventBus.getInstance().post(Event("PERMISSION_DENIED"))
//                finish()
            } else {
                setFragmentAdapter()
                playerController.startPlayer()
            }
        }
    }

    private fun isPermitted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        EventBus.getInstance().unregister(this)
        val serviceIntent = Intent(this, MusicService::class.java)
        stopService(serviceIntent)
        super.onDestroy()
    }

    override fun onClick(view: View?) {
    }
}