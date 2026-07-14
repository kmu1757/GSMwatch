package com.gsminulx.gsmwatch.presentation

import android.content.Context
import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

/**
 * 앱을 열지 않아도 워치 타일에서 "다음 일과까지 남은 시간"을 바로 볼 수 있게 하는 Wear Tile.
 * 데이터 계산은 기존 SchoolTimer.getRemainingTimeMessage() 를 그대로 재사용한다.
 */
class GsmTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val prefs = getSharedPreferences("GsmWatchPrefs", Context.MODE_PRIVATE)
        val timer = SchoolTimer(prefs.getInt("grade", 1), prefs.getInt("classNum", 1), prefs)
        // 타일은 1분 주기로만 갱신되므로 '초'는 정확할 수 없다 → 분 단위까지만 표시
        val message = timer.getRemainingTimeMessage().replace("\n", " ").replace(Regex("\\s*\\d+초"), "")

        val text = LayoutElementBuilders.Text.Builder()
            .setText(message)
            .setMaxLines(3)
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(18f))
                    .setColor(argb(0xFF00E5FF.toInt())) // 시안 강조색 (앱 테마와 통일)
                    .build()
            )
            .build()

        val layout = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text)
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .setFreshnessIntervalMillis(60_000L) // 1분마다 갱신 요청 (분 단위 카운트다운)
            .build()

        return ResolvableFuture.create<TileBuilders.Tile>().apply { set(tile) }
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
        return ResolvableFuture.create<ResourceBuilders.Resources>().apply { set(resources) }
    }

    companion object {
        private const val RESOURCES_VERSION = "1"
    }
}
