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
import androidx.core.animation.doOnCancel
import androidx.core.animation.doOnEnd
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.marginTop
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

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
    private var mPart: Int = 0 //等分数
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
    private var mIsSelectPartHighlight: Boolean = true // 选中部分高亮，其它部分 半透明
    private var mIsSelectPartHighlightSwitch: Boolean = true // 开关
    private var mSelectPathIndex: Int = -1
    var mTextList: MutableList<String> = mutableListOf()
    var mOnPartClickListener: ((index: Int) -> Unit)? = null
    var mOnRotateEndListener: ((index: Int) -> Unit)? = null
    var mOnRotateBeginListener: (() -> Unit)? = null

    // 属性动画，计算目标旋转角度，并绘制
    private val mAnim by lazy {
        val anim = ValueAnimator.ofFloat(0f, (mAnimEndDuration + mTouchToEndDelay) / 1000 * 50f)
        anim.duration = mAnimEndDuration + mTouchToEndDelay
        anim.interpolator = mInterpolator
        anim.addUpdateListener {
            if (!mRunFlag) {
                // 手动touch 停止， 当前时间与touch时间 超出后，停止
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
            val partEndResult = getPointerPart()
            if (partEndResult != -1) {
                mOnRotateEndListener?.invoke(partEndResult)
            }

            if (!mIsSelectPartHighlightSwitch) return@doOnEnd
            // 若选中区需要高亮
            if (partEndResult != -1 && mBackColorList.size > 1) {
                mIsSelectPartHighlight = true
                mSelectPathIndex = partEndResult
                invalidate()
            }
        }
        // anim.cancel() 后，会执行 onCancel，接着还是会执行 onEnd
        anim.doOnCancel {
        }
        anim
    }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
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
            mPointerBitmap =
                array.getDrawable(R.styleable.StoneTurntable_turntable_pointer)?.toBitmap()
            mPointAngle = array.getFloat(R.styleable.StoneTurntable_turntable_pointer_angle, 270f)
            mTextSize =
                array.getDimensionPixelSize(R.styleable.StoneTurntable_turntable_text_size, 14)
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
        if (mPart == 0) return

        mNormalAngle = (360 / mPart).toFloat()
        mSpecialAngle = if (360 % mPart == 0) {
            mNormalAngle
        } else {
            mNormalAngle + 360 % mPart
        }.toFloat()

        if (mPathList.isEmpty()) {
            for (i in 0 until mPart) {
                mPathList.add(Path())
            }
        } else {
            for (i in 0 until (mPart - mPathList.size)) {
                mPathList.add(Path())
            }
            mPathList.forEach { it.reset() }
        }

        if (mTextPathList.isEmpty()) {
            for (i in 0 until mPart) {
                mTextPathList.add(Path())
            }
        } else {
            for (i in 0 until (mPart - mTextPathList.size)) {
                mTextPathList.add(Path())
            }
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

        drawWArcAndText(canvas)

        // 绘制控制按钮
        mPointerBitmap?.run {
            canvas.drawBitmap(this, null, mBitmapOutRectF, null)
        }
    }


    private fun drawWArcAndText(canvas: Canvas) {
        for (i in 0 until mPart) {
            if (i >= mPathList.size) return
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
            val offset = mRadius / 5f
            // 弧长公式：圆心角度数 * PI * 半径 / 180
            val arcLength =
                ((if (i != mPart - 1) mNormalAngle else mSpecialAngle) * Math.PI * (mRadius - offset) / 180).toFloat()
            val calcText = calculateText(text, offset, arcLength)

            mTextPaint.color = Color.WHITE
            /*
             * 当只有一个数据，会绘制圆，若还是使 旋转角度增加，
             * 可能会造成无法绘制出文本的情况(比如 path 的 startAngle=[65f 或 258f])；
             * 因此 使用 上下左右 四个点作为 path 的 startAngle
             */
            val tempTextPath = if (mTextList.size == 1) {
                Path().apply {
                    addArc(
                        mOutRectF, 90f * Random.nextInt(4), mNormalAngle
                    )
                }
            } else {
                mTextPathList[i]
            }
            canvas.drawTextOnPath(
                calcText,
                tempTextPath, //mTextPathList[i],
                0f,
                offset,
                mTextPaint
            )
        }

        //高亮开关没开 || 运行中 || 不需要高亮(高亮状态在动画结束时置true) || 选中部分的 index == -1
        if (!mIsSelectPartHighlightSwitch || mRunFlag || !mIsSelectPartHighlight || mSelectPathIndex == -1) return
        for (i in 0 until mPathList.size) {
            if (mSelectPathIndex == i) continue
            mPaint.color = Color.parseColor("#BFFFFFFF") // 白色 透明度75%
            canvas.drawPath(mPathList[i], mPaint) // 绘制扇形 path，
        }
    }

    private fun calculateText(text: String, offset: Float, arcLength: Float): String {
        var textLength = mTextPaint.measureText(text)
        var end = 1
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
        return text.substring(0, if (end > text.length) text.length else end)
    }

    private fun startRotate() {
        if (mRunFlag /*|| mPathList.size == 1*/) return
        mRunFlag = true
        mOnRotateBeginListener?.invoke()

        mIsSelectPartHighlight = false // 开始旋转动画时，不高亮
        mAnim.start()
    }

    private fun stopRotate() {
        if (mTouchToEndStartTime != 0L) return
        mTouchToEndStartTime = System.currentTimeMillis()
        mRunFlag = false
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
                    } else {
                        resetHighlightStatus()
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
        if (mPathList.size == 1) {
            // 若不加判断，可能返回-1
            return 0
        }
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

    fun setAnimEndDuration(duration: Long): TurntableView {
        mAnimEndDuration = duration
        return this
    }

    // 手动触发停止  延时多久
    fun setTouchToEndDelay(delay: Long): TurntableView {
        mTouchToEndDelay = delay
        return this
    }


    fun setColorList(list: List<Int>) {
        mBackColorList.clear()
        mBackColorList.addAll(list)
    }

    // 设置文本。若背景色list数量不足，会创建随机颜色；若多余，会移除
    fun setTextList(list: List<String>): TurntableView {
        mTextList.clear()
        mTextList.addAll(list)
        if (list.size > mBackColorList.size) {
            (mBackColorList.size until list.size).forEach { _ ->
                mBackColorList.add(getRandomColor())
            }
        } else {
            (mBackColorList.size - 1 downTo list.size).forEach {
                mBackColorList.removeAt(it)
            }
        }

        mPart = mTextList.size
        return this
    }

    fun setIsSelectPartHighlightSwitch(isSelectPartHighlight: Boolean) {
        mIsSelectPartHighlightSwitch = isSelectPartHighlight
    }

    fun resetHighlightStatus() {
        if (mIsSelectPartHighlightSwitch) {
            mIsSelectPartHighlight = false
            invalidate()
        }
    }

    // todo
    fun setDrawableList(list: List<Drawable>): TurntableView {
        return this
    }

    // 生成 color 是否要半透明
    private fun generateColor(color: Int, isTranslucent: Boolean): Int {
//        val a = (color shr 24) and 0xff
        val r = (color shr 16) and 0xff
        val g = (color shr 8) and 0xff
        val b = color and 0xff
        return Color.argb(if (isTranslucent) 128 else 255, r, g, b)
    }

    private fun getRandomColor(): Int {
        val sb = StringBuilder()
        var temp: String
        for (i in 0 until 3) {
            temp = Integer.toHexString(Random.nextInt(0xFF))
            if (temp.length == 1) {
                temp = "0$temp"
            }
            sb.append(temp)
        }
        return Color.parseColor("#FF$sb") // argb
    }
}