package com.hero.ziggymusic.view.main.musiclist

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.databinding.FragmentMusicListBinding
import com.hero.ziggymusic.event.EventBus
import com.hero.ziggymusic.ext.playMusic
import com.hero.ziggymusic.view.main.popup.MusicOptionMenuPopup
import com.hero.ziggymusic.view.main.musiclist.viewmodel.MusicListUiState
import com.hero.ziggymusic.view.main.musiclist.viewmodel.MusicListViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MusicListFragment : Fragment() {
    private var _binding: FragmentMusicListBinding? = null
    private val binding get() = _binding!!

    private val vm by viewModels<MusicListViewModel>()

    private lateinit var musicListAdapter: MusicListAdapter
    private var mediaStoreObserver: ContentObserver? = null
    private var hasRefreshedAfterPermission = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMusicListBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        EventBus.getInstance().register(this)
        initRecyclerView(binding.rvMusicList)
        collectUiState()
    }

    override fun onStart() {
        super.onStart()
        registerMediaStoreObserverIfNeeded()
    }

    override fun onResume() {
        super.onResume()

        if (hasAudioPermission()) {
            registerMediaStoreObserverIfNeeded()

            if (!hasRefreshedAfterPermission) {
                hasRefreshedAfterPermission = true
                vm.refreshMusicList()
            }
        } else {
            hasRefreshedAfterPermission = false
        }
    }

    private fun initRecyclerView(recyclerView: RecyclerView) {
        musicListAdapter = MusicListAdapter(
            onItemClick = { music ->
                playMusic(music.id)
            },
            onOptionClick = { music, view ->
                openMusicOptionMenuPopup(music, view)
            }
        )

        recyclerView.run {
            layoutManager = LinearLayoutManager(context)
            adapter = musicListAdapter
        }
    }

    private fun collectUiState() {
        vm.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is MusicListUiState.Idle -> {
                    binding.rvMusicList.isVisible = false
                    binding.tvNothingFound.isVisible = false
                }

                is MusicListUiState.Content -> {
                    musicListAdapter.submitList(state.data)
                    binding.rvMusicList.isVisible = true
                    binding.tvNothingFound.isVisible = false
                }

                is MusicListUiState.Empty -> {
                    musicListAdapter.submitList(emptyList())
                    binding.tvNothingFound.text = vm.emptyStateMessage.value.orEmpty()
                    binding.rvMusicList.isVisible = false
                    binding.tvNothingFound.isVisible = true
                }

                is MusicListUiState.Error -> {
                    Log.e("MusicListFragment", "음원 목록 불러오기 실패")
                    musicListAdapter.submitList(emptyList())
                    binding.rvMusicList.isVisible = false
                }
            }
        }

        vm.toastEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun registerMediaStoreObserverIfNeeded() {
        if (!hasAudioPermission()) return
        if (mediaStoreObserver != null) return

        mediaStoreObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                vm.refreshMusicList()
            }
        }

        requireContext().contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaStoreObserver!!
        )
    }

    private fun unregisterMediaStoreObserver() {
        mediaStoreObserver?.let { observer ->
            requireContext().contentResolver.unregisterContentObserver(observer)
        }
        mediaStoreObserver = null
    }

    private fun hasAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun playMusic(musicKey: String) {
        requireContext().playMusic(musicKey)
    }

    private fun openMusicOptionMenuPopup(data: MusicModel, anchorView: View) {
        val isFavorite = vm.isContainedInFavorites(data.id)

        MusicOptionMenuPopup(
            anchorView = anchorView,
            showAddToFavorites = !isFavorite,
            showRemoveFromFavorites = isFavorite,
            onAddToFavorites = { addMusicToFavorites(data) },
            onRemoveFromFavorites = { removeMusicFromFavorites(data) }
        ).show()
    }

    private fun addMusicToFavorites(musicModel: MusicModel) {
        // Local DB에 저장한다.
        vm.addMusicToFavorites(musicModel)
    }

    private fun removeMusicFromFavorites(musicModel: MusicModel) {
        // Local DB에서 삭제한다.
        vm.removeMusicFromMyFavorites(musicModel)
    }

    override fun onStop() {
        unregisterMediaStoreObserver()
        super.onStop()
    }

    override fun onDestroyView() {
        _binding = null
        EventBus.getInstance().unregister(this)
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = MusicListFragment()
    }
}
