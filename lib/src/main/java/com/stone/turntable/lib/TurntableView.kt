package com.stone.turntable.lib

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.marginTop
import java.lang.IllegalStateException
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * desc:
 * author:  stone
 * time:    2021/8/14 11:42
 */
class TurntableView constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
    View(context, attrs, defStyleAttr) {

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context) : this(context, null)

    private val mPaint: Paint = Paint()
    private val mTextPaint: TextPaint
    private var mRadius: Int = 100
    private var mPart: Int = 1 //等分数
        set(value) {
            field = value
            mPathList.clear()
        }
    private var mMin: Int = 200 // 最小直径
    private var mCenterX: Int = 300 // 圆心点x
    private var mCenterY: Int = 300 // 圆心点y
    private val mOutRectF = RectF() // 整个圆的外矩形
    private var mAngle: Float = 0.toFloat() // 当前旋转角度值
    private var mNormalAngle: Float = 0f
    private var mSpecialAngle: Float = 0f
    private var mPointAngle: Float = 0.toFloat() // 指针指向角度
    private var mAnimEndDuration = 3000L // 单位 ms
    private var mTouchToEndDelay = 2000L // touch 停止的延迟时间
    private var mTouchToEndStartTime = 0L // touch 停止触发的 开始时间
    private val mBackColorList: MutableList<Int> = mutableListOf()
    private var mRunFlag: Boolean = false
    private var mStartRunTime: Long = 0
    private var mPathList: MutableList<Path> = mutableListOf()
    private var mTextPathList: MutableList<Path> = mutableListOf()
    private var mTextSize = 30
    private var mPointerBitmap: Bitmap? = null // 指针图片
    private val mBitmapOutRectF = RectF() // 圆心控制图 的外矩形
    private var mInterpolator: TimeInterpolator = AccelerateDecelerateInterpolator() // 插值器，先加速再减速
    var mTextList: MutableList<String> = mutableListOf()
    var mOnPartClickListener: ((index: Int) -> Unit)? = null
    var mOnRotateEndListener: ((index: Int) -> Unit)? = null

    // 属性动画，计算目标旋转角度，并绘制
    private val mAnim by lazy {
        val anim = ValueAnimator.ofFloat(0f, (mAnimEndDuration + mTouchToEndDelay) / 1000 * 50f)
        anim.duration = mAnimEndDuration + mTouchToEndDelay
        anim.interpolator = mInterpolator
        anim.addUpdateListener {
            if (!mRunFlag) {
                // 手动touch后，延时停止
                if (System.currentTimeMillis() - mTouchToEndStartTime >= mTouchToEndDelay) {
                    it.cancel()
                    return@addUpdateListener
                }
            } else {
                // 非touch，正常停止
                if (it.duration * it.animatedFraction >= mAnimEndDuration) {

                    it.cancel()
                    return@addUpdateListener
                }
            }
            mAngle += it.animatedValue as Float
            Thread.sleep((50 * it.animatedFraction).toLong())
            invalidate()
        }
        anim.doOnEnd {
            mRunFlag = false
            mTouchToEndStartTime = 0L
            mAngle %= 360
            mOnRotateEndListener?.invoke(getPointerPart())
        }
        anim
    }

    init {
        mPaint.isAntiAlias = true // 设置画笔无锯齿
        mPaint.strokeWidth = 6f
        mPaint.strokeCap = Paint.Cap.ROUND
        mPaint.strokeJoin = Paint.Join.ROUND
        mPaint.style = Paint.Style.FILL

        mTextPaint = TextPaint()
        mTextPaint.textAlign = Paint.Align.CENTER
//        mTextPaint.letterSpacing = 5f

        isClickable = true

        if (attrs != null) {
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                mRadius.toFloat(),
                resources.displayMetrics
            )
            val array = context.obtainStyledAttributes(attrs, R.styleable.StoneTurntable)
            mPointerBitmap = array.getDrawable(R.styleable.StoneTurntable_turntable_pointer)?.toBitmap()
            mPointAngle = array.getFloat(R.styleable.StoneTurntable_turntable_pointer_angle, 270f)
            mTextSize = array.getDimensionPixelSize(R.styleable.StoneTurntable_turntable_text_size, 14)
            array.recycle()
        }

        mTextPaint.textSize = mTextSize.toFloat()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val wSpec = MeasureSpec.getMode(widthMeasureSpec)
        val hSpec = MeasureSpec.getMode(heightMeasureSpec)
        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        mMin = if (wSpec == hSpec && wSpec != MeasureSpec.UNSPECIFIED) {
            min(wSize, hSize) - marginStart - marginEnd - marginTop - marginBottom
        } else {
            var w = 0
            var h = 0
            if (wSpec == MeasureSpec.EXACTLY) {
                w = wSize
            }
            if (hSpec == MeasureSpec.EXACTLY) {
                h = hSize
            }
            var result = if (w > 0 && h > 0) min(w, h) else max(w, h)
            val screenW = resources.displayMetrics.widthPixels
            val screenH = resources.displayMetrics.heightPixels
            result = if (result > 0 && screenW > 0) min(result, screenW) else max(result, screenW)
            result = if (result > 0 && screenH > 0) min(result, screenH) else max(result, screenH)
            if (result == 0) {
                result = min(screenW, screenH) - marginStart - marginEnd - marginTop - marginBottom
            }
            result
        }

        setMeasuredDimension(mMin, mMin)
        mRadius = mMin / 2   //使用小的一边作为半径默认值

//        mTextPaint.textScaleX = 1.1f // 文本水平放大
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mTextPaint.letterSpacing = 0.15f
        } // 字母间距比例
        mCenterX = mMin / 2
        mCenterY = mRadius
        mPointerBitmap?.let {
            mBitmapOutRectF.set(
                mCenterX - it.width / 2f,
                mCenterY - it.height / 2f,
                mCenterX + it.width / 2f,
                mCenterY + it.height / 2f
            )
        }
    }

    fun drawingTurntable() {
//        mAngle = 36f // 初始绘制角度
        invalidate()
    }

    // 绘制转盘、扇形格、文本、控制按钮
    override fun onDraw(canvas: Canvas) {
        mPart = mTextList.size
        if (mPart == 0) return

        mNormalAngle = (360 / mPart).toFloat()
        mSpecialAngle = if (360 % mPart == 0) {
            mNormalAngle
        } else {
            mNormalAngle + 360 % mPart
        }.toFloat()

        if (mBackColorList.isEmpty()) {
            for (i in 0 until mPart) {
                mBackColorList.add(generateColor(i))
            }
        }

        if (mPathList.size != mPart) {
            mPathList.forEach { it.reset() }
            for (i in mPathList.size - 1 until mPart) {
                mPathList.add(Path())
            }
        } else {
            mPathList.forEach { it.reset() }
        }

        if (mTextPathList.size != mPart) {
            mTextPathList.forEach { it.reset() }
            for (i in mTextPathList.size - 1 until mPart) {
                mTextPathList.add(Path())
            }
        } else {
            mTextPathList.forEach { it.reset() }
        }

        mStartRunTime = System.currentTimeMillis()

        canvas.drawCircle(mCenterX.toFloat(), mCenterY.toFloat(), mRadius.toFloat(), mPaint)

        // 整个圆的外矩形. 原平稳的矩形旋转后，其所在范围的外矩形大可能会变大，因为屏幕上还是以 ltrb来 计算绘制.
        // 而圆的外矩形是个正方形，旋转后，不影响，后续因 其构造的 扇形
        mOutRectF.set(
            (mCenterX - mRadius).toFloat(),
            0f,
            (mCenterX + mRadius).toFloat(),
            (2 * mRadius).toFloat()
        )

        for (i in 0 until mPart) {
            /* 使用 path等分圆，绘制扇形。 方便后续判断 点击的 part index. */
            mPathList[i].reset()
            mPaint.color = mBackColorList[i]
            mPathList[i].apply {
                if (i != mPart - 1) {
                    addArc(
                        mOutRectF, mNormalAngle * i + mAngle % 360, mNormalAngle
                    )
                } else {
                    addArc(
                        mOutRectF,
                        360 - mSpecialAngle + mAngle % 360,
                        mSpecialAngle
                    )
                }
            }
            mPathList[i].lineTo(mCenterX.toFloat(), mCenterY.toFloat())
            mPathList[i].close()
            canvas.drawPath(mPathList[i], mPaint) // 绘制扇形 path

            // 文本 path，不需要 path.close()， 结合 path.textAlign(center)， 环绕绘制文本
            mTextPathList[i].reset()
            mTextPathList[i].apply {
                if (i != mPart - 1) {
                    addArc(
                        mOutRectF, mNormalAngle * i + mAngle % 360, mNormalAngle
                    )
                } else {
                    addArc(
                        mOutRectF,
                        360 - mSpecialAngle + mAngle % 360,
                        mSpecialAngle
                    )
                }
            }
            val text = mTextList[i]
            var textLength = mTextPaint.measureText(mTextList[i])
            var end = 1
            val offset = mRadius / 5f
            // 弧长公式：圆心角度数 * PI * 半径 / 180
            val arcLength =
                ((if (i != mPart - 1) mNormalAngle else mSpecialAngle) * Math.PI * (mRadius - offset) / 180).toFloat()
            val maxLength = arcLength - offset // 允许的文本最大length
            // 计算, 得出 在 maxLength 附近的 end 值 (对应text的下标位置)
            if (textLength > maxLength) {
                do {
                    textLength = mTextPaint.measureText(text.substring(0, end))
                    end++
                } while (textLength < maxLength && end <= text.length)
            } else {
                end = text.length
            }
            mTextPaint.color = Color.WHITE
            canvas.drawTextOnPath(
                text.substring(0, if (end > text.length) text.length else end),
                mTextPathList[i],
                0f,
                offset,
                mTextPaint
            )
        }

        // 绘制控制按钮
        mPointerBitmap?.run {
            canvas.drawBitmap(this, null, mBitmapOutRectF, null)
        }
    }

    private fun startRotate() {
        if (mRunFlag) return
        mRunFlag = true
        mAnim.start()
    }

    private fun stopRotate() {
        if (mTouchToEndStartTime != 0L) return
        mTouchToEndStartTime = System.currentTimeMillis()
        mRunFlag = false
    }

    private fun generateColor(index: Int): Int {
        return when (index % 4) {
            0 -> resources.getColor(R.color.blue_71f8)
            1 -> resources.getColor(R.color.teal_200)
            2 -> resources.getColor(R.color.blue_3ff)
            else -> resources.getColor(R.color.purple_200)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_UP -> {
                if (mPointerBitmap != null) {
                    if (isCollision(event.x, event.y, mBitmapOutRectF)) {
                        if (!mRunFlag) { //  已停止，要启动
                            startRotate()
                        } else { // 运行中，要停止
                            stopRotate()
                        }
                        return true
                    }
                }

                if (!mRunFlag) {
                    val partIndex = getTouchPointPart(event.x, event.y)
                    if (partIndex != -1) {
                        mOnPartClickListener?.invoke(partIndex)
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 碰撞检测：点和矩形
     */
    private fun isCollision(x1: Float, y1: Float, reftF: RectF): Boolean {
        return x1 >= reftF.left && x1 <= reftF.left + reftF.width() && y1 >= reftF.top && y1 <= reftF.top + reftF.height()
    }

    /**
     * 触摸点，在哪个部分
     */
    private fun getTouchPointPart(x: Float, y: Float): Int {
        mPathList.forEachIndexed { index, path ->
            if (pointIsInPath(x, y, path)) {
                return index
            }
        }
        return -1
    }

    private fun pointIsInPath(x: Float, y: Float, path: Path): Boolean {
        val bounds = RectF()
        path.computeBounds(bounds, true)
        val region = Region()
        region.setPath(
            path,
            Region(
                Rect(
                    bounds.left.toInt(),
                    bounds.top.toInt(),
                    bounds.right.toInt(),
                    bounds.bottom.toInt()
                )
            )
        )
        return region.contains(x.toInt(), y.toInt())
    }

    /**
     * 指针指向哪个部分。
     * 每个部分的角度 是一个 range , [left, right]。
     * 当旋转后，重新计算 left值，超360，需要 % 。
     */
    private fun getPointerPart(): Int {
        mPathList.forEachIndexed { index, _ ->
            if (index != mPart - 1) {
                val left = (mAngle + mNormalAngle * index) % 360
                val right = left + mNormalAngle
                if (mPointAngle in left..right) {
                    return index
                }
            } else {
                val left = (mAngle + (360 - mSpecialAngle)) % 360
                val right = left + mSpecialAngle
                if (mPointAngle in left..right) {
                    return index
                }
            }
        }
        return -1
    }

    fun setTimeInterpolator(interpolator: TimeInterpolator): TurntableView {
        mInterpolator = interpolator
        return this
    }

    fun setColors(list: List<Int>): TurntableView {
        mBackColorList.clear()
        mBackColorList.addAll(list)
        return this
    }

    fun setAnimEndDuration(duration: Long): TurntableView {
        mAnimEndDuration = duration
        return this
    }

    // 手动触发停止  延时多久
    fun setTouchToEndDelay(delay: Long): TurntableView {
        mTouchToEndDelay = delay
        return this
    }

    fun setTextList(list: List<String>): TurntableView {
        if (mBackColorList.isEmpty()) throw IllegalStateException("color list is empty")
        if (list.size % mBackColorList.size == 1) throw IllegalStateException("TextListSize % ColorListSize == 1")
        mTextList.clear()
        mTextList.addAll(list)
        if (list.size > mBackColorList.size) {
            val modulus = mBackColorList.size
            (mBackColorList.size until list.size).forEach {
                mBackColorList.add(mBackColorList[it % modulus])
            }

        }
        return this
    }

    // todo
    fun setDrawableList(list: List<Drawable>): TurntableView {
        return this
    }
}