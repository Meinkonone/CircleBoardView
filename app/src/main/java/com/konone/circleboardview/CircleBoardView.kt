package com.konone.circleboardview

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.TypedArray
import android.graphics.*
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.OverScroller
import java.util.*
import kotlin.math.abs

/**
 * @author Konone
 */
class CircleBoardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {
    private var mCircleCenter: Point? = null
    private var mCircleRadius = 0
    private var mCircleScaleOutRadius = 0
    private var mCircleScaleInRadius = 0
    private var mResourcesContext: Context? = null
    private var mBgPaint: Paint? = null
    private var mCenterSelectPaint: Paint? = null
    private var mThickPaint: Paint? = null
    private var mThinPaint: Paint? = null
    private var mHintPaint: TextPaint? = null
    private var mHintPath: Path? = null
    private var mHintRectF: RectF? = null
    private var mStartAngle = sCenterAngle
    private var mLastX = 0f
    private var mLastY = 0f
    private var mWidth = 0
    private var mMove = 0f
    private var mOffSet = 0f
    private var mMaxOffSet = 0f
    private var mMoveAngleOffSet = 0f
    private var mAnimBaseAngleOffSet = 0f
    private var mLastValueIndex = 0
    private var mSelectedValueIndex = mLastValueIndex
    private var mListener: ValueChangeListener? = null
    private var mValueAnimator: ValueAnimator? = null
    private var mTotalValue: MutableList<String>? = null
    private var mShowValue: MutableList<String>? = null
    private var mShowValueAngle: MutableList<Float>? = ArrayList()
    private var mItemIndexPosition: HashMap<Int, Float>? = null
    private var mNeedSeparateShow = false
    private var mIsSoldInterval = false
    private var mIndexEverInterval = 0
    private var mMaxIndexOffSet = 0
    private var mMaxScaleIndex = 0
    private var mScaleInterval = 0

    private var mUseVelocity = true
    private var mVelocityTracker: VelocityTracker? = null
    private var mScroller: OverScroller? = null
    private var mLastScrollX = 0
    private var mLastPressedX = 0

    interface ValueChangeListener {
        fun onValueChange(valueIndex: Int)
    }

    private fun init(context: Context, attributeSet: AttributeSet?) {
        mResourcesContext = context
        //获取自定义view的自定义相关属性
        val attrArray: TypedArray = context.obtainStyledAttributes(attributeSet, R.styleable.CircleBoardView)
        val circleX = attrArray.getDimensionPixelOffset(R.styleable.CircleBoardView_circleX, 0)
        val circleY = attrArray.getDimensionPixelOffset(R.styleable.CircleBoardView_circleY, 0)
        mCircleRadius = attrArray.getDimensionPixelOffset(R.styleable.CircleBoardView_radius, 0)
        mCircleScaleInRadius =  attrArray.getDimensionPixelOffset(R.styleable.CircleBoardView_hintRadius, 0)
        //...一些列的自定义属性
        attrArray.recycle()

        //为了方便，demo这里直接读取相关属性
        val resources = mResourcesContext!!.resources
        mScroller = OverScroller(context, DecelerateInterpolator())
        mScroller!!.setFriction(0.009f)

        mCircleCenter = if (circleX != 0 && circleY != 0) {
            Point(circleX, circleY)
        } else {
            Point(resources.getDimensionPixelOffset(R.dimen.board_center_x),
                    resources.getDimensionPixelOffset(R.dimen.board_center_y))
        }
        if (mCircleRadius == 0) {
            mCircleRadius = mCircleCenter!!.y
        }
        mCircleScaleOutRadius = mCircleRadius - resources.getDimensionPixelOffset(R.dimen.board_scale_out_radius_padding)
        if (mCircleScaleInRadius == 0) {
            mCircleScaleInRadius = resources.getDimensionPixelOffset(R.dimen.board_scale_in_radius)
        }

        mBgPaint = Paint()
        mBgPaint!!.isAntiAlias = true
        mBgPaint!!.style = Paint.Style.FILL
        mBgPaint!!.strokeWidth = mCircleRadius.toFloat()
        mBgPaint!!.color = resources.getColor(R.color.circle_board_bg)
        mThickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mThickPaint!!.strokeWidth = 4f
        mThickPaint!!.style = Paint.Style.STROKE
        mThickPaint!!.color = Color.WHITE
        mThinPaint = Paint(mThickPaint)
        mThinPaint!!.strokeWidth = 2f
        mThinPaint!!.color = resources.getColor(R.color.circle_board_thin_bg)
        mCenterSelectPaint = Paint(mThickPaint)
        mCenterSelectPaint!!.color = getResources().getColor(R.color.select_color)
        mHintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        mHintPaint!!.isAntiAlias = true
        mHintPaint!!.textSize = 24f
        mHintPaint!!.color = resources.getColor(R.color.circle_board_thin_bg)

        mHintPath = Path()
        mHintRectF = RectF(0f, 90 - sTextDrawAngleOffset, (mCircleCenter!!.x * 2).toFloat(),
                mCircleCenter!!.x * 2 + 90 - sTextDrawAngleOffset)
    }

    fun setScaleInterval(scaleInterval: Int) {
        mScaleInterval = scaleInterval
    }

    fun setMaxIndexOffSet(indexOffSet: Int) {
        mMaxIndexOffSet = indexOffSet
    }

    fun needSeparateShow(needSeparateShow: Boolean) {
        mNeedSeparateShow = needSeparateShow
    }

    fun setIndexEverInterval(indexEverInterval: Int) {
        mIndexEverInterval = indexEverInterval
    }

    fun setIsSolidInterval(solidInterval: Boolean) {
        mIsSoldInterval = solidInterval
    }

    fun useVelocity(useVelocity: Boolean) {
        mUseVelocity = useVelocity
    }

    fun setValueChangeListener(listener: ValueChangeListener?) {
        mListener = listener
    }

    /**
     * should invoke after @method setScaleInterval()
     *
     * @param showValueIndex
     * @param totalValues
     */
    fun setValue(showValueIndex: List<Int?>?, totalValues: MutableList<String>) {
        if (showValueIndex == null) {
            mShowValue = totalValues
        } else {
            mShowValue = ArrayList()
            for (i in showValueIndex.indices) {
                mShowValue!!.add(totalValues[showValueIndex[i]!!])
            }
        }
        for (i in mShowValue!!.indices) {
            mShowValueAngle!!.add(i, getAngleByLength(mHintPaint!!.measureText(mShowValue!![i])))
        }
        mTotalValue = totalValues
        mItemIndexPosition = HashMap()
        mMaxScaleIndex = (mShowValue!!.size - 1) * mScaleInterval + mMaxIndexOffSet
        mMaxOffSet = -(2 * Math.PI * mCircleRadius * mMaxScaleIndex * 1.5f / 360).toFloat()
        for (i in mTotalValue!!.indices) {
            if (mIndexEverInterval == 0 || i == 0) {
                mItemIndexPosition!![i] = -mScaleInterval * 1.5f * i
            } else {
                if (mResourcesContext!!.resources.getString(R.string.auto) == mTotalValue!![0]) {
                    mItemIndexPosition!![i] = -mScaleInterval * 1.5f - mScaleInterval * 1.5f / mIndexEverInterval * (i - 1)
                } else {
                    mItemIndexPosition!![i] = -(mScaleInterval * 1.5f) / mIndexEverInterval * i
                }
            }
        }
        for (i in 0 until mItemIndexPosition!!.size) {
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        mWidth = width
    }

    override fun onDraw(canvas: Canvas) {
        if (visibility != VISIBLE) {
            return
        }
        super.onDraw(canvas)
        //drawBg
        canvas.drawCircle(mCircleCenter!!.x.toFloat(), mCircleCenter!!.y.toFloat(), mCircleRadius.toFloat(), mBgPaint!!)

        for (i in 0..mMaxScaleIndex) {
            val angle = mStartAngle + i * sAngleInterval
            val point1 = getCoordinatePoint(mCircleScaleOutRadius, angle)
            val point2 = getCoordinatePoint(mCircleScaleInRadius, angle)
            if (i % mScaleInterval == 0) { //drawThick
                canvas.drawLine(point1[0], point1[1], point2[0], point2[1], mThickPaint!!)
                //draw Hint
                var drawHint: Boolean = if (mNeedSeparateShow) {
                    i % (mScaleInterval * 2) == 0
                } else {
                    true
                }
                if (drawHint) {
                    mHintPath!!.reset()
                    mHintPath!!.addArc(mHintRectF!!, angle - mShowValueAngle!![i / mScaleInterval] / 2,
                            mShowValueAngle!![i / mScaleInterval] + sTextDrawAngleOffset)
                    canvas.drawTextOnPath(mShowValue!![i / mScaleInterval], mHintPath!!, 0f, 0f, mHintPaint!!)
                }
            } else { //drawThin
                canvas.drawLine(point1[0], point1[1], point2[0], point2[1], mThinPaint!!)
            }
        }
        //drawCenterSelect
        val selectPosTop = getCoordinatePoint(mCircleScaleOutRadius, sCenterAngle)
        val selectPosBottom = getCoordinatePoint(mCircleScaleInRadius, sCenterAngle)
        canvas.drawLine(selectPosTop[0], selectPosTop[1], selectPosBottom[0], selectPosBottom[1], mCenterSelectPaint!!)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) {
            return false
        }
        val action = event.action
        val xPosition = event.x.toInt()
        val yPosition = event.y
        if (mUseVelocity) {
            if (mVelocityTracker == null) {
                mVelocityTracker = VelocityTracker.obtain()
            }
            mVelocityTracker!!.addMovement(event)
        }

        if (mValueAnimator != null && mValueAnimator!!.isRunning) {
            return true
        }
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                forceFinishScroll()
                mLastX = xPosition.toFloat()
                mLastY = yPosition
                mLastPressedX = xPosition
                mMove = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                val xOffSet = mLastX - xPosition
                var yOffSet = yPosition - mLastY
                if (xPosition > mWidth / 2) {
                    yOffSet = -yOffSet
                }
                mMove = if (abs(xOffSet) > abs(yOffSet)) xOffSet else yOffSet
                updateMoveAndValue()
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                mLastX = 0f
                if (mLastPressedX - xPosition == 0 || !mUseVelocity) {
                    onActionUp()
                } else if (mUseVelocity) {
                    countVelocityTracker()
                }
            }
        }
        mLastX = xPosition.toFloat()
        mLastY = yPosition
        return true
    }

    override fun computeScroll() {
        super.computeScroll()
        if (mScroller!!.computeScrollOffset()) {
            if (mScroller!!.currX == mScroller!!.finalX) {
                onActionUp()
            } else {
                val xPosition = mScroller!!.currX
                mMove = (mLastScrollX - xPosition).toFloat()
                updateMoveAndValue()
                mLastScrollX = xPosition
            }
        }
    }

    private fun onActionUp() {
        mLastPressedX = 0
        if (mIsSoldInterval) {
            val index = (mMoveAngleOffSet / 1.5f).toInt()
            val remain = mMoveAngleOffSet % 1.5f
            val intervalOffset: Float
            intervalOffset = if (remain < sSolidIntervalTolerance) {
                remain
            } else {
                mMoveAngleOffSet - (index + 1) * 1.5f
            }
            scrollToTarget(intervalOffset, -1)
        } else {
            scrollToTarget(mMoveAngleOffSet - mItemIndexPosition!![mSelectedValueIndex]!!, -1)
        }
    }

    private fun countVelocityTracker() {
        mScroller!!.forceFinished(true)
        mVelocityTracker!!.computeCurrentVelocity(700)
        val xVelocity = mVelocityTracker!!.xVelocity
        if (abs(xVelocity) > 300) {
            mScroller!!.fling(0, 0, xVelocity.toInt(), 0, Int.MIN_VALUE, Int.MAX_VALUE, 0, 0)
        } else {
            onActionUp()
        }
        mLastScrollX = 0
    }

    @Synchronized
    private fun updateMoveAndValue() {
        mOffSet -= mMove
        if (mOffSet >= 0) {
            mOffSet = 0f
            mScroller!!.forceFinished(true)
        } else if (mOffSet <= mMaxOffSet) {
            mOffSet = mMaxOffSet
            mScroller!!.forceFinished(true)
        }
        mMoveAngleOffSet = getAngleByLength(mOffSet)
        updateAngle()
        mLastValueIndex = mSelectedValueIndex
        mSelectedValueIndex = getSelectIndex(mMoveAngleOffSet)
        notifyValueChange()
    }

    @Synchronized
    private fun updateAngleAndValue(angle: Float) {
        mMoveAngleOffSet = mAnimBaseAngleOffSet - angle
        updateAngle()
    }

    @Synchronized
    private fun updateAngle() {
        mStartAngle = sCenterAngle + mMoveAngleOffSet
        if (mLastValueIndex == -1 && mStartAngle <= sCenterAngle) {
            mStartAngle = sCenterAngle
        } else if (mStartAngle >= sCenterAngle) {
            mStartAngle = sCenterAngle
        } else if (mStartAngle <= sCenterAngle - sAngleInterval * mMaxScaleIndex) {
            mStartAngle = sCenterAngle - sAngleInterval * mMaxScaleIndex
        }
        postInvalidate()
    }

    private fun getSelectIndex(offSet: Float): Int {
        val delta = offSet.coerceAtLeast(getAngleByLength(mMaxOffSet))
        return if (mIsSoldInterval) {
            (-delta / 9 * 1024.0f / 10).toInt()
        } else {
            for (i in 0 until mItemIndexPosition!!.size) {
                val value: Float = mItemIndexPosition!![i]!!
                if (abs(delta - value) <= 1.5 / (mIndexEverInterval + 1)) {
                    return i
                }
            }
            mLastValueIndex
        }
    }

    fun forceFinishScroll() {
        if (mScroller != null) {
            mScroller!!.forceFinished(true)
        }
        if (mValueAnimator != null) {
            mValueAnimator!!.cancel()
        }
    }

    fun setSelectorValue(value: String) {
        var angle: Float
        mMove = 0f
        if (mIsSoldInterval) {
            angle = value.toInt() / (1024.0f / 10) * -9
            if (angle % sAngleInterval != 0f) {
                val index = (mMoveAngleOffSet / 1.5f).toInt()
                val remain = mMoveAngleOffSet % 1.5f
                if (remain < sSolidIntervalTolerance) {
                    angle -= remain
                } else {
                    angle = (index + 1) * 1.5f
                }
            }
        } else {
            mSelectedValueIndex = mTotalValue!!.indexOf(value)
            angle = mItemIndexPosition!![mSelectedValueIndex]!!
        }
        mOffSet = getLengthByAngle(angle)
        updateMoveAndValue()
    }

    private fun getAngleByLength(length: Float): Float {
        return (360.0f * length / (2 * Math.PI * mCircleRadius)).toFloat()
    }

    private fun getLengthByAngle(angle: Float): Float {
        return (angle / 360.0f * (2 * Math.PI * mCircleRadius)).toFloat()
    }

    private fun notifyValueChange() {
        if (mListener == null) {
            return
        }
        mListener!!.onValueChange(mSelectedValueIndex)
    }

    /**
     * getPos by radius and angleF
     */
    private fun getCoordinatePoint(radius: Int, cirAngle: Float): FloatArray {
        val point = FloatArray(2)
        var arcAngle = Math.toRadians(cirAngle.toDouble())
        if (cirAngle < 90) {
            point[0] = (mCircleCenter!!.x + Math.cos(arcAngle) * radius).toFloat()
            point[1] = (mCircleCenter!!.y + Math.sin(arcAngle) * radius).toFloat()
        } else if (cirAngle == 90f) {
            point[0] = mCircleCenter!!.x.toFloat()
            point[1] = (mCircleCenter!!.y + radius).toFloat()
        } else if (cirAngle > 90 && cirAngle < 180) {
            arcAngle = Math.PI * (180 - cirAngle) / 180.0
            point[0] = (mCircleCenter!!.x - Math.cos(arcAngle) * radius).toFloat()
            point[1] = (mCircleCenter!!.y + Math.sin(arcAngle) * radius).toFloat()
        } else if (cirAngle == 180f) {
            point[0] = (mCircleCenter!!.x - radius).toFloat()
            point[1] = mCircleCenter!!.y.toFloat()
        } else if (cirAngle > 180 && cirAngle < 270) {
            arcAngle = Math.PI * (cirAngle - 180) / 180.0
            point[0] = (mCircleCenter!!.x - Math.cos(arcAngle) * radius).toFloat()
            point[1] = (mCircleCenter!!.y - Math.sin(arcAngle) * radius).toFloat()
        } else if (cirAngle == 270f) {
            point[0] = mCircleCenter!!.x.toFloat()
            point[1] = (mCircleCenter!!.y - radius).toFloat()
        } else {
            arcAngle = Math.PI * (360 - cirAngle) / 180.0
            point[0] = (mCircleCenter!!.x + Math.cos(arcAngle) * radius).toFloat()
            point[1] = (mCircleCenter!!.y - Math.sin(arcAngle) * radius).toFloat()
        }
        return point
    }

    private fun scrollToTarget(targetMove: Float, thickIndex: Int) {
        if (targetMove == 0f) {
            mMove = 0f
            return
        }
        mAnimBaseAngleOffSet = mMoveAngleOffSet
        mValueAnimator = ValueAnimator.ofFloat(0f, targetMove)
        mValueAnimator!!.duration = 100
        mValueAnimator!!.interpolator = PathInterpolator(0.16f, 0f, 0.33f, 1f)
        mValueAnimator!!.addUpdateListener(ValueAnimator.AnimatorUpdateListener { animation: ValueAnimator ->
            val animatedValue = animation.animatedValue as Float
            updateAngleAndValue(animatedValue)
        })
        mValueAnimator!!.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                mOffSet -= getLengthByAngle(targetMove)
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
        mValueAnimator!!.start()
    }

    companion object {
        private const val sTextDrawAngleOffset = 3.5f
        private const val sCenterAngle = 270f
        private const val sAngleInterval = 1.5f
        private const val sSolidIntervalTolerance = sAngleInterval / 2 - 0.15f
    }

    init {
        init(context, attrs)
    }
}