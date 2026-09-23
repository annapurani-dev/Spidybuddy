package com.example.spidermanbuddy

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import kotlin.math.atan2
import kotlin.math.hypot

class FloatingService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var spideyView: SpideyView

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        spideyView = SpideyView(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        
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
        private val paint = Paint().apply {
            color = Color.argb(200, 255, 255, 255)
            strokeWidth = 8f
            isAntiAlias = true
        }
        
        private val matrixTransform = Matrix()
        
        // Physics state
        private val anchor = PointF(0f, 0f)
        private val restLength = 400f
        private var posX = 0f
        private var posY = 0f
        private var velX = 0f
        private var velY = 0f
        
        private val gravity = 1.5f
        private val springK = 0.05f
        private val damping = 0.92f
        
        private var isDragging = false
        private var isDraggingAnchor = false
        private var lastTouchTime: Long = 0
        
        private val spideyRadius = 150f

        init {
            // Load and scale bitmap to reasonable size (300x300)
            val original = BitmapFactory.decodeResource(resources, R.drawable.spiderman_clean)
            val aspect = original.width.toFloat() / original.height.toFloat()
            spideyBitmap = Bitmap.createScaledBitmap(original, 300, (300 / aspect).toInt(), true)
            
            // Set initial position
            val displayMetrics = resources.displayMetrics
            anchor.x = displayMetrics.widthPixels / 2f
            anchor.y = 100f
            
            posX = anchor.x
            posY = anchor.y + restLength
            
            // Only consume touches on spiderman or anchor to not block screen
            viewTreeObserver.addOnComputeInternalInsetsListener { info ->
                info.setTouchableInsets(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION)
                val rect = Rect()
                if (isDragging || isDraggingAnchor) {
                    // If dragging, we want to receive all touches to track the drag smoothly
                    info.touchableRegion.set(0, 0, width, height)
                } else {
                    // Only intercept around anchor and spiderman
                    val region = android.graphics.Region()
                    region.op(Rect((anchor.x - 100).toInt(), (anchor.y - 100).toInt(), (anchor.x + 100).toInt(), (anchor.y + 100).toInt()), android.graphics.Region.Op.UNION)
                    region.op(Rect((posX - spideyRadius).toInt(), (posY - spideyRadius).toInt(), (posX + spideyRadius).toInt(), (posY + spideyRadius).toInt()), android.graphics.Region.Op.UNION)
                    info.touchableRegion.set(region)
                }
            }
            
            Choreographer.getInstance().postFrameCallback(this)
        }
        
        fun stopPhysics() {
            Choreographer.getInstance().removeFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!isDragging && !isDraggingAnchor) {
                val dx = posX - anchor.x
                val dy = posY - anchor.y
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
                
                posX += velX
                posY += velY
            }
            
            invalidate() // Request redraw
            Choreographer.getInstance().postFrameCallback(this)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            
            // Draw string
            canvas.drawLine(anchor.x, anchor.y, posX, posY, paint)
            
            // Draw anchor point handle
            canvas.drawCircle(anchor.x, anchor.y, 20f, paint)
            
            // Calculate angle
            val currentAngle = atan2((posX - anchor.x).toDouble(), (posY - anchor.y).toDouble())
            
            // Draw Spidey rotated
            matrixTransform.reset()
            matrixTransform.postTranslate(-spideyBitmap.width / 2f, -spideyBitmap.height / 2f)
            matrixTransform.postRotate(Math.toDegrees(currentAngle).toFloat() * -0.2f)
            matrixTransform.postTranslate(posX, posY)
            
            canvas.drawBitmap(spideyBitmap, matrixTransform, null)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val tx = event.rawX
            val ty = event.rawY
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Check double tap
                    val clickTime = System.currentTimeMillis()
                    if (clickTime - lastTouchTime < 300) {
                        (context as FloatingService).stopSelf()
                        return true
                    }
                    lastTouchTime = clickTime
                    
                    // Check if clicked anchor
                    if (hypot((tx - anchor.x).toDouble(), (ty - anchor.y).toDouble()) < 150f) {
                        isDraggingAnchor = true
                        return true
                    }
                    
                    // Check if clicked spidey
                    if (hypot((tx - posX).toDouble(), (ty - posY).toDouble()) < spideyRadius * 2) {
                        isDragging = true
                        velX = 0f
                        velY = 0f
                        return true
                    }
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDraggingAnchor) {
                        anchor.x = tx
                        anchor.y = ty
                    } else if (isDragging) {
                        posX = tx
                        posY = Math.max(ty, anchor.y + 50f) // Don't push above anchor
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    isDraggingAnchor = false
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }
}
