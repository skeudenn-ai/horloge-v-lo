package com.example.clockedge

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.BatteryManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClockWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ClockEngine()

    inner class ClockEngine : Engine() {

        private var visible = false
        private var width = 0
        private var height = 0
        private var batteryPct = 0

        private val violet = Color.parseColor("#8A8FC0")
        private val pink = Color.parseColor("#B08AAE")
        private val gray = Color.parseColor("#A8A8A8")
        private val pillColor = Color.parseColor("#7C78B0")

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textSize = 400f
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeJoin = Paint.Join.ROUND
        }
        private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            color = pillColor
            textAlign = Paint.Align.CENTER
        }
        private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = pillColor
        }
        private val batteryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B0B0B0")
            textAlign = Paint.Align.LEFT
        }

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    if (level >= 0) batteryPct = level * 100 / scale
                }
                if (visible) draw()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            // Le dernier état de la batterie est renvoyé directement (broadcast "sticky").
            val sticky = registerReceiver(receiver, filter)
            sticky?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level >= 0) batteryPct = level * 100 / scale
            }
        }

        override fun onDestroy() {
            unregisterReceiver(receiver)
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) draw()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w
            height = h
            draw()
        }

        private fun glyphPath(text: String, target: RectF): Path {
            val path = Path()
            glyphPaint.getTextPath(text, 0, text.length, 0f, 0f, path)
            val bounds = RectF()
            path.computeBounds(bounds, true)
            val m = Matrix()
            m.setRectToRect(bounds, target, Matrix.ScaleToFit.FILL)
            path.transform(m)
            return path
        }

        private fun draw() {
            if (width == 0 || height == 0) return
            val holder = surfaceHolder
            val canvas = try { holder.lockCanvas() } catch (e: Exception) { null } ?: return
            try {
                render(canvas)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        private fun render(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            canvas.drawColor(Color.BLACK)

            // Écran d'accueil (téléphone déverrouillé) : fond noir uni, sans horloge.
            // L'horloge n'apparaît que sur l'écran de verrouillage et dans l'aperçu.
            val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (!isPreview && !keyguard.isKeyguardLocked) return

            // Halo bleu discret derrière les heures
            bgPaint.shader = RadialGradient(
                w * 0.5f, h * 0.27f, w * 0.9f,
                Color.parseColor("#0F1A26"), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, bgPaint)
            bgPaint.shader = null

            val now = Date()
            val loc = Locale.FRENCH
            val hours = SimpleDateFormat("HH", loc).format(now)
            val minutes = SimpleDateFormat("mm", loc).format(now)
            val dayNum = SimpleDateFormat("dd", loc).format(now)
            val month = SimpleDateFormat("MM", loc).format(now)
            val dayName = SimpleDateFormat("EEEE", loc).format(now).uppercase(loc)

            val left = w * 0.24f
            val right = w * 0.76f
            val blockH = h * 0.32f
            val hTop = h * 0.15f
            val mTop = hTop + blockH + h * 0.02f

            // Heures (dégradé violet -> rose)
            val hoursRect = RectF(left, hTop, right, hTop + blockH)
            strokePaint.shader = LinearGradient(
                0f, hoursRect.top, 0f, hoursRect.bottom, violet, pink, Shader.TileMode.CLAMP
            )
            drawPairDigits(canvas, hours, hoursRect)

            // Minutes (gris)
            strokePaint.shader = null
            strokePaint.color = gray
            val minRect = RectF(left, mTop, right, mTop + blockH)
            drawPairDigits(canvas, minutes, minRect)

            // Date empilée "13 / 11" à droite des heures
            val dateX = w * 0.87f
            smallPaint.textSize = w * 0.045f
            smallPaint.color = violet
            var y = hTop + w * 0.06f
            val step = w * 0.065f
            canvas.drawText(dayNum, dateX, y, smallPaint)
            canvas.drawText("/", dateX, y + step, smallPaint)
            canvas.drawText(month, dateX, y + 2 * step, smallPaint)

            // Pastille avec le jour, texte tourné à 90°
            val pillW = w * 0.075f
            val pillH = w * 0.30f
            val pillTop = hTop + w * 0.28f
            val pill = RectF(dateX - pillW / 2, pillTop, dateX + pillW / 2, pillTop + pillH)
            canvas.drawRoundRect(pill, pillW / 2, pillW / 2, pillPaint)
            smallPaint.color = pillColor
            smallPaint.textSize = w * 0.04f
            canvas.save()
            canvas.rotate(90f, pill.centerX(), pill.centerY())
            val baseline = pill.centerY() - (smallPaint.ascent() + smallPaint.descent()) / 2
            canvas.drawText(dayName, pill.centerX(), baseline, smallPaint)
            canvas.restore()

            // Batterie en bas
            batteryPaint.textSize = w * 0.05f
            val txt = "$batteryPct%"
            val tw = batteryPaint.measureText(txt)
            val iconW = w * 0.022f
            val iconH = w * 0.04f
            val gap = w * 0.025f
            val total = iconW + gap + tw
            val bx = (w - total) / 2
            val by = h * 0.955f
            val body = RectF(bx, by - iconH, bx + iconW, by)
            canvas.drawRoundRect(body, 3f, 3f, batteryPaint)
            canvas.drawText(txt, bx + iconW + gap, by - 2f, batteryPaint)
        }

        /** Dessine deux chiffres côte à côte, chacun dans sa propre case. */
        private fun drawPairDigits(canvas: Canvas, text: String, rect: RectF) {
            val gap = rect.width() * 0.06f
            val cellW = (rect.width() - gap) / 2f
            for (i in 0 until 2) {
                val l = rect.left + i * (cellW + gap)
                val cell = RectF(l, rect.top, l + cellW, rect.bottom)
                canvas.drawPath(glyphPath(text[i].toString(), cell), strokePaint)
            }
        }
    }
}
