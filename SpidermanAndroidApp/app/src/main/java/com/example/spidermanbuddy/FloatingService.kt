package com.example.spidermanbuddy

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.PointF
import android.os.Build
import android.os.IBinder
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.atan2
import kotlin.math.hypot

class FloatingService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var spideyView: SpideyView
    private lateinit var params: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        spideyView = SpideyView(this)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = resources.displayMetrics.widthPixels / 2 - 150
        params.y = 500
        
        windowManager.addView(spideyView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::spideyView.isInitialized) {
            spideyView.stopPhysics()
            windowManager.removeView(spideyView)
        }
    }

    inner class SpideyView(context: android.content.Context) : View(context), Choreographer.FrameCallback {
        private var spideyBitmap: Bitmap
        private val matrixTransform = Matrix()
        
        // Physics state
        private val anchor = PointF(0f, 0f)
        private val restLength = 400f
        private var velX = 0f
        private var velY = 0f
        
        private val gravity = 1.5f
        private val springK = 0.05f
        private val damping = 0.92f
        
        private var isDragging = false
        private var lastTouchTime: Long = 0
        
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var initialX = 0
        private var initialY = 0

        init {
            val original = BitmapFactory.decodeResource(resources, R.drawable.spiderman_clean)
            val aspect = original.width.toFloat() / original.height.toFloat()
            spideyBitmap = Bitmap.createScaledBitmap(original, 300, (300 / aspect).toInt(), true)
            
            val displayMetrics = resources.displayMetrics
            anchor.x = displayMetrics.widthPixels / 2f
            anchor.y = -100f // Invisible anchor above screen
            
            Choreographer.getInstance().postFrameCallback(this)
        }
        
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(spideyBitmap.width, spideyBitmap.height)
        }
        
        fun stopPhysics() {
            Choreographer.getInstance().removeFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!isDragging) {
                // Calculate center of spidey
                val cx = params.x + spideyBitmap.width / 2f
                val cy = params.y + spideyBitmap.height / 2f
                
                val dx = cx - anchor.x
                val dy = cy - anchor.y
                val currentLength = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                
                if (currentLength > 0) {
                    val force = -springK * (currentLength - restLength)
                    val forceX = force * (dx / currentLength)
                    val forceY = force * (dy / currentLength)
                    
                    velX += forceX
                    velY += forceY + gravity
                }
                
                velX *= damping
                velY *= damping
                
                // Move window
                if (Math.abs(velX) > 0.5f || Math.abs(velY) > 0.5f) {
                    params.x += velX.toInt()
                    params.y += velY.toInt()
                    windowManager.updateViewLayout(this, params)
                }
            }
            
            invalidate() // Request redraw for rotation
            Choreographer.getInstance().postFrameCallback(this)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            
            val cx = params.x + spideyBitmap.width / 2f
            val cy = params.y + spideyBitmap.height / 2f
            val currentAngle = atan2((cx - anchor.x).toDouble(), (cy - anchor.y).toDouble())
            
            matrixTransform.reset()
            matrixTransform.postTranslate(-spideyBitmap.width / 2f, -spideyBitmap.height / 2f)
            matrixTransform.postRotate(Math.toDegrees(currentAngle).toFloat() * -0.2f)
            matrixTransform.postTranslate(spideyBitmap.width / 2f, spideyBitmap.height / 2f)
            
            canvas.drawBitmap(spideyBitmap, matrixTransform, null)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val clickTime = System.currentTimeMillis()
                    if (clickTime - lastTouchTime < 300) {
                        (context as FloatingService).stopSelf()
                        return true
                    }
                    lastTouchTime = clickTime
                    
                    isDragging = true
                    velX = 0f
                    velY = 0f
                    
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(this, params)
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    return true
                }
            }
            return false
        }
    }
}
