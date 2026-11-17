package com.lqpicker.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.lqpicker.scan.ScanBus
import com.lqpicker.scan.ScreenScanService

@Composable
internal fun ScanCard() {
    val ctx = LocalContext.current

    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data
        if (res.resultCode == Activity.RESULT_OK && data != null) {
            val i = Intent(ctx, ScreenScanService::class.java)
                .setAction(ScreenScanService.ACTION_START)
                .putExtra(ScreenScanService.EXTRA_CODE, res.resultCode)
                .putExtra(ScreenScanService.EXTRA_DATA, data)
            ContextCompat.startForegroundService(ctx, i)
        } else {
            ScanBus.status = "Bạn đã không cấp quyền ghi màn hình."
        }
    }
    val askProjection = {
        val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        askProjection()
    }

    var canDraw by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        canDraw = Settings.canDrawOverlays(ctx)
    }

    fun begin() {
        val needNoti = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needNoti) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else askProjection()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Pal.surface)
            .padding(12.dp)
    ) {
        Text("QUÉT MÀN HÌNH CHỌN TƯỚNG", color = Pal.gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            if (ScanBus.running) {
                "Đang bật. Vào game, chạm bong bóng “LQ”, chọn vị trí rồi bấm Phân tích. Kết quả cũng tự điền vào đây."
            } else {
                "Tuỳ chọn: bong bóng nổi để phân tích nhanh trong 15 giây chọn tướng. Chỉ quét khi bạn bấm, xử lý trên máy, không lưu ảnh, không thao tác vào game."
            },
            color = Pal.text,
            fontSize = 13.sp
        )
        if (ScanBus.status.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(ScanBus.status, color = Pal.sub, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!ScanBus.running) {
                if (!canDraw) {
                    Pill(
                        text = "Cấp quyền bong bóng",
                        selected = false,
                        onClick = {
                            overlayLauncher.launch(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                            )
                        }
                    )
                }
                Pill(text = if (canDraw) "Bật quét + bong bóng" else "Bật (chỉ thông báo)", selected = true, onClick = { begin() })
            } else {
                Pill(
                    text = "Phân tích",
                    selected = true,
                    onClick = {
                        ctx.startService(Intent(ctx, ScreenScanService::class.java).setAction(ScreenScanService.ACTION_SCAN))
                    }
                )
                Pill(
                    text = "Tắt",
                    selected = false,
                    onClick = {
                        ctx.startService(Intent(ctx, ScreenScanService::class.java).setAction(ScreenScanService.ACTION_STOP))
                    }
                )
            }
        }
    }
}
