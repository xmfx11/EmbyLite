package com.embylite.presentation.ui.player

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.Util
import com.embylite.presentation.theme.EmbyLiteTheme
import com.embylite.data.local.TokenManager
import com.embylite.utils.NetworkModule
import com.embylite.utils.PlayerUtils

class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val itemId = intent.getStringExtra("item_id") ?: ""
        try {
            setContent {
                EmbyLiteTheme {
                    PlayerScreen(itemId = itemId)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerActivity", "setContent crash", e)
            finish()
        }
    }
}

@androidx.compose.runtime.Composable
fun PlayerScreen(itemId: String) {
    val viewModel: PlayerViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(itemId) { viewModel.loadAndPrepare(itemId) }

    val playUrl = (state as? PlayerState.Ready)?.url
    val error = (state as? PlayerState.Error)?.message

    // ExoPlayer 实例
    val context = androidx.compose.ui.platform.LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .build()
    }

    // 准备播放源
    LaunchedEffect(playUrl) {
        if (!playUrl.isNullOrEmpty()) {
            // 用支持跨协议重定向的 DataSource
            val httpFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)  // 关键：302 跟随
                .setUserAgent("EmbyLite")
            val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            val mediaItem = MediaItem.fromUri(playUrl)
            val mediaSource: MediaSource = mediaSourceFactory.createMediaSource(mediaItem)
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    // 释放
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when {
            error != null -> Text(error, color = MaterialTheme.colorScheme.error)
            playUrl == null -> Text("加载中...", color = Color.White)
            else -> AndroidView(
                factory = { ctx ->
                    StyledPlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
