package com.example.starcatcher

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.max
import kotlin.random.Random

/** Star Catcher: drag the basket, catch stars, dodge bombs. */
class GameView(context: Context) : SurfaceView(context), Runnable {

    private class Item(var x: Float, var y: Float, val speed: Float, val bomb: Boolean)

    private enum class State { READY, PLAYING, OVER }

    private val prefs = context.getSharedPreferences("starcatcher", Context.MODE_PRIVATE)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val items = mutableListOf<Item>()

    @Volatile private var running = false
    private var thread: Thread? = null

    private var state = State.READY
    private var score = 0
    private var best = prefs.getInt("best", 0)
    private var lives = 3
    private var basketX = 0f
    private var spawnTimer = 0f
    private var elapsed = 0f

    private val basketW get() = width * 0.22f
    private val basketH get() = width * 0.06f
    private val basketY get() = height * 0.9f
    private val radius get() = width * 0.04f

    fun resume() {
        running = true
        thread = Thread(this).also { it.start() }
    }

    fun pause() {
        running = false
        thread?.join()
    }

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val holder: SurfaceHolder = holder
            if (!holder.surface.isValid) continue
            val now = System.nanoTime()
            val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
            last = now
            update(dt)
            val canvas = holder.lockCanvas() ?: continue
            try { draw(canvas) } finally { holder.unlockCanvasAndPost(canvas) }
            Thread.sleep(8)
        }
    }

    private fun reset() {
        items.clear()
        score = 0; lives = 3; elapsed = 0f; spawnTimer = 0f
        basketX = width / 2f
        state = State.PLAYING
    }

    private fun update(dt: Float) {
        if (state != State.PLAYING || width == 0) return
        elapsed += dt
        spawnTimer -= dt
        val difficulty = 1f + elapsed / 30f
        if (spawnTimer <= 0f) {
            spawnTimer = max(0.25f, 0.8f / difficulty)
            val bomb = Random.nextFloat() < 0.25f
            items += Item(
                Random.nextFloat() * (width - 2 * radius) + radius, -radius,
                height * (0.25f + Random.nextFloat() * 0.15f) * difficulty, bomb
            )
        }
        val it = items.iterator()
        while (it.hasNext()) {
            val i = it.next()
            i.y += i.speed * dt
            val caught = i.y + radius >= basketY && i.y - radius <= basketY + basketH &&
                i.x >= basketX - basketW / 2 && i.x <= basketX + basketW / 2
            if (caught) {
                if (i.bomb) lives-- else score++
                it.remove()
            } else if (i.y - radius > height) {
                if (!i.bomb) lives--
                it.remove()
            }
        }
        if (lives <= 0) {
            state = State.OVER
            if (score > best) { best = score; prefs.edit().putInt("best", best).apply() }
        }
    }

    override fun draw(c: Canvas) {
        c.drawColor(Color.rgb(15, 20, 50))
        paint.style = Paint.Style.FILL
        for (i in items) {
            paint.color = if (i.bomb) Color.rgb(230, 70, 70) else Color.rgb(255, 215, 0)
            if (i.bomb) c.drawCircle(i.x, i.y, radius, paint) else drawStar(c, i.x, i.y, radius)
        }
        if (state == State.PLAYING) {
            paint.color = Color.rgb(90, 200, 255)
            c.drawRoundRect(
                basketX - basketW / 2, basketY, basketX + basketW / 2, basketY + basketH,
                20f, 20f, paint
            )
        }
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = width * 0.055f
        c.drawText("Score: $score   Best: $best", 30f, paint.textSize + 40f, paint)
        paint.textAlign = Paint.Align.RIGHT
        c.drawText("♥".repeat(max(lives, 0)), width - 30f, paint.textSize + 40f, paint)

        paint.textAlign = Paint.Align.CENTER
        when (state) {
            State.READY -> {
                paint.textSize = width * 0.09f
                c.drawText("STAR CATCHER", width / 2f, height * 0.4f, paint)
                paint.textSize = width * 0.05f
                c.drawText("Catch stars, dodge bombs", width / 2f, height * 0.47f, paint)
                c.drawText("Tap to start", width / 2f, height * 0.53f, paint)
            }
            State.OVER -> {
                paint.textSize = width * 0.09f
                c.drawText("GAME OVER", width / 2f, height * 0.4f, paint)
                paint.textSize = width * 0.05f
                c.drawText("Score: $score", width / 2f, height * 0.47f, paint)
                c.drawText("Tap to play again", width / 2f, height * 0.53f, paint)
            }
            else -> {}
        }
    }

    private fun drawStar(c: Canvas, cx: Float, cy: Float, r: Float) {
        val p = Path()
        for (k in 0 until 10) {
            val rad = if (k % 2 == 0) r else r * 0.45f
            val a = Math.PI / 5 * k - Math.PI / 2
            val x = cx + (rad * Math.cos(a)).toFloat()
            val y = cy + (rad * Math.sin(a)).toFloat()
            if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        c.drawPath(p, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> if (state != State.PLAYING) reset() else basketX = e.x
            MotionEvent.ACTION_MOVE -> if (state == State.PLAYING) basketX = e.x
        }
        return true
    }
}
