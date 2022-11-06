package com.hero.ziggymusic.view.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.navigation.NavigationBarView
import com.hero.ziggymusic.R
import com.hero.ziggymusic.databinding.ActivityMainBinding
import com.hero.ziggymusic.view.main.musiclist.MusicListFragment
import com.hero.ziggymusic.view.main.myplaylist.MyPlaylistFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), View.OnClickListener,
    NavigationBarView.OnItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private val permission = Manifest.permission.READ_EXTERNAL_STORAGE
    private var REQ_READ = 99

    private val viewModel by viewModels<MainViewModel>()

    private lateinit var playerController: PlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

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

    private fun isPermitted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
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
                finish()
            } else {
                setFragmentAdapter()
                playerController.startPlayer()
            }
        }
    }

    override fun onClick(view: View?) {
    }
}