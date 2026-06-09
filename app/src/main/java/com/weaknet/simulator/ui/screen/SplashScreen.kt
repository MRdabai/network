package com.weaknet.simulator.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weaknet.simulator.BuildConfig
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        delay(2200)
        onFinished()
    }

    val alphaAnim by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(800, easing = EaseOutCubic),
        label = "alpha"
    )

    val slideAnim by animateFloatAsState(
        targetValue = if (visible) 0f else 40f,
        animationSpec = tween(800, easing = EaseOutCubic),
        label = "slide"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF0D47A1), Color(0xFF1976D2), Color(0xFF42A5F5))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(alphaAnim)
                .offset(y = slideAnim.dp)
        ) {
            Text(
                "W",
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "WeakNet",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 3.sp
            )

            Spacer(Modifier.height(40.dp))
        }

        // 斜着排列的 slogan
        val sloganAlpha by animateFloatAsState(
            targetValue = if (visible) 0.85f else 0f,
            animationSpec = tween(1000, delayMillis = 400, easing = EaseOutCubic),
            label = "sloganAlpha"
        )

        Text(
            "努力让你的手机更慢！",
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 80.dp)
                .rotate(-12f)
                .alpha(sloganAlpha),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            fontStyle = FontStyle.Italic,
            color = Color(0xCCFFFFFF),
            letterSpacing = 4.sp
        )

        // 底部版本号
        Text(
            "v${BuildConfig.VERSION_NAME}",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .alpha(alphaAnim * 0.5f),
            fontSize = 12.sp,
            color = Color.White
        )
    }
}
