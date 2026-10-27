# :core 를 쓰는 앱(특히 R8 로 축소하는 워치 릴리스 빌드)에 함께 적용되는 규칙.
#
# Retrofit 은 서비스 인터페이스의 제네릭 시그니처·애노테이션을 리플렉션으로 읽는다. AGP 8+ 기본인
# R8 full mode 는 keep 되지 않은 클래스의 시그니처를 지워, suspend 함수(Continuation<T>)의 반환 타입을
# 못 읽고 ClassCastException 이 난다 → 모든 조회가 실패해 워치에 '오프라인' 이 뜨던 원인.
# Retrofit 2.10+ 의 내장 규칙으로 해결되지만, 서비스 인터페이스를 명시적으로 지켜 둔다.

-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

-keep,allowobfuscation interface com.gsminulx.gsmwatch.core.NeisApi { *; }
-keep,allowobfuscation interface com.gsminulx.gsmwatch.core.BotApi { *; }
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
