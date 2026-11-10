package com.resqnet.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqnet.app.R
import com.resqnet.app.ui.theme.Navy
import com.resqnet.app.ui.theme.Red

/** The ResQNet logo (shield + pulse + signal). Same asset is used on every screen. */
@Composable
fun ResQLogo(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_logo),
        contentDescription = "ResQNet logo",
        modifier = modifier.size(size)
    )
}

/** Small red rounded badge with the white shield mark, used in app headers. */
@Composable
fun LogoBadge(size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(Red, RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo_mark),
            contentDescription = "ResQNet",
            modifier = Modifier.size(size * 0.62f)
        )
    }
}

/** "ResQ" in navy + "Net" in red wordmark. */
@Composable
fun Wordmark(fontSize: TextUnit = 40.sp, navy: Color = Navy) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = navy)) { append("ResQ") }
            withStyle(SpanStyle(color = Red)) { append("Net") }
        },
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold
    )
}
