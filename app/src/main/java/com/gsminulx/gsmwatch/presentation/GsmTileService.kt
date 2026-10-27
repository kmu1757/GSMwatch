package com.gsminulx.gsmwatch.presentation

import android.content.ComponentName
import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicFloat
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.protolayout.layout.androidImageResource
import androidx.wear.protolayout.layout.imageResource
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.circularProgressIndicator
import androidx.wear.protolayout.material3.graphicDataCard
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.LayoutString
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.layoutString
import androidx.wear.protolayout.types.stringLayoutConstraint
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.gsminulx.gsmwatch.core.DayStatus
import com.gsminulx.gsmwatch.core.RemainingFormat
import com.gsminulx.gsmwatch.core.caption
import com.gsminulx.gsmwatch.core.label
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import com.gsminulx.gsmwatch.core.R as CoreR

/**
 * 앱을 열지 않아도 "다음 일과까지 남은 시간"을 보여주는 Wear Tile (ProtoLayout Material 3 Expressive).
 *
 * - 남은 시간은 플랫폼 시계 기반 **동적 표현식**으로 렌더러가 직접 갱신한다(서비스를 깨우지 않음).
 * - 앞으로의 일과 경계마다 Timeline entry 를 미리 만들어, 교시가 바뀌어도 타일이 자동 전환된다.
 * - 시간표는 캐시에서 읽어 과목명까지 표시한다.
 */
class GsmTileService : Material3TileService(allowDynamicTheme = true, defaultColorScheme = TileBrandColorScheme) {

    override suspend fun MaterialScope.tileResponse(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val service = this@GsmTileService
        val prefs = service.getSharedPreferences("GsmWatchPrefs", Context.MODE_PRIVATE)
        val timer = SchoolTimer(prefs.getInt("grade", 1), prefs.getInt("classNum", 1), prefs)
        timer.reloadFromCache() // 캐시 시간표 반영(이전엔 빈 시간표로 계산돼 과목명이 나오지 않았다)

        val launchApp = clickable(
            ActionBuilders.launchAction(ComponentName(service, MainActivity::class.java)),
            "open_app"
        )

        val timeline = TimelineBuilders.Timeline.Builder()
        for (segment in segments(timer, LocalDateTime.now())) {
            timeline.addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(statusLayout(segment, launchApp)).build())
                    .setValidity(
                        TimelineBuilders.TimeInterval.Builder()
                            .setStartMillis(segment.start.toEpochMillis())
                            .setEndMillis(segment.end.toEpochMillis())
                            .build()
                    )
                    .build()
            )
        }

        return TileBuilders.Tile.Builder()
            .setTileTimeline(timeline.build())
            // 남은 시간/교시 전환은 타임라인과 동적 표현식이 처리 → 데이터(과목·학년반) 반영용으로만 주기 갱신
            .setFreshnessIntervalMillis(FRESHNESS_MILLIS)
            .build()
    }

    /** 타임라인 한 구간: [start, end) 동안 [status] 를 표시. [minutes] 면 "N분", 아니면 "H시간 M분" 형식. */
    private data class Segment(val start: LocalDateTime, val end: LocalDateTime, val status: DayStatus, val minutes: Boolean)

    /** 지금부터 일과 경계를 따라 최대 [MAX_SEGMENTS] 개 구간을 만든다. */
    private fun segments(timer: SchoolTimer, now: LocalDateTime): List<Segment> {
        val out = mutableListOf<Segment>()
        var t = now
        while (out.size < MAX_SEGMENTS) {
            val st = timer.status(t)
            val end = st.target
            if (!end.isAfter(t)) break
            when {
                // 하루 이상 남은 주말 카운트다운은 1시간 단위로 정적 표기를 갱신
                st.remaining >= Duration.ofDays(1) -> out += Segment(t, minOf(end, t.plusHours(1)), st, minutes = false)
                end.minusHours(1).isAfter(t) -> {
                    out += Segment(t, end.minusHours(1), st, minutes = false)
                    out += Segment(end.minusHours(1), end, st, minutes = true)
                }
                else -> out += Segment(t, end, st, minutes = true)
            }
            t = out.last().end
        }
        return out
    }

    private fun MaterialScope.statusLayout(segment: Segment, launchApp: ModifiersBuilders.Clickable): LayoutElementBuilders.LayoutElement {
        val st = segment.status
        return primaryLayout(
            titleSlot = { text(st.label.layoutString, maxLines = 1) },
            mainSlot = {
                graphicDataCard(
                    onClick = launchApp,
                    height = expand(),
                    graphic = { statusGraphic(st, segment.start) },
                    title = { text(remainingText(segment)) },
                    content = { text(st.caption.layoutString) }
                )
            },
            bottomSlot = {
                textEdgeButton(onClick = launchApp) { text("열기".layoutString) }
            }
        )
    }

    /** 진행률 링(동적), 구간 시작을 모르면 아이콘. */
    private fun MaterialScope.statusGraphic(st: DayStatus, at: LocalDateTime): LayoutElementBuilders.LayoutElement {
        val start = st.start
        val total = start?.let { Duration.between(it, st.target).seconds } ?: 0L
        if (start == null || total <= 0L) {
            val res = if (st is DayStatus.WeekendDorm) CoreR.drawable.ic_bedtime else CoreR.drawable.ic_schedule
            return icon(imageResource(androidImage = androidImageResource(res)))
        }
        val staticProgress = (Duration.between(start, at).seconds.toFloat() / total).coerceIn(0f, 1f)
        // 진행률 = 1 - 남은초/전체초 (렌더러가 매초 계산)
        val dynamicProgress: DynamicFloat = remainingDuration(st)
            .toIntSeconds().asFloat().div(total.toFloat()).times(-1f).plus(1f)
        return circularProgressIndicator(staticProgress = staticProgress, dynamicProgress = dynamicProgress)
    }

    private fun remainingDuration(st: DayStatus) =
        DynamicInstant.platformTimeWithSecondsPrecision()
            .durationUntil(DynamicInstant.withSecondsPrecision(st.target.atZone(ZoneId.systemDefault()).toInstant()))

    /** 남은 시간 텍스트: 정적 폴백 + (가능하면) 렌더러가 갱신하는 동적 문자열. */
    private fun remainingText(segment: Segment): LayoutString {
        val st = segment.status
        val atStart = Duration.between(segment.start, st.target)
        val staticText = RemainingFormat.korean(atStart)
        if (st.remaining >= Duration.ofDays(1)) return staticText.layoutString
        val left = remainingDuration(st)
        val dynamic: DynamicString = if (segment.minutes) {
            left.toIntMinutes().plus(1).format().concat(DynamicString.constant("분"))
        } else {
            left.toIntHours().format()
                .concat(DynamicString.constant("시간 "))
                .concat(left.minutesPart.format())
                .concat(DynamicString.constant("분"))
        }
        return LayoutString(staticText, dynamic, stringLayoutConstraint("00시간 00분"))
    }

    private fun LocalDateTime.toEpochMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    companion object {
        private const val MAX_SEGMENTS = 8
        private const val FRESHNESS_MILLIS = 30 * 60 * 1000L

        /** 동적 색상을 쓸 수 없을 때의 GSM 브랜드 팔레트(앱 테마와 동일 계열). */
        private val TileBrandColorScheme = ColorScheme(
            primary = 0xFF4CDADA.argb,
            onPrimary = 0xFF003737.argb,
            primaryContainer = 0xFF004F50.argb,
            onPrimaryContainer = 0xFF9CF1F0.argb,
            secondary = 0xFFB0CCCA.argb,
            secondaryContainer = 0xFF324B4B.argb,
            onSecondaryContainer = 0xFFCCE8E6.argb,
            tertiary = 0xFFADC6EC.argb,
            tertiaryContainer = 0xFF2E4665.argb,
            onTertiaryContainer = 0xFFD5E3FF.argb,
            surfaceContainer = 0xFF1B2322.argb,
            onSurface = 0xFFDDE4E2.argb,
            onSurfaceVariant = 0xFFBEC9C7.argb,
        )

        /** 데이터/설정이 바뀌었을 때 타일 갱신 요청. */
        fun requestUpdate(context: Context) {
            TileService.getUpdater(context).requestUpdate(GsmTileService::class.java)
        }
    }
}
