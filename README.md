# ZiggyMusic
뮤직 플레이어 앱


## 개요
디바이스에 저장된 음원들을 재생하여 들을 수 있는 음원 재생 서비스


## 프로젝트 기간
- 초기 : 2022\. 09 ~ 2022\. 11 <br>
- 리뉴얼 : 2023\. 12 ~ 2024\. 01

## 기여도
- 기획, 개발, 디자인 모두 했습니다.


## 사용 프로그램 및 언어
- 사용 프로그램 : Android Studio, GitHub
- 사용 언어 : Kotlin


## 앱의 버전
- minSdkVersion : 26
- targetSdkVersion : 34


## 주요 기능
- 디바이스에 저장된 음원을 재생할 수 있다.
- 사용자가 원하는 음원을 나의 플레이리스트에 추가할 수 있다.
- 나의 플레이리스트에서 원하지 않는 음원을 제외할 수 있다.
- 알림 채널에서 플레이어를 재생하거나 트랙 간에 이동할 수 있다.


## 사용된 기술
- MVVM 디자인 패턴
- AAC (Android Architecture Components)
- LiveData
- Hilt
- Coroutine
- MotionLayout
- ROOM DB
- RecyclerView


## 사용된 라이브러리
- media3 (ExoPlayer)
- Material 디자인
- Glide (사진 첨부)
- EventBus


## 문제 및 해결 과정
- 처음에 MediaPlayer를 사용하여 플레이어를 구현했지만, 기획했던 UI/UX를 구현하는데 어려움이 있어서<br>
현업에서 많이 쓰이는 media3를 사용하는 것으로 좀 더 쉽게 구현했습니다.
- 본 앱의 플레이어가 Youtube Music의 플레이어와 유사하게 동작하도록 구현하는데 어려움이 있었습니다.<br>
이를 MotionLayout 라이브러리를 import하여 MotionScene을 통해 플레이어가 확장/축소되도록 했습니다.<br>
플레이어가 축소되었을 때 표시/미표시되는 View를 Constraint로 설정하는데 시간이 오래 걸렸지만 결과적으로 성공했습니다.


## 개발 후 느낀 점
MVVM 디자인 패턴과 AAC를 적용하여 유지보수가 용이하도록 하고 UI 업데이트가 좀 더 간편해지도록 했습니다.<br>
수많은 기능들이 있는 media3를 사용했지만, 일부 기능만 적용했기에 아쉬움이 남습니다.<br>
추후에 영상 앱을 기획할 때 다시 한 번 공부하고 적용해볼 생각입니다.<br>
EQ 조절이나 스킨 기능 등이 반영된 설정 기능을 추가하는 것도 고려하고 있습니다.<br>



## 스크린샷
<img src="/images/music_list.png" width="320px" height="675px" title="music_list" alt="music_list"></img>
<img src="/images/my_playlist.png" width="320px" height="675px" title="my_playlist" alt="my_playlist"></img>
<img src="/images/player_fragment.png" width="320px" height="675px" title="player_fragment" alt="player"></img>
<img src="/images/music_notification.png" width="320px" height="354px" title="music_notification" alt="notification"></img>

<video controls muted loop>
  <source src="/images/ziggymusic_시연영상.mp4" width="400px" height="844px" type="video/mp4" />
</video>
