# ZiggyMusic

ZiggyMusic는 디바이스에 저장된 음원들을 빠르게 찾아 재생할 수 있는 고급 뮤직 플레이어 앱입니다.  
직관적인 UI, 강력한 플레이리스트, 모션 기반 플레이어 전환과 확장성 높은 구조가 특징입니다.

---

## 주요 기능

- **오프라인 음원 재생**  
  디바이스에 저장된 모든 음악 파일을 탐색하고 바로 재생
- **개별/전체 반복, 셔플 플레이**  
  다양한 재생 모드로 감상 지원
- **나만의 플레이리스트**  
  좋아하는 곡을 원하는 대로 모아 관리
- **플레이어 확장/축소**  
  MotionLayout 기반으로 Youtube Music처럼 부드럽게 플레이어 크기 전환
- **알림 바 컨트롤**  
  음악 재생/정지/트랙 이동 등 빠른 제어
- **이퀄라이저(EQ) 설정**  
  다양한 음색 튜닝 가능
- **블루투스 연결 상태 확인 및 컨트롤**
- **Lottie 기반 오디오 Visualizer UI**
- **Room DB 기반 안정적 데이터 저장/관리**
- **Material Design & Dark 모드 완벽 지원**

---

## 기술 스택 및 라이브러리

- `Kotlin` (JVM 17)
- `Android SDK`: minSdk 26, targetSdk 36
- **아키텍처**: MVVM 패턴, AAC ViewModel, LiveData, Hilt DI, Coroutine
- **UI/UX**: ConstraintLayout, MotionLayout, RecyclerView, DataBinding, ViewBinding, Material Components
- **미디어**: AndroidX media3(ExoPlayer)
- **이미지**: Glide
- **데이터베이스**: Room
- **의존성 주입**: Hilt
- **EventBus**: Otto
- **View 효과**: ElasticViews
- **애니메이션**: Airbnb Lottie

---

## 프로젝트 구조

```plaintext
app/                            # 메인 앱 모듈
 └─ src/main/java/com/hero/ziggymusic/       # View, ViewModel, Repository, Service 등 핵심 로직
 └─ src/main/res/layout/                     # 주요 UI 레이아웃(Music List, Playlist, Player, Setting, Notification 등)
 └─ src/main/res/xml/player_motion_scene.xml # MotionLayout 애니메이션 로직
 └─ src/main/AndroidManifest.xml             # 권한, 서비스, 메인 액티비티, 인텐트 필터 명시
 └─ src/main/res/drawable/                   # 아이콘 및 리소스
```
- 기타: Gradle, Room, Hilt, Media3, Glide 등 최신 라이브러리 사용

---

## 아키텍처 및 설계 특징

- **MVVM 아키텍처**  
  데이터/비즈니스 로직과 UI 명확 분리, LiveData/StateFlow로 화면 업데이트
- **Hilt 기반 DI**  
  객체 결합도 최소화 및 모듈화 용이
- **Room+Repository 패턴**  
  데이터 영속성 및 테스트성 확보
- **MotionLayout & ConstraintLayout**  
  최상위 플레이어 UI/애니메이션 구현

---

## 주요 개발 문제 및 해결 과정

- **MediaPlayer 한계 극복**  
  기존 내장 MediaPlayer는 복잡한 UI/UX(확장/축소, 커스텀 컨트롤)에 한계 →  
  확장성 높은 Media3 + ExoPlayer 채택, 대중적인 오디오 앱 수준 UX 구현

- **플레이어 MotionLayout 구현**  
  Youtube Music과 비슷한 플레이어 축소/확장, 모션/Constraint 세팅에 난항 →  
  수십 번 시도 끝에 정상 동작 및 가독성 있는 XML 코드로 재구성 성공

- **Room DB와 LiveData 연동**  
  실시간 데이터 갱신 및 DB 트랜잭션 충돌 문제 → Repository, Coroutine, LiveData 구조로 분리/해결

- **알림/백그라운드 재생**  
  Foreground Service, Media Button 인텐트 연동, PendingIntent, NotificationChannel 세팅 등 Android 13+ 정책 대응

---


## 사용 예시

최신 음악 리스트/재생 화면/설정/알림 등 예시는 아래 스크린샷 참고

<img src="/images/music_list.png" width="320px" height="675px" title="music_list" alt="music_list"></img>
<img src="/images/my_playlist.png" width="320px" height="675px" title="my_playlist" alt="my_playlist"></img>
<img src="/images/player_fragment.png" width="320px" height="675px" title="player_fragment" alt="player"></img>
<img src="/images/music_setting.png" width="320px" height="675px" title="music_setting" alt="music_setting"></img>
<img src="/images/music_notification.png" width="320px" height="354px" title="music_notification" alt="notification"></img>


---

### 시연 영상

<video controls muted loop>
  <source src="/images/ziggymusic_시연영상.mp4" width="400px" height="844px" type="video/mp4" />
</video>


## 발전 방향 및 느낀 점

- 미디어 앱 특성상 다양한 Android 버전, 권한/서비스 이슈 많았으나 최신 패턴과 라이브러리 활용으로 모두 극복
- MVVM 패턴, Hilt 도입으로 추후 유지보수성과 확장성 크게 향상됨을 경험
- 미디어3의 다양한 기능(동영상 등) 일부만 적용했지만, 나중에 새로운 프로젝트에서 더 깊이 활용할 계획
- EQ/스킨 등 고도화된 설정 기능과 블루투스 제어, 그리고 더 다양한 UI 커스터마이징 기능도 향후 예정

---
