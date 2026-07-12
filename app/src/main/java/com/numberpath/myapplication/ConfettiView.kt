package com.numberpath.myapplication

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ConfettiView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val particles = mutableListOf<Particle>()
    private val paint = Paint().apply { style = Paint.Style.FILL }
    private var animator: ValueAnimator? = null

    fun burst() {
        particles.clear()
        // Vibrant firecracker colors!
        val colors = listOf(Color.parseColor("#E91E63"), Color.parseColor("#FFB300"), Color.parseColor("#4CAF50"), Color.parseColor("#2196F3"), Color.parseColor("#9C27B0"))

        // Explode from the center of the top screen
        val cx = width / 2f
        val cy = height / 4f

        for (i in 0..150) {
            val angle = Random.nextDouble(0.0, 2 * Math.PI)
            val speed = Random.nextDouble(15.0, 60.0)
            particles.add(
                Particle(
                    x = cx, y = cy,
                    vx = (cos(angle) * speed).toFloat(),
                    vy = (sin(angle) * speed).toFloat(),
                    color = colors.random(),
                    life = 255f
                )
            )
        }

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2500 // 2.5 seconds of falling confetti
            addUpdateListener {
                particles.forEach { p ->
                    p.x += p.vx
                    p.y += p.vy
                    p.vy += 2.0f // Gravity pulls them down
                    p.life -= 3f // Fade out
                }
                particles.removeAll { it.life <= 0 }
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        particles.forEach { p ->
            paint.color = p.color
            paint.alpha = p.life.toInt().coerceIn(0, 255)
            // Draw square confetti pieces
            canvas.drawRect(p.x, p.y, p.x + 16f, p.y + 16f, paint)
        }
    }

    data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Int, var life: Float)
}