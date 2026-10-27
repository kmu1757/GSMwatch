#!/usr/bin/env bash
# Wear OS 에뮬레이터에 (R8 로 축소된) 릴리스 APK 를 설치·실행해 실제 조회 흐름을 확인한다.
#  1) 디버그 빌드에선 드러나지 않는 R8/난독화 문제(예: Retrofit 리플렉션 실패 → '오프라인' 오표시) → 실패 처리
#  2) 오프라인으로 시작했다가 네트워크가 붙으면 앱이 스스로 다시 조회하는지(자동 재조회) → 실패 처리
#  3) NEIS 실제 조회 성공 여부 → 러너에서 NEIS 접속이 막힐 수 있어 경고만
set -uo pipefail
APK=$(ls $1 | head -1)   # 인자는 경로 또는 글롭 패턴
PKG=com.gsminulx.gsmwatch
echo "APK: $APK"

app_log() { adb logcat -d -s GsmRepo:V SchoolTimer:V; }
fetch_count() { app_log | grep -c "fetch done"; }
wait_fetches() { # $1=기대 개수, $2=최대 초
  for _ in $(seq 1 $(( $2 / 2 ))); do
    [ "$(fetch_count)" -ge "$1" ] && return 0
    sleep 2
  done
  return 1
}

adb wait-for-device
adb install -r "$APK"

# 비행기 모드로 시작 → 첫 조회는 오프라인이어야 한다
adb shell cmd connectivity airplane-mode enable
sleep 3
adb logcat -c
adb shell am start -W -n "$PKG/.presentation.MainActivity"
wait_fetches 1 60 || true
echo "첫 조회: $(app_log | grep 'fetch done' | tail -1)"

# 네트워크 복구 → 앱이 자동으로 다시 조회해야 한다 (에뮬레이터 DNS 는 워크플로우에서 8.8.8.8 로 지정)
adb shell cmd connectivity airplane-mode disable
retried=0
if wait_fetches 2 120; then retried=1; echo "자동 재조회 확인"; fi

adb logcat -d > logcat.txt
echo "----- 앱 로그 -----"
grep -E " (GsmRepo|SchoolTimer) |FATAL EXCEPTION" logcat.txt | grep -v "^\s*at " | head -40

status=0
if grep -E " (GsmRepo|SchoolTimer) " logcat.txt | grep -qE "ClassCastException|ParameterizedType|NoSuchMethodException|ClassNotFoundException"; then
  echo "::error::R8 축소 빌드에서 리플렉션 오류 발생 (네트워크와 무관하게 조회 실패)"
  status=1
fi
if grep -A3 "FATAL EXCEPTION" logcat.txt | grep -q "Process: $PKG"; then
  echo "::error::앱 크래시"
  status=1
fi
if [ "$(grep -c 'fetch done' logcat.txt)" -lt 1 ]; then
  echo "::error::조회 결과 로그(fetch done)가 없음"
  status=1
fi
if [ $retried -eq 0 ]; then
  echo "::error::비행기 모드 해제 후 120초 안에 자동 재조회가 일어나지 않음 (앱 또는 에뮬레이터 네트워크 확인)"
  status=1
fi

last=$(grep "fetch done" logcat.txt | tail -1)
echo "마지막 조회: $last"
if echo "$last" | grep -q "problem=null"; then
  echo "NEIS 조회 성공"
elif [ $status -eq 0 ]; then
  echo "::warning::NEIS 조회는 실패했지만 앱 오류는 아님 (러너에서 NEIS 접속이 막혔을 수 있음)"
fi
exit $status
