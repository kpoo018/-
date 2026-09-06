# 빌드된 APK

`android/build.sh apk` 로 만든 디버그 APK 를 폰에서 바로 받을 수 있도록 여기에 둔다.
빌드 산출물을 저장소에 넣는 것은 보통 피하지만, 개인용 앱이라 받는 경로가 하나 있는 편이 낫다.

코드를 고쳤으면 다시 빌드해서 이 파일을 갱신한다:

```bash
android/build.sh apk && cp android/app/build/kashi-debug.apk dist/
```
