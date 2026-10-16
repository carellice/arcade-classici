package com.giochiclassici.arcade.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Sistema di particelle leggero. Le coordinate sono nell'unità del gioco (celle o unità mondo). */
class ParticleSystem(private val max: Int = 700) {
    private class P(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val color: Color, val size: Float,
        val gravity: Float, val drag: Float,
    )

    private val list = ArrayList<P>(256)

    fun burst(
        x: Float, y: Float, color: Color,
        count: Int = 14, speed: Float = 6f, size: Float = 0.12f, life: Float = 0.6f,
        gravity: Float = 0f, drag: Float = 2.5f,
    ) {
        repeat(count) {
            if (list.size >= max) return
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val s = speed * (0.25f + 0.75f * Random.nextFloat())
            list += P(
                x, y, cos(a) * s, sin(a) * s,
                life * (0.6f + 0.4f * Random.nextFloat()), life, color,
                size * (0.6f + 0.7f * Random.nextFloat()), gravity, drag,
            )
        }
    }

    fun emit(x: Float, y: Float, vx: Float, vy: Float, color: Color, size: Float, life: Float, gravity: Float = 0f) {
        if (list.size >= max) return
        list += P(x, y, vx, vy, life, life, color, size, gravity, 1f)
    }

    fun update(dt: Float) {
        var i = 0
        while (i < list.size) {
            val p = list[i]
            p.life -= dt
            if (p.life <= 0f) {
                list[i] = list[list.size - 1]
                list.removeAt(list.size - 1)
                continue
            }
            val d = 1f / (1f + p.drag * dt)
            p.vx *= d; p.vy = p.vy * d + p.gravity * dt
            p.x += p.vx * dt; p.y += p.vy * dt
            i++
        }
    }

    fun clear() = list.clear()

    fun draw(scope: DrawScope, scale: Float = 1f, origin: Offset = Offset.Zero) {
        for (p in list) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            scope.drawCircle(
                p.color,
                p.size * scale * (0.35f + 0.65f * a),
                Offset(origin.x + p.x * scale, origin.y + p.y * scale),
                alpha = a,
                blendMode = BlendMode.Plus,
            )
        }
    }
}
