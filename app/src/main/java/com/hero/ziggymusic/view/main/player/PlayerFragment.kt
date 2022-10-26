package com.hero.ziggymusic.view.main.player

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.hero.ziggymusic.R
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.database.music.entity.PlayerModel
import com.hero.ziggymusic.databinding.FragmentPlayerBinding

import com.hero.ziggymusic.view.main.musiclist.MusicListAdapter
import com.hero.ziggymusic.view.main.player.viewmodel.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

@AndroidEntryPoint
class PlayerFragment : Fragment(), View.OnClickListener {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MusicListAdapter

    private var model: PlayerModel = PlayerModel()
    private val playerViewModel by viewModels<PlayerViewModel>()

    private var player: ExoPlayer? = null
    private lateinit var handler: Handler

    private val musicKey: String
        get() = requireArguments().getString(EXTRA_MUSIC_FILE_KEY).orEmpty()

    private val updateSeekRunnable = Runnable {
        updateSeek()
    }

    private lateinit var playerMotionManager: PlayerMotionManager
    private lateinit var playerBottomSheetManager: PlayerBottomSheetManager

    companion object {
        const val TAG = "NowPlayingFragment"
        const val EXTRA_MUSIC_FILE_KEY: String = "id"
        fun newInstance(musicKey: String): PlayerFragment =
            PlayerFragment().apply {
                arguments = Bundle().apply {
                    putString(EXTRA_MUSIC_FILE_KEY, musicKey)
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding =
            FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initPlayView()
        initViewModel()
//        initPlayListButton()
        initPlayControlButtons()
        initSeekBar()
//        initRecyclerView()

        playerBottomSheetManager = PlayerBottomSheetManager(
            viewLifecycleOwner.lifecycle,
            binding.root,
            object : BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            playerViewModel.changeState(PlayerMotionManager.State.EXPANDED)
                        }
                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            playerViewModel.changeState(PlayerMotionManager.State.COLLAPSED)
                        }
                        BottomSheetBehavior.STATE_HIDDEN -> {
                            playerBottomSheetManager.collapse()
                        }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
//                    binding.constraintLayout.progress = slideOffset
                }
            }
        )

        playerMotionManager = PlayerMotionManager(
            binding.constraintLayout,
            playerBottomSheetManager
        )

        binding.root.setOnClickListener {
            val toggleState = when (playerViewModel.state.value) {
                PlayerMotionManager.State.COLLAPSED -> PlayerMotionManager.State.EXPANDED
                PlayerMotionManager.State.EXPANDED -> PlayerMotionManager.State.COLLAPSED
            }

            playerViewModel.changeState(toggleState)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            playerViewModel.state
                .collect { state ->
                    playerMotionManager.changeState(state)
                }
        }
    }

    private fun initViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            playerViewModel.musicList.observe(viewLifecycleOwner) { musicList ->
                model.replaceMusicList(musicList)
                val nowMusic = musicList.find {
                    it.id == musicKey
                } ?: musicList.getOrNull(0)

                if (musicList.isNotEmpty()) {
                    playMusic(musicList, nowMusic)
                }
            }
        }
    }

    fun changeMusic(musicId: String) {
        val findIndex = playerViewModel.musicList.value.orEmpty()
            .indexOfFirst {
                it.id == musicId
            }

        if (findIndex != -1) {
            player?.seekTo(findIndex, 0)
            player?.play()
        }
    }

    private fun initializeNowPlaying(musicModel: MusicModel) {
        binding.tvNowPlayingTitle.text = musicModel.musicTitle
        binding.tvSongArtist.text = musicModel.musicArtist
        binding.tvTotalTime.text = milliSecondsToTimer(musicModel.duration ?: 0L)
        binding.ivAlbumArt.setImageURI(musicModel.getAlbumUri())
    }


    private fun milliSecondsToTimer(milliSeconds: Long): String {
        var timerString: String = ""
        val secondsString: String

        val minutes: Int = (milliSeconds % (1000 * 60 * 60) / (1000 * 60)).toInt()
        val seconds: Int = ((milliSeconds % (1000 * 60 * 60)) % (1000 * 60) / 1000).toInt()

        if (seconds < 10) {
            secondsString = "0$seconds"
        } else {
            secondsString = "" + seconds
        }

        timerString = "$timerString$minutes:$secondsString"

        return timerString
    }

    private fun initSeekBar() {
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
                player?.seekTo(seekBar.progress * 1000L)
            }
        })

        // clickable 을 false 로 주면 됨.
//        binding.playListSeekBar.setOnTouchListener { v, event ->
//            false // 터치 무시
//        }
    }

    private fun initPlayControlButtons() {
        // 재생 or 일시정지 버튼
        binding.ivPlayPause.setOnClickListener {
            val player = this.player ?: return@setOnClickListener

            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }

        binding.ivNext.setOnClickListener {
            player?.run {
                val nextIndex = if (currentMediaItemIndex + 1 in 0 until mediaItemCount) {
                    currentMediaItemIndex + 1
                } else {
                    0
                }

                seekTo(nextIndex, 0)
            }
        }

        binding.ivPrevious.setOnClickListener {
            player?.run {
                val prevIndex = if (currentMediaItemIndex -1 in 0 until mediaItemCount) {
                    currentMediaItemIndex - 1
                } else {
                    0 // 0번에서 뒤로 갈때
                }

                seekTo(prevIndex, 0)
            }
        }

        binding.ivPlaylist.setOnClickListener {
            val player = this.player ?: return@setOnClickListener

//           player.play()

            // TODO: 뒤로가기 버튼 누르면 접어주기
        }
    }

    private fun initPlayView() {
        player = ExoPlayer.Builder(requireContext()).build()
        binding.vPlayer.player = player

        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                // 플레이어가 재생 또는 일시정지 될 떄

                if (isPlaying) {
                    binding.ivPlayPause.setImageResource(R.drawable.ic_pause_button)
                } else {
                    binding.ivPlayPause.setImageResource(R.drawable.ic_play_button)
                }
            }

            // 미디어 아이템이 바뀔 때
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)

                val newMusicKey: String = mediaItem?.mediaId ?: return
                model.changedMusic(newMusicKey)

                // 리사이클러 뷰 스크롤 이동
//           binding.rvPlayList.scrollToPosition(model.currentPosition)
                updatePlayerView(model.currentMusic)
            }

            // 재생, 재생완료, 버퍼링 상태 ...
            override fun onPlaybackStateChanged(state: Int) {
                super.onPlaybackStateChanged(state)

                updateSeek()
            }
        })

    }

    private fun updateSeek() {
        val player = this.player ?: return
        val duration = if (player.duration >= 0) player.duration else 0 // 전체 음악 길이
        val position = player.currentPosition

        updateSeekUi(duration, position)

        val state = player.playbackState

        val view = binding.root
        view.removeCallbacks(updateSeekRunnable)
        // 재생 중일때 (대 중이 아니거나, 재생이 끝나지 않은 경우)
        if (state != Player.STATE_IDLE && state != Player.STATE_ENDED) {
            view.postDelayed(updateSeekRunnable, 1000) // 1초에 한번씩 실행
        }

    }

    private fun updateSeekUi(duration: Long, position: Long) {
        binding.sbPlayList.max = (duration / 1000).toInt() // 총 길이를 설정. 1000으로 나눠 작게
        binding.sbPlayList.progress = (position / 1000).toInt() // 동일하게 1000으로 나눠 작게

        binding.sbPlayer.max = (duration / 1000).toInt()
        binding.sbPlayer.progress = (position / 1000).toInt()

        binding.tvCurrentPlayTime.text = String.format(
            "%02d:%02d",
            TimeUnit.MINUTES.convert(position, TimeUnit.MILLISECONDS), // 현재 분
            (position / 1000) % 60 // 분 단위를 제외한 현재 초
        )

        binding.tvTotalTime.text = String.format(
            "%02d:%02d",
            TimeUnit.MINUTES.convert(duration, TimeUnit.MILLISECONDS), // 전체 분
            (duration / 1000) % 60 // 분 단위를 제외한 초
        )
    }

    private fun updatePlayerView(musicModel: MusicModel?) {
        musicModel ?: return

        binding.tvSongTitle.text = musicModel.musicTitle
        binding.tvSongArtist.text = musicModel.musicArtist

        Glide.with(binding.ivAlbumArt.context)
            .load(musicModel.getAlbumUri())
            .transform(RoundedCorners(12))
            .into(binding.ivAlbumArt)
    }


    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.iv_playlist -> {
                // TODO: 접어주기
            }
        }
    }

    private fun playMusic(musicList: List<MusicModel>, nowPlayMusic: MusicModel?) {
        if (nowPlayMusic != null) {
            model.updateCurrentMusic(nowPlayMusic)
        }

        val musicMediaItems = musicList.map { music ->
            val defaultDataSourceFactory =
                DefaultDataSource.Factory(requireContext())
            val musicFileUri = music.getMusicFileUri()
            val mediaItem = MediaItem.Builder()
                .setMediaId(music.id)
                .setUri(musicFileUri)
                .build()

            val mediaSource = ProgressiveMediaSource.Factory(defaultDataSourceFactory)
                .createMediaSource(mediaItem)
            mediaSource
        }

        val playIndex = musicList.indexOf(nowPlayMusic)

        player?.run {
            setMediaSources(musicMediaItems)
            prepare()
            seekTo(max(playIndex, 0), 0) // positionsMs=0 초 부터 시작
//            play()
        }
    }

    override fun onStop() {
        super.onStop()

        player?.pause()
        binding.root.removeCallbacks(updateSeekRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()

        _binding = null
        player?.release()
//       binding.root.removeCallbacks(updateSeekRunnable)
    }


    // 하단은 기존 코드

//    private var _binding: ActivityNowPlayingBinding? = null
//    private val binding get() = _binding!!
//
//    private val nowPlayingViewModel by viewModels<NowPlayingViewModel>()
//
//    private lateinit var mediaPlayer: MediaPlayer
//    private lateinit var exoPlayer: ExoPlayer
//    private lateinit var handler: Handler
//
//    companion object {
//        const val EXTRA_MUSIC_FILE_KEY: String = "id"
//
//        fun getIntent(context: Context, musicKey: String): Intent =
//            Intent(context, NowPlayingFragment::class.java)
//                .putExtra(EXTRA_MUSIC_FILE_KEY, musicKey)
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        _binding = DataBindingUtil.setContentView(this, R.layout.activity_now_playing)
//        binding.lifecycleOwner = this
////        val view = binding.root
////        setContentView(view)
//
//
//
//        initView()
//        initViewModel()
//        setOnClickListener()
////        prepareMediaPlayer()
//        initPlayer()
//    }
//
//    private fun initPlayer() {
//        mediaPlayer = MediaPlayer()
//        mediaPlayer.setOnBufferingUpdateListener(MediaPlayer.OnBufferingUpdateListener { mediaPlayer, i ->
//            binding.sbPlayer.secondaryProgress = i
//        })
//
//        mediaPlayer.setOnCompletionListener(MediaPlayer.OnCompletionListener {
//            binding.sbPlayer.progress = 0
//            binding.ibPlayPause.setImageResource(R.drawable.ic_play_button)
//            binding.tvCurrentTime.text = R.string.current_playing_time.toString()
//            mediaPlayer.reset()
////            prepareMediaPlayer()
//        })
//
//        lifecycleScope.launch {
//            ticker(1000L, 1000L)
//                .receiveAsFlow()
//                .collect() {
//                    binding.tvCurrentTime.text =
//                        milliSecondsToTimer(mediaPlayer.currentPosition.toLong())
//                }
//        }
//
////        exoPlayer = ExoPlayer.buil
//    }
//
//    private fun initViewModel() {
//        nowPlayingViewModel.nowPlayingMusic.observe(this) { musicModel ->
//            initializeNowPlaying(musicModel)
//            playMusic(musicModel.getMusicFileUri())
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        mediaPlayer.stop()
//        mediaPlayer.release()
//    }
//
//    private fun initView() {
//        val musicKey = intent.getStringExtra(EXTRA_MUSIC_FILE_KEY).orEmpty()
//
//        nowPlayingViewModel.requestMusic(musicKey)
//
//        binding.sbPlayer.max = 100
//    }
//
//    private fun setOnClickListener() {
//        handler = Handler(Looper.getMainLooper())
//        binding.ivBack.setOnClickListener(this)
//        binding.ibPlayPause.setOnClickListener(View.OnClickListener {
//            if (mediaPlayer.isPlaying) {
//                handler.removeCallbacks(updater)
//                mediaPlayer.pause()
//                binding.ibPlayPause.setImageResource(R.drawable.ic_play_button)
//            } else {
//                mediaPlayer.start()
//                binding.ibPlayPause.setImageResource(R.drawable.ic_pause_button)
//                updateSeekBar()
//            }
//        })
//
//        binding.ibPrevious.setOnClickListener(this)
//        binding.ibNext.setOnClickListener(this)
//        binding.ibRepeat.setOnClickListener(this)
//
//        binding.sbPlayer.setOnClickListener { view ->
//            val seekBar = view as SeekBar
//            val playPosition = mediaPlayer.duration / 100 * seekBar.progress
//            mediaPlayer.seekTo(playPosition)
//            binding.tvCurrentTime.text = milliSecondsToTimer(mediaPlayer.currentPosition.toLong())
//
//        }
//    }
//
//    private fun initializeNowPlaying(musicModel: MusicModel) {
//        binding.tvSongTitle.text = musicModel.musicTitle
//        binding.tvSongArtist.text = musicModel.musicArtist
//        binding.tvTotalTime.text = milliSecondsToTimer(musicModel.duration ?: 0L)
//        binding.rivAlbumArt.setImageURI(musicModel.getAlbumUri())
//    }
//
//    private fun prepareMediaPlayer(musicFileUri: Uri) {
//
//    }
//
//    private fun playMusic(musicFileUri: Uri) {
//        mediaPlayer.setDataSource(this, musicFileUri)
//        mediaPlayer.prepare()
//        mediaPlayer.start()
//    }
//
//    private var updater : Runnable = Runnable {
//        run {
//            updateSeekBar()
//            val currentDuration : Long = mediaPlayer.currentPosition.toLong()
//            binding.tvCurrentTime.text = milliSecondsToTimer(currentDuration)
//        }
//    }
//
//    private fun updateSeekBar() {
//        if (mediaPlayer.isPlaying) {
//            binding.sbPlayer.progress = (((mediaPlayer.currentPosition.div(mediaPlayer.duration)).times(
//                100
//            )).toFloat()).toInt()
//            handler.postDelayed(updater, 1000)
//        }
//    }
//
//    private fun milliSecondsToTimer(milliSeconds : Long) : String {
//        var timerString : String = ""
//        val secondsString : String
//
//        val minutes : Int = (milliSeconds % (1000 * 60 * 60) / (1000 * 60)).toInt()
//        val seconds : Int = ((milliSeconds % (1000 * 60 * 60)) % (1000 * 60) / 1000).toInt()
//
//        if (seconds < 10) {
//            secondsString = "0$seconds"
//        } else {
//            secondsString = "" + seconds
//        }
//
//        timerString = "$timerString$minutes:$secondsString"
//
//        return timerString
//    }
//
//    override fun onClick(view: View?) {
//        when(view?.id) {
//            binding.ivBack.id -> finish()
//        }
//    }
}