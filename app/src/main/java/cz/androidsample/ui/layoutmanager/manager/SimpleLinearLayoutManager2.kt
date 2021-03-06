package com.cz.sample.ui.layoutmanager

import android.support.v7.widget.LinearLayoutCompat
import android.support.v7.widget.OrientationHelper
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup

/**
 * Created by cz on 2017/9/14.
 * 一个LinearLayoutManager最精简核心实现
 * 无findFirstVisiblePosition
 */
open class SimpleLinearLayoutManager2 : RecyclerView.LayoutManager {
    companion object {
        val DIRECTION_START = -1
        val DIRECTION_END = 1

        val HORIZONTAL = OrientationHelper.HORIZONTAL
        val VERTICAL = OrientationHelper.VERTICAL
    }
    protected lateinit var orientationHelper: OrientationHelper
    private val layoutState=LayoutState()
    /**
     * 当前排版方向
     * @see {@link .HORIZONTAL} or {@link .VERTICAL}
     */
    private var orientation: Int = LinearLayoutCompat.VERTICAL

    @JvmOverloads constructor(orientation: Int = LinearLayoutCompat.VERTICAL) {
        setOrientation(orientation)
    }

    override fun generateDefaultLayoutParams() =RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    /**
     * 设置方向
     * @param orientation
     */
    open fun setOrientation(orientation: Int) {
        if (orientation != HORIZONTAL && orientation != VERTICAL) {
            throw IllegalArgumentException("invalid orientation:" + orientation)
        }
        this.orientation = orientation
        if (OrientationHelper.HORIZONTAL == orientation) {
            orientationHelper = OrientationHelper.createHorizontalHelper(this)
        } else if (OrientationHelper.VERTICAL == orientation) {
            orientationHelper = OrientationHelper.createVerticalHelper(this)
        }
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        super.onLayoutChildren(recycler, state)
        if(0==itemCount||state.isPreLayout){
            //将当前所有的RecyclerView的ChildView进行回收
            detachAndScrapAttachedViews(recycler)
        } else if(state.didStructureChange()){
            detachAndScrapAttachedViews(recycler)
            //当前有效空间
            updateLayoutStateToFillEnd(0,0)
            //填充控件
            fill(recycler,state)
        }
    }

    private fun updateLayoutStateToFillEnd(itemPosition: Int, offset: Int) {
        layoutState.layoutOffset = offset
        layoutState.available = height - paddingBottom - offset
        layoutState.position = itemPosition
        layoutState.itemDirection = DIRECTION_END
    }

    /**
     * 填充控件
     */
    private fun fill(recycler: RecyclerView.Recycler, state: RecyclerView.State):Int {
        //当前可填充空间
        val start=layoutState.available
        var remainingSpace=layoutState.available
        while(0<remainingSpace&&hasMore(state)){
            //循环排版子控件,直到塞满为止
            val consumed=layoutChildView(recycler,state)
            layoutState.layoutOffset +=consumed*layoutState.itemDirection
            layoutState.available-=consumed
            remainingSpace-=consumed
        }
        //返回排版后,所占用空间
        return start-layoutState.available
    }


    override fun canScrollHorizontally()= HORIZONTAL==orientation
    override fun canScrollVertically() = VERTICAL==orientation

    override fun scrollHorizontallyBy(dx: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        if (orientation == VERTICAL) return 0
        return scrollBy(dx,recycler,state)
    }
    /**
     * 此处实现纵向滚动代码
     */
    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        if (orientation == HORIZONTAL) return 0
        return scrollBy(dy,recycler,state)
    }

    /**
     * 滚动加载控件过程
     */
    private fun scrollBy(dy:Int, recycler:RecyclerView.Recycler,state: RecyclerView.State):Int {
        if (childCount == 0 || dy == 0) {
            return 0
        }
        //设定当前操作方向,dy>0 代表方向向下,否则则向上
        val layoutDirection = if (dy > 0) DIRECTION_END else DIRECTION_START
        //获取操作的偏移正值
        val absDy = Math.abs(dy)
        //动态更新布局状态
        updateLayoutState(layoutDirection,absDy)
        //填充当前布局
        var consumed=layoutState.scrollingOffset +fill(recycler,state)
        //做边界处理
        val scrolled = if (absDy > consumed) layoutDirection * consumed else dy
        if(orientation == HORIZONTAL){
            offsetChildrenHorizontal(-scrolled)//横向滚动
        } else {
            offsetChildrenVertical(-scrolled)//纵向滚动
        }
        return scrolled
    }

    /**
     * 根据滚动偏移量,更新布局状态值
     */
    private fun updateLayoutState(layoutDirection:Int,requiredSpace: Int) {
        val scrollingOffset: Int
        layoutState.itemDirection= layoutDirection
        if(layoutDirection== DIRECTION_END){
            val view=getChildAt(childCount-1)
            layoutState.position=getPosition(view) + layoutState.itemDirection
            layoutState.layoutOffset = orientationHelper.getDecoratedEnd(view)
            //当前RecyclerView底部-最后一个控件底部,因为最后一个控件会超出底部
            scrollingOffset=orientationHelper.getDecoratedEnd(view) - orientationHelper.endAfterPadding
        } else {
            val child=getChildAt(0)
            layoutState.position=getPosition(child) + layoutState.itemDirection
            layoutState.layoutOffset =orientationHelper.getDecoratedStart(child)
            //当前RecyclerView-控件顶点 因为控件可能超出顶端
            scrollingOffset= -orientationHelper.getDecoratedStart(child) + orientationHelper.startAfterPadding
        }
        //记录控件,缺多少值可以排版.也可以理解为,可排版空余值,默认不可排版时,一般为-1数
        //比如往下拉,requiredSpace为10 scrollOffset为105 则当前再滑动95下一个条目才开始加载,反之亦然
        layoutState.available= requiredSpace -scrollingOffset
        layoutState.scrollingOffset =scrollingOffset
    }




    /**
     * 填充子控件
     */
    protected open fun layoutChildView(recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        val view=nextView(recycler,state)
        if (layoutState.itemDirection == DIRECTION_END) {
            addView(view)
        } else {
            addView(view, 0)
        }
        //测量控件
        measureChildWithMargins(view,0,0)
        /* orientationHelper.getDecoratedMeasurement(view)会获取当前方向控件计算尺寸
            如horizontal 取width作为计算长度+ insets:分隔线空间+控件margin值 为总计算高度 */
        val consumed = orientationHelper.getDecoratedMeasurement(view)
        var left: Int=paddingLeft
        val top: Int
        val right: Int
        val bottom: Int
        if (orientation == VERTICAL) {
            //width+分隔线+左右margin,控制右排版位置
            right = left + orientationHelper.getDecoratedMeasurementInOther(view)
            if (layoutState.itemDirection == DIRECTION_START) {
                bottom = layoutState.layoutOffset
                top = layoutState.layoutOffset - consumed
            } else {
                top = layoutState.layoutOffset
                bottom = layoutState.layoutOffset + consumed
            }
        } else {
            top = paddingTop
            bottom = top + orientationHelper.getDecoratedMeasurementInOther(view)
            if (layoutState.itemDirection == DIRECTION_START) {
                right = layoutState.layoutOffset
                left = layoutState.layoutOffset - consumed
            } else {
                left = layoutState.layoutOffset
                right = layoutState.layoutOffset + consumed
            }
        }
        layoutDecorated(view, left, top, right, bottom)
        //返回控件高度/宽
        return consumed
    }

    protected fun nextView(recycler: RecyclerView.Recycler, state: RecyclerView.State): View {
        //获取一个控件,会走完缓存->onCreateViewHolder()
        val view=recycler.getViewForPosition(layoutState.position)
        layoutState.position+=layoutState.itemDirection
        return view
    }

    protected fun hasMore(state: RecyclerView.State):Boolean{
        return layoutState.position in 0..state.itemCount-1
    }


    inner class LayoutState{
        /**
         * 当前有效空间
         */
        var available=0
        /**
         * 当前排版位置
         */
        var layoutOffset =0
        /**
         * 当前滚动位置
         */
        var scrollingOffset =0
        /**
         * 当前位置
         */
        var position:Int=0
        /**
         * 当前操作条目方向
         */
        var itemDirection=0
    }


}