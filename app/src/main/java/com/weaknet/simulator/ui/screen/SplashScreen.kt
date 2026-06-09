package com.weaknet.simulator.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
        targetValue = if (visible) 0f else 30f,
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
                "WeakNet",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 3.sp
            )

            Spacer(Modifier.height(16.dp))

            val sloganAlpha by animateFloatAsState(
                targetValue = if (visible) 0.8f else 0f,
                animationSpec = tween(1000, delayMillis = 400, easing = EaseOutCubic),
                label = "sloganAlpha"
            )

            Text(
                "Simplifica tu configuración de red",
                modifier = Modifier.alpha(sloganAlpha),
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xCCFFFFFF),
                letterSpacing = 1.sp
            )
        }

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
