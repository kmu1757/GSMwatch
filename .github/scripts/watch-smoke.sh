#!/usr/bin/env bash
# Wear OS 에뮬레이터에 (R8 로 축소된) 릴리스 APK 를 설치·실행해 NEIS 조회가 실제로 동작하는지 확인한다.
# 디버그 빌드에선 드러나지 않는 R8/난독화 문제(예: Retrofit 리플렉션 실패 → '오프라인' 오표시)를 잡기 위한 스모크 테스트.
set -uo pipefail
# 인자는 경로 또는 글롭 패턴 (예: "apks/.../GSMwatch_*.apk")
APK=$(ls $1 | head -1)
echo "APK: $APK"
PKG=com.gsminulx.gsmwatch

adb wait-for-device
adb install -r "$APK"
adb logcat -c
adb shell am start -W -n "$PKG/.presentation.MainActivity"

# 첫 조회 결과 로그(fetch done)가 나올 때까지 최대 120초 대기
for _ in $(seq 1 60); do
  if adb logcat -d -s GsmRepo:I | grep -q "fetch done"; then break; fi
  sleep 2
done

adb logcat -d > logcat.txt
echo "----- 앱 로그 -----"
grep -E "GsmRepo|SchoolTimer|FATAL EXCEPTION|AndroidRuntime" logcat.txt | head -60

status=0
if grep -qE "ClassCastException|ParameterizedType|NoSuchMethodException|ClassNotFoundException" logcat.txt; then
  echo "::error::R8 축소 빌드에서 리플렉션 오류 발생 (네트워크와 무관하게 조회 실패)"
  status=1
fi
if grep -q "FATAL EXCEPTION" logcat.txt && grep -A3 "FATAL EXCEPTION" logcat.txt | grep -q "$PKG"; then
  echo "::error::앱 크래시"
  status=1
fi
if ! grep -q "fetch done" logcat.txt; then
  echo "::error::조회 결과 로그(fetch done)가 없음"
  status=1
else
  result=$(grep "fetch done" logcat.txt | tail -1)
  echo "결과: $result"
  # 러너에서 NEIS 접속이 막힐 수 있어(해외 IP) 통신 실패 자체는 경고만 한다. 판정 기준은 리플렉션 오류 유무.
  if echo "$result" | grep -q "problem=null"; then
    echo "NEIS 조회 성공"
  else
    echo "::warning::NEIS 조회는 실패했지만 리플렉션 오류는 아님 (러너 네트워크/NEIS 접근 문제일 수 있음)"
  fi
fi
exit $status
