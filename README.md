# ZiggyMusic

ZiggyMusic는 디바이스에 저장된 음원들을 빠르게 찾아 재생할 수 있는 고급 뮤직 플레이어 앱입니다.  
직관적인 UI, 강력한 플레이리스트, 모션 기반 플레이어 전환과 확장성 높은 구조가 특징입니다.

---

## 앱 정보

- **앱 이름**: ZiggyMusic
- **앱 유형**: Android 음악 플레이어 앱
- **주요 기능**: 디바이스 내 오프라인 음원 재생, 플레이리스트 관리, 알림 바 컨트롤, 이퀄라이저 설정
- **문의 이메일**: chs8275@gmail.com
- **개인정보처리방침**: [개인정보처리방침 보기](./privacy-policy.md)

---

## 주요 기능

- **오프라인 음원 재생**  
  디바이스에 저장된 모든 음악 파일을 탐색하고 바로 재생
- **개별/전체 반복, 셔플 플레이**  
  다양한 재생 모드로 감상 지원
- **나만의 플레이리스트**  
  좋아하는 곡을 원하는 대로 모아 관리
- **플레이어 확장/축소**  
  MotionLayout 기반으로 부드럽게 플레이어 크기 전환
- **알림 바 컨트롤**  
  음악 재생/정지/트랙 이동 등 빠른 제어
- **이퀄라이저(EQ) 설정**  
  다양한 음색 튜닝 가능
- **블루투스 연결 상태 확인 및 컨트롤**
- **Lottie 기반 오디오 Visualizer UI**
- **Room DB 기반 안정적 데이터 저장/관리**
- **Material Design & Dark 모드 지원**

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
