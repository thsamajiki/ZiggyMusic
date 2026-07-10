package com.hero.ziggymusic.presentation.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.hero.ziggymusic.data.local.entity.MusicTrackEntity
import com.hero.ziggymusic.domain.music.repository.MusicRepository
import com.hero.ziggymusic.presentation.common.SingleEvent
import com.hero.ziggymusic.presentation.main.model.MainTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

sealed class MainNavigationCommand {
    object AppSettings : MainNavigationCommand()
    object AudioSettings : MainNavigationCommand()
    object TermsOfService : MainNavigationCommand()
    object PrivacyPolicy : MainNavigationCommand()
    object LicenseNotices : MainNavigationCommand()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val musicRepository: MusicRepository
): ViewModel() {
    val musicTracks: LiveData<List<MusicTrackEntity>> = musicRepository.observeMusicTracks()

    // MainTitle 상태 관리
    private val _currentTitle = MutableLiveData<MainTitle>(MainTitle.MusicTracks)
    val currentTitle: LiveData<MainTitle> = _currentTitle

    private val _navigationEvent = MutableLiveData<SingleEvent<MainNavigationCommand>>()
    val navigationEvent: LiveData<SingleEvent<MainNavigationCommand>> = _navigationEvent

    fun requestOpenAppSettings() {
        _navigationEvent.value = SingleEvent(MainNavigationCommand.AppSettings)
    }

    fun requestOpenAudioSettings() {
        _navigationEvent.value = SingleEvent(MainNavigationCommand.AudioSettings)
    }

    fun requestTermsOfService() {
        _navigationEvent.value = SingleEvent(MainNavigationCommand.TermsOfService)
    }

    fun requestOpenPrivacyPolicy() {
        _navigationEvent.value = SingleEvent(MainNavigationCommand.PrivacyPolicy)
    }

    fun requestOpenSourceLicenses() {
        _navigationEvent.value = SingleEvent(MainNavigationCommand.LicenseNotices)
    }

    fun setTitle(title: MainTitle) {
        _currentTitle.value = title
    }
}
