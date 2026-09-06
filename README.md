# kashi — 일본어 가사를 원문 + 한글 발음 + 뜻으로

Spotify 로 일본 노래를 들으면서 잠금화면에 **원문 / 한글 발음 / 한국어 뜻** 세 줄을
띄우는 앱과, 그 가사를 만들어 주는 파이프라인.

```
君はまだ知らない          ← 원문
키미와 마다 시라나이       ← 한글 발음
너는 아직 모르지          ← 뜻
```

## 구조

```
Spotify / YouTube Music / 로컬 플레이어
        │  (MediaSession: 곡 정보 + 재생 위치)
        ▼
   Android 앱  ──HTTP──▶  파이프라인 서버 (PC)
   잠금화면 알림                │
   홈/잠금화면 위젯             ├─ LRCLIB      가사 (API 키 불필요)
   앱 내 전체 가사              ├─ fugashi+UniDic  읽기
                              ├─ 한글 변환기      발음 표기
                              └─ Claude/DeepL/Papago  뜻
```

**왜 Spotify SDK 를 안 쓰나.** Spotify 는 2026 년 개발 모드를 크게 조였다. 앱 소유자가
Premium 이어야 하고 허용 사용자는 5 명, 공개 배포에 필요한 extended quota 는 MAU 25 만
이상인 법인만 신청할 수 있다. 안드로이드의 `MediaSessionManager` 를 읽으면 이 벽을
통째로 피하고, 덤으로 YouTube Music 과 로컬 플레이어까지 같은 코드로 덮인다. 대신
**알림 접근 권한**이 필요하다.

**왜 서버를 두나.** 형태소 분석 사전(UniDic)이 크고 번역은 API 키가 필요하다. 무거운
일은 PC 에서 곡당 한 번만 하고, 폰은 결과 JSON 만 받아 캐시한다.

## 파이프라인

### 설치

```bash
python3 -m venv .venv
.venv/bin/pip install -r pipeline/requirements.txt
```

읽기 정확도를 더 올리려면 전체 UniDic 을 쓴다 (`unidic-lite` 보다 크지만 정확하다):

```bash
.venv/bin/pip install unidic && .venv/bin/python -m unidic download
```

### 써 보기

```bash
cd pipeline

# 곡 찾기
../.venv/bin/python -m kashi.cli search "yoasobi 夜に駆ける"

# 3단 가사 만들기 (번역 없이)
../.venv/bin/python -m kashi.cli build --artist YOASOBI --title 夜に駆ける --translator none

# 번역까지 (ANTHROPIC_API_KEY / DEEPL_API_KEY / PAPAGO_* 중 아무거나)
export ANTHROPIC_API_KEY=sk-...
../.venv/bin/python -m kashi.cli build --artist YOASOBI --title 夜に駆ける --duration 261

# 안드로이드 앱이 붙을 서버
../.venv/bin/python -m kashi.cli serve --port 8765
```

`--duration` 을 주면 같은 제목의 다른 버전(라이브·리믹스)을 집을 확률이 크게 준다.

### 한글 표기 두 가지

| | 발음 (기본) | 표기법 |
|---|---|---|
| 東京 | 토오쿄오 | 도쿄 |
| 学校 | 각코오 | 갓코 |
| 先輩 | 셈파이 | 센파이 |
| 先生 | 센세에 | 센세이 |
| つよい | 츠요이 | 쓰요이 |

**발음 모드**는 실제로 들리는 소리에 맞춘다. 장음을 모음 반복으로 살리고, 촉음과
`ん` 을 뒤 자음에 동화시킨다. 따라 부르는 것이 목적이라 기본값이다.

**표기법 모드**는 국립국어원 외래어 표기법을 따른다. 장음을 적지 않고 か/た행이
어두에서 평음이 된다. 제목·고유명사를 통일된 표기로 적을 때 쓴다.

### 当て字 (특수 읽기)

형태소 분석기는 「運命」에 「さだめ」 루비가 붙은 것 같은 가사 특유의 읽기를 잡지
못한다. JSON 으로 직접 지정한다.

```json
{ "運命": "サダメ", "本気": "マジ" }
```

```bash
../.venv/bin/python -m kashi.cli build --artist ... --title ... --overrides overrides.json
```

### 번역기

| 값 | 필요한 환경변수 |
|---|---|
| `none` | 없음. 원문 + 발음만 만든다 |
| `claude` | `ANTHROPIC_API_KEY` |
| `deepl` | `DEEPL_API_KEY` |
| `papago` | `PAPAGO_CLIENT_ID`, `PAPAGO_CLIENT_SECRET` |

`auto` (기본) 는 위 순서로 쓸 수 있는 것을 고르고, 아무것도 없으면 `none` 으로 떨어진다.

가사는 주어가 자주 빠지고 비유가 많아 줄 단위로 던지면 번역이 무너진다. 어느 백엔드든
**곡 전체를 한 번에** 보내고 줄 번호로 돌려받는다. 곡당 한 번이라 비용도 미미하다.

### 테스트

```bash
.venv/bin/python -m pytest pipeline/tests -q
```

## 안드로이드 앱

`android/` 에 Gradle 프로젝트가 있다. Android Studio 로 열어 빌드한다 (minSdk 26).

AndroidX 를 쓰지 않는다. 프레임워크 View 와 coroutines 만으로 충분한 크기라서 그렇게
했고, 덕분에 Google Maven 에 닿을 수 없는 환경에서도 빌드할 수 있다:

```bash
# Gradle 없이. android.jar + kotlinc + d8 + aapt2 만 있으면 된다. 경로는 스크립트 상단 참고.
android/build.sh apk    # app/build/kashi-debug.apk
android/build.sh test   # JVM 단위 테스트
```

1. 서버를 PC 에서 띄운다 (`kashi.cli serve`). 폰과 같은 네트워크여야 한다.
2. 앱 설정에서 서버 주소를 넣는다. 예: `http://192.168.0.10:8765`
3. **알림 접근 권한**을 켠다 (설정 화면의 버튼이 바로 데려간다).
4. Spotify 에서 일본 노래를 재생한다.

### 잠금화면

- **상시 알림** — 모든 안드로이드 버전에서 잠금화면에 3 단 가사가 뜬다. 주력.
- **위젯** — 홈 화면에 놓을 수 있고, 잠금화면 위젯은 Android 16 QPR2 이상에서 지원된다.

### 싱크

`MediaSession` 이 주는 재생 위치는 스냅샷이라 그대로 쓰면 가사가 뚝뚝 끊긴다.
`PlaybackState.lastPositionUpdateTime` 을 기준으로 흐른 시간을 더해 보간한다.

일정 간격으로 폴링하지 않는다. **다음 가사 줄이 시작하는 시각을 계산해 그때 한 번만**
깨어난다. 배터리에 훨씬 낫고 줄이 넘어가는 순간도 더 정확하다. 다만 플레이어가 상태를
자주 알려주지 않으면 추정값이 밀리므로, 아무리 길어도 10 초에 한 번은 깨어나 재생
위치를 다시 읽는다.

기기마다 남는 오차는 설정의 **싱크 보정** 슬라이더로 맞춘다.

## 알아둘 것

- **안드로이드 앱은 컴파일·패키징·단위 테스트까지는 검증했지만 기기에서 돌려 보지는
  못했다.** 에뮬레이터 시스템 이미지를 받을 수 없는 환경이었다. 알림 접근 권한 흐름과
  MediaSession 읽기는 실제 기기에서 확인이 필요하다. 파이프라인은 테스트 51 개,
  앱은 JVM 테스트 8 개가 통과한다.
- **가사 저작권.** LRCLIB 은 크라우드소싱이라 개인 학습용으로 쓰는 것과 배포·상업
  이용은 완전히 다른 이야기다. 배포하려면 일본 곡은 JASRAC/NexTone, 한국 곡은 KOMCA
  관련 라이선스가 필요하고, 실무적으로는 Musixmatch(글로벌) 또는
  Syncpower/PetitLyrics(일본)와 계약하는 것이 정공법이다.
- **서버에 인증이 없다.** 집 안 네트워크 전제다. 공개된 곳에 그대로 노출하지 말 것.
