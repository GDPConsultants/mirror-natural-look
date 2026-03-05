package com.mirrornaturallook

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class MirrorOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var silverTintEnabled  = true  set(v) { field = v; invalidate() }
    var edgeGlowEnabled    = true  set(v) { field = v; invalidate() }
    var frostedBorderEnabled = true set(v) { field = v; invalidate() }
    var isDarkTheme        = true  set(v) { field = v; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f || h == 0f) return
        if (silverTintEnabled)   drawSilverTint(canvas, w, h)
        if (edgeGlowEnabled)     drawEdgeGlow(canvas, w, h)
        if (frostedBorderEnabled) drawFrostedBorder(canvas, w, h)
    }

    private fun drawSilverTint(canvas: Canvas, w: Float, h: Float) {
        val a = if (isDarkTheme) 0.07f else 0.10f
        paint.shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(
                Color.argb((a*1.2f*255).toInt(), 200,200,220),
                Color.argb((a*0.8f*255).toInt(), 180,180,200),
                Color.argb((a*1.1f*255).toInt(), 210,210,230)
            ), floatArrayOf(0f,.45f,1f), Shader.TileMode.CLAMP)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        canvas.drawRect(0f,0f,w,h,paint)
        paint.xfermode = null
    }

    private fun drawEdgeGlow(canvas: Canvas, w: Float, h: Float) {
        val a = if (isDarkTheme) 0.18f else 0.22f
        paint.shader = RadialGradient(w/2f, h*0.44f, maxOf(w,h)*0.72f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT,
                Color.argb((a*0.5f*255).toInt(),180,180,220),
                Color.argb((a*255).toInt(),180,180,220)),
            floatArrayOf(0f,.35f,.72f,1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,w,h,paint)
        // top-left corner glint
        paint.shader = RadialGradient(0f,0f,w*0.22f,
            intArrayOf(Color.argb((a*0.7f*255).toInt(),220,220,255),Color.TRANSPARENT),
            floatArrayOf(0f,1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(0f,0f,w*0.22f,paint)
    }

    private fun drawFrostedBorder(canvas: Canvas, w: Float, h: Float) {
        val e = w*0.028f
        val topA  = if (isDarkTheme) 0.22f else 0.50f
        val sideA = if (isDarkTheme) 0.16f else 0.38f
        val botA  = if (isDarkTheme) 0.14f else 0.35f
        fun edge(x0:Float,y0:Float,x1:Float,y1:Float,a:Float,rect:RectF) {
            paint.shader = LinearGradient(x0,y0,x1,y1,
                intArrayOf(Color.argb((a*255).toInt(),255,255,255),Color.TRANSPARENT),
                floatArrayOf(0f,1f), Shader.TileMode.CLAMP)
            canvas.drawRect(rect,paint)
        }
        edge(0f,0f,0f,e*1.5f, topA,  RectF(0f,0f,w,e*1.5f))
        edge(0f,h, 0f,h-e*1.2f, botA,  RectF(0f,h-e*1.2f,w,h))
        edge(0f,0f,e, 0f,    sideA, RectF(0f,0f,e,h))
        edge(w, 0f,w-e,0f,   sideA, RectF(w-e,0f,w,h))
        // top highlight line
        val hlA = if (isDarkTheme) 0.40f else 0.85f
        paint.shader = LinearGradient(w*0.10f,0f,w*0.90f,0f,
            intArrayOf(Color.TRANSPARENT,
                Color.argb((hlA*255).toInt(),255,255,255),
                Color.TRANSPARENT),
            floatArrayOf(0f,.5f,1f), Shader.TileMode.CLAMP)
        canvas.drawRect(w*0.10f,0f,w*0.90f,1.5f,paint)
    }
}
