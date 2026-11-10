package com.resqnet.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqnet.app.R
import com.resqnet.app.ui.components.ResQLogo
import com.resqnet.app.ui.components.Wordmark
import com.resqnet.app.ui.theme.Line
import com.resqnet.app.ui.theme.Navy
import com.resqnet.app.ui.theme.Red
import com.resqnet.app.ui.theme.Slate
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2800)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFE4ECFA), Color(0xFFFAFBFE), Color(0xFFF1F4FB))
                )
            )
    ) {
        // Illustration (helping hand) in the lower half
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.58f)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFFFAFBFE), Color.Transparent))
                    )
            )
        }

        // Logo, wordmark & tagline
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ResQLogo(size = 150.dp)
            Spacer(Modifier.height(4.dp))
            Wordmark(fontSize = 54.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Be Safe. Get Help. Stay Connected.",
                color = Navy,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Loading card + red wave footer
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                    .padding(top = 20.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(38.dp),
                    color = Red,
                    trackColor = Line,
                    strokeWidth = 4.dp
                )
                Spacer(Modifier.height(10.dp))
                Text("Initializing...", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Securing your connection", color = Slate, fontSize = 13.sp)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .navigationBarsPadding()
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFFF07A66), Color(0xFFD94A4A))),
                        RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp)
                    )
            )
        }
    }
}
