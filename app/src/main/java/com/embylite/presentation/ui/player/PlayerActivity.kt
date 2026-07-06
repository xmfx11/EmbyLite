package com.embylite.presentation.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.embylite.presentation.theme.EmbyLiteTheme
import com.embylite.utils.AppLogger
import com.embylite.utils.PlayerUtils
import kotlinx.coroutines.delay

class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全屏沉浸式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 默认横屏播放
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val itemId = intent.getStringExtra("item_id") ?: ""
        try {
            setContent {
                EmbyLiteTheme(darkTheme = true) {
                    PlayerScreen(itemId = itemId, onBack = { finish() })
                }
            }
        } catch (e: Exception) {
            AppLogger.e("PlayerActivity setContent crash", e)
            finish()
        }
    }

    // 返回键交由播放器处理（先退出全屏）
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun PlayerScreen(itemId: String, onBack: () -> Unit) {
    val viewModel: PlayerViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val ready = state as? PlayerState.Ready
    val errorMsg = (state as? PlayerState.Error)?.message

    // ExoPlayer 实例（LoadControl 增大缓冲，避免短时长或高码率媒体加载失败）
    val exoPlayer = remember {
        val loadControl = com.google.android.exoplayer2.DefaultLoadControl.Builder()
            .setBufferDurationsMs(1500, 50000, 1000, 2500)  // 最小缓冲1.5s / 最大50s / 播放回填1s / 重缓冲回填2.5s
            .setBackBuffer(30000, true)  // 保留 30s 后向缓冲
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
    }
    // 播放状态
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    // 控制 UI 状态
    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var isLandscape by remember { mutableStateOf(true) }
    var showDiagnostic by remember { mutableStateOf(false) }

    val activity = context as? Activity

    // 缓冲超时检测：缓冲超过 30 秒未就绪则提示
    LaunchedEffect(isBuffering, ready?.url) {
        if (isBuffering && ready != null && playerError == null) {
            delay(30_000)
            if (isBuffering && playerError == null) {
                playerError = "缓冲超时（30秒未开始播放），请检查网络或服务器是否支持该媒体格式"
                AppLogger.w("Player buffering timeout 30s")
            }
        }
    }

    // 监听播放 URL 准备媒体源
    LaunchedEffect(ready?.url) {
        val url = ready?.url
        if (!url.isNullOrEmpty()) {
            playerError = null
            isBuffering = true
            // 关键修复：注入 X-Emby-Token header（某些 Emby 配置不认 api_key 参数只认 header）
            val token = com.embylite.data.local.TokenManager.getCachedToken()
            val headers = mutableMapOf(
                "User-Agent" to "EmbyLite",
                "Accept" to "*/*"
            )
            if (!token.isNullOrEmpty()) {
                headers["X-Emby-Token"] = token
                headers["X-Emby-Authorization"] =
                    "MediaBrowser Client=\"EmbyLite\", Device=\"Android\", DeviceId=\"EmbyLite-Android-001\", Version=\"0.04\""
            }
            val httpFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)  // 关键：302 STRM 跨协议重定向
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(30_000)
                .setUserAgent("EmbyLite")
                .setDefaultRequestProperties(headers)
            val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            // 设置 MIME type hint 让 ExoPlayer 正确识别 container（mkv/ts/avi 等）
            val mime = PlayerUtils.containerToMime(ready?.mediaSource?.Container)
            val mediaItemBuilder = MediaItem.Builder().setUri(url)
            if (mime != null) mediaItemBuilder.setMimeType(mime)
            val mediaItem = mediaItemBuilder.build()
            val mediaSource: MediaSource = mediaSourceFactory.createMediaSource(mediaItem)
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            AppLogger.i("Player prepared media url=${url.take(120)}... mime=$mime container=${ready?.mediaSource?.Container}")
        }
    }

    // Player.Listener 监听状态和错误（关键：原来无监听导致失败无反馈）
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                    playerError = null
                    AppLogger.i("Player STATE_READY duration=$duration")
                } else if (playbackState == Player.STATE_ENDED) {
                    AppLogger.i("Player STATE_ENDED")
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                AppLogger.d("Player isPlaying=$playing")
            }

            override fun onPlayerError(error: PlaybackException) {
                val msg = "${error.errorCodeName}: ${error.message ?: "未知错误"}"
                AppLogger.e("Player onPlayerError", error)
                playerError = msg
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // 轮询当前进度
    LaunchedEffect(ready?.url) {
        while (true) {
            if (exoPlayer.duration > 0) {
                currentPosition = exoPlayer.currentPosition.coerceIn(0, exoPlayer.duration)
                duration = exoPlayer.duration
            }
            delay(500)
        }
    }

    // 控制层自动隐藏（播放中 4 秒无操作自动隐藏）
    LaunchedEffect(controlsVisible, isPlaying, isLocked) {
        if (controlsVisible && isPlaying && !isLocked) {
            delay(4000)
            controlsVisible = false
        }
    }

    // 释放
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            AppLogger.i("Player released")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
                // 单击切换控制层；双击播放/暂停（未锁定时）
                detectTapGestures(
                    onTap = {
                        if (isLocked) {
                            controlsVisible = !controlsVisible
                        } else {
                            controlsVisible = !controlsVisible
                        }
                    },
                    onDoubleTap = {
                        if (!isLocked) {
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        }
                    }
                )
            }
    ) {
        // 视频画面
        AndroidView(
            factory = { ctx ->
                StyledPlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false  // 用自定义控制层
                    resizeMode = com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 缓冲圈
        if (isBuffering && playerError == null && ready != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        // 错误层（ExoPlayer 错误）
        if (playerError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "播放失败",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = playerError ?: "",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            onClick = {
                                playerError = null
                                exoPlayer.prepare()
                                exoPlayer.playWhenReady = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Replay, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("重试", color = Color.White)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White.copy(alpha = 0.15f),
                            onClick = { showDiagnostic = !showDiagnostic }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (showDiagnostic) "隐藏详情" else "查看详情", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                    if (showDiagnostic && ready != null) {
                        Spacer(modifier = Modifier.size(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = PlayerUtils.buildDiagnosticInfo(ready.itemId, ready.mediaSource, ready.url),
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // 加载中（数据未就绪）
        if (state is PlayerState.Loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.size(12.dp))
                    Text("加载中...", color = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        // 数据层错误（非 ExoPlayer 错误）
        if (errorMsg != null && playerError == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("无法播放", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(errorMsg, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(modifier = Modifier.size(16.dp))
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { viewModel.loadAndPrepare(itemId) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("重试", color = Color.White)
                        }
                    }
                }
            }
        }

        // 控制层（仅未锁定时显示完整控制；锁定时只显示锁图标提示）
        AnimatedVisibility(
            visible = controlsVisible && playerError == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PlayerControls(
                title = ready?.title ?: "EmbyLite 播放器",
                isPlaying = isPlaying,
                isLocked = isLocked,
                isLandscape = isLandscape,
                currentPosition = currentPosition,
                duration = duration,
                playbackSpeed = playbackSpeed,
                onBack = onBack,
                onPlayPause = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                onSeekBack = { exoPlayer.seekBack() },
                onSeekForward = { exoPlayer.seekForward() },
                onSeekTo = { pos -> exoPlayer.seekTo(pos) },
                onLockToggle = {
                    isLocked = !isLocked
                    if (isLocked) controlsVisible = true
                },
                onRotateToggle = {
                    isLandscape = !isLandscape
                    activity?.requestedOrientation =
                        if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                },
                onSpeedChange = { speed ->
                    playbackSpeed = speed
                    exoPlayer.setPlaybackSpeed(speed)
                }
            )
        }

        // 锁定状态提示（点击解锁）
        if (isLocked) {
            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            onClick = {
                                isLocked = false
                                controlsVisible = true
                            }
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "解锁",
                                tint = Color.White,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("已锁定，点击解锁", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControls(
    title: String,
    isPlaying: Boolean,
    isLocked: Boolean,
    isLandscape: Boolean,
    currentPosition: Long,
    duration: Long,
    playbackSpeed: Float,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onLockToggle: () -> Unit,
    onRotateToggle: () -> Unit,
    onSpeedChange: (Float) -> Unit
) {
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderPos by remember { mutableLongStateOf(0L) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    val displayPos = if (sliderDragging) sliderPos else currentPosition
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    Box(modifier = Modifier.fillMaxSize()) {
        // 顶部渐变条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRotateToggle) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = "旋转", tint = Color.White)
                }
                IconButton(onClick = onLockToggle) {
                    Icon(
                        if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "锁定",
                        tint = Color.White
                    )
                }
            }
        }

        // 中央播放控制
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSeekBack, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.Replay10, contentDescription = "后退10秒", tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.2f),
                onClick = onPlayPause,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "播放/暂停",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            IconButton(onClick = onSeekForward, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.Forward10, contentDescription = "前进10秒", tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }

        // 底部进度条 + 时间 + 倍速
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = PlayerUtils.msToTimeStr(displayPos),
                    color = Color.White,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = displayPos.toFloat(),
                    onValueChange = {
                        sliderDragging = true
                        sliderPos = it.toLong()
                    },
                    onValueChangeFinished = {
                        sliderDragging = false
                        onSeekTo(sliderPos)
                    },
                    valueRange = 0f..(duration.coerceAtLeast(1L).toFloat()),
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = PlayerUtils.msToTimeStr(duration),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 倍速按钮
                Box {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.15f),
                        onClick = { showSpeedMenu = !showSpeedMenu }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${playbackSpeed}x",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false }
                    ) {
                        speeds.forEach { sp ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("${sp}x" + if (sp == 1.0f) " (正常)" else "") },
                                onClick = {
                                    onSpeedChange(sp)
                                    showSpeedMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
