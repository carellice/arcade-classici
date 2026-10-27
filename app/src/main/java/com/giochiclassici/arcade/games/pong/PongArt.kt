package com.giochiclassici.arcade.games.pong

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.glowRoundRect
import kotlin.math.sin

fun DrawScope.drawPongPaddle(x: Float, y: Float, width: Float, height: Float, color: Color) {
    val top = Offset(x - width / 2, y - height / 2)
    drawRoundRect(Color.Black.copy(alpha = 0.6f), top + Offset(5f, 9f), Size(width, height), CornerRadius(width / 3))
    glowRoundRect(color, top, Size(width, height), width / 3, glow = width * 0.8f,
        fill = Brush.horizontalGradient(listOf(color, Color.White, color), top.x, top.x + width))
    drawLine(Color.White.copy(alpha = 0.8f), top + Offset(width * 0.25f, 8f),
        top + Offset(width * 0.25f, height - 8f), width * 0.1f)
}

fun DrawScope.drawPongBall(center: Offset, radius: Float, trail: List<PongPoint> = emptyList()) {
    trail.forEachIndexed { i, p ->
        val t = (i + 1f) / (trail.size + 1f)
        drawRoundRect(Neon.Yellow.copy(alpha = t * 0.3f),
            Offset(p.x - radius * t, p.y - radius * t), Size(radius * 2 * t, radius * 2 * t), CornerRadius(4f))
    }
    glowRoundRect(Neon.Yellow, center - Offset(radius, radius), Size(radius * 2, radius * 2), radius * 0.25f,
        glow = radius * 1.4f, fill = Brush.verticalGradient(listOf(Color.White, Neon.Yellow), center.y - radius, center.y + radius))
}

/** Stessa miniatura animata nel menu e nella scelta della modalità. */
fun DrawScope.drawPongPreview(time: Float) {
    val w = size.width
    val h = size.height
    for (i in 0..8) drawLine(Neon.TextDim.copy(alpha = 0.25f),
        Offset(w / 2, h * i / 9), Offset(w / 2, h * (i + 0.5f) / 9), w * 0.018f)
    val ballX = w * (0.1f + (1 - kotlin.math.abs((time * 0.6f % 2) - 1)) * 0.8f)
    val ballY = h * (0.5f + sin(time * 2.2f) * 0.3f)
    drawPongPaddle(w * 0.08f, h * (0.5f + sin(time * 2.2f - 0.3f) * 0.3f), w * 0.06f, h * 0.3f, Neon.Teal)
    drawPongPaddle(w * 0.92f, h * (0.5f + sin(time * 2.2f + 0.3f) * 0.3f), w * 0.06f, h * 0.3f, Neon.Pink)
    drawPongBall(Offset(ballX, ballY), w * 0.045f)
}

fun DrawScope.drawPongArena(game: PongGame) {
    val w = PongGame.WIDTH
    val h = PongGame.HEIGHT
    drawRoundRect(Brush.verticalGradient(listOf(Neon.Panel.copy(alpha = 0.85f), Neon.Bg0)),
        Offset.Zero, Size(w, h), CornerRadius(28f))
    for (x in 1..9) drawLine(Neon.Teal.copy(alpha = 0.035f), Offset(x * 100f, 0f), Offset(x * 100f, h), 1.5f)
    for (y in 1..13) drawLine(Neon.Teal.copy(alpha = 0.035f), Offset(0f, y * 100f), Offset(w, y * 100f), 1.5f)
    drawRoundRect(Neon.Teal.copy(alpha = 0.35f), Offset(2f, 2f), Size(w - 4f, h - 4f), CornerRadius(28f), style = Stroke(3f))
    drawCircle(Neon.TextDim.copy(alpha = 0.12f), 140f, Offset(w / 2, h / 2), style = Stroke(3f))
    for (i in 0..25) drawLine(Neon.TextDim.copy(alpha = 0.25f),
        Offset(w / 2, 40f + i * 52f), Offset(w / 2, 66f + i * 52f), 5f)
    for (y in listOf(PongGame.WALL, h - PongGame.WALL)) {
        drawLine(Neon.Teal.copy(alpha = 0.1f), Offset(25f, y), Offset(w - 25f, y), 18f)
        drawLine(Neon.Teal.copy(alpha = 0.8f), Offset(25f, y), Offset(w - 25f, y), 4f)
    }
    if (game.flash > 0f) {
        val color = if (game.lastScorer == 1) Neon.Teal else Neon.Pink
        drawRect(color.copy(alpha = game.flash * 0.1f), size = Size(w, h))
    }
    drawPongPaddle(PongGame.PADDLE_X, game.leftY, PongGame.PADDLE_WIDTH, PongGame.PADDLE_HEIGHT, Neon.Teal)
    drawPongPaddle(w - PongGame.PADDLE_X, game.rightY, PongGame.PADDLE_WIDTH, PongGame.PADDLE_HEIGHT, Neon.Pink)
    drawPongBall(Offset(game.ballX, game.ballY), PongGame.BALL_RADIUS, game.trail.toList())
}
