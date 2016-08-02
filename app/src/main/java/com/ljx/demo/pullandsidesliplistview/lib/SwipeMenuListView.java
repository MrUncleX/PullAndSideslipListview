package com.ljx.demo.pullandsidesliplistview.lib;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Scroller;
import android.widget.TextView;

import com.ljx.demo.pullandsidesliplistview.R;
import com.ljx.demo.pullandsidesliplistview.XListViewFooter;
import com.ljx.demo.pullandsidesliplistview.XListViewHeader;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 
 * @author baoyz
 * @date 2014-8-18
 * 
 */
public class SwipeMenuListView extends ListView implements OnScrollListener {

	private static final int TOUCH_STATE_NONE = 0;
	private static final int TOUCH_STATE_X = 1;
	private static final int TOUCH_STATE_Y = 2;

	private int MAX_Y = 5;
	private int MAX_X = 3;
	private float mDownX;
	private float mDownY;
	private int mTouchState;
	private int mTouchPosition;
	private SwipeMenuLayout mTouchView;
	private OnSwipeListener mOnSwipeListener;

	private SwipeMenuCreator mMenuCreator;
	private OnMenuItemClickListener mOnMenuItemClickListener;
	private Interpolator mCloseInterpolator;
	private Interpolator mOpenInterpolator;


	private float mLastY = -1; // save event y
	private Scroller mScroller; // 用于回滚
	private OnScrollListener mScrollListener; // 回滚监听
	// 触发刷新和加载更多接口.
	private IXListViewListener mListViewListener;
	// -- 头部的View
	private XListViewHeader mHeaderView;
	// 查看头部的内容，用它计算头部高度，和隐藏它
	// 当禁用的时候刷新
	private RelativeLayout mHeaderViewContent;
	private TextView mHeaderTimeView;
	private int mHeaderViewHeight; // 头部View的高
	private boolean mEnablePullRefresh = true;
	private boolean mPullRefreshing = false; // 是否刷新.
	// -- 底部的View
	private XListViewFooter mFooterView;
	private boolean mEnablePullLoad;
	private boolean mPullLoading;
	private boolean mIsFooterReady = false;
	// 总列表项，用于检测列表视图的底部
	private int mTotalItemCount;

	// for mScroller, 滚动页眉或者页脚
	private int mScrollBack;
	private final static int SCROLLBACK_HEADER = 0;// 顶部
	private final static int SCROLLBACK_FOOTER = 1;// 下部

	private final static int SCROLL_DURATION = 400; // 滚动回时间
	private final static int PULL_LOAD_MORE_DELTA = 50; // 当大于50PX的时候，加载更多

	private final static float OFFSET_RADIO = 1.8f; // support iOS like pull
	// feature.



	public SwipeMenuListView(Context context) {
		super(context);
		init();
		initWithContext(context);
	}

	public SwipeMenuListView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
		initWithContext(context);
	}

	public SwipeMenuListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
		initWithContext(context);
	}
	private void initWithContext(Context context) {
		mScroller = new Scroller(context, new DecelerateInterpolator());
		// XListView need the scroll event, and it will dispatch the event to
		// user's listener (as a proxy).
		super.setOnScrollListener(this);

		// 初始化头部View
		mHeaderView = new XListViewHeader(context);
		mHeaderViewContent = (RelativeLayout) mHeaderView
				.findViewById(R.id.xlistview_header_content);
		mHeaderTimeView = (TextView) mHeaderView
				.findViewById(R.id.xlistview_header_time);
		addHeaderView(mHeaderView);// 把头部这个视图添加进去

		// 初始化底部的View
		mFooterView = new XListViewFooter(context);

		// 初始化头部高度
		mHeaderView.getViewTreeObserver().addOnGlobalLayoutListener(
				new OnGlobalLayoutListener() {
					@Override
					public void onGlobalLayout() {
						mHeaderViewHeight = mHeaderViewContent.getHeight();
						getViewTreeObserver()
								.removeGlobalOnLayoutListener(this);
					}
				});
	}
	private void init() {
		MAX_X = dp2px(MAX_X);
		MAX_Y = dp2px(MAX_Y);
		mTouchState = TOUCH_STATE_NONE;
	}

	@Override
	public void setAdapter(ListAdapter adapter) {
		if (mIsFooterReady == false) {
			mIsFooterReady = true;
			addFooterView(mFooterView);
		}
		super.setAdapter(new SwipeMenuAdapter(getContext(), adapter) {
			@Override
			public void createMenu(SwipeMenu menu) {
				if (mMenuCreator != null) {
					mMenuCreator.create(menu);
				}
			}

			@Override
			public void onItemClick(SwipeMenuView view, SwipeMenu menu,
									int index) {
				boolean flag = false;
				if (mOnMenuItemClickListener != null) {
					flag = mOnMenuItemClickListener.onMenuItemClick(
							view.getPosition(), menu, index);
				}
				if (mTouchView != null && !flag) {
					mTouchView.smoothCloseMenu();
				}
			}
		});
	}

	public void setCloseInterpolator(Interpolator interpolator) {
		mCloseInterpolator = interpolator;
	}

	public void setOpenInterpolator(Interpolator interpolator) {
		mOpenInterpolator = interpolator;
	}

	public Interpolator getOpenInterpolator() {
		return mOpenInterpolator;
	}

	public Interpolator getCloseInterpolator() {
		return mCloseInterpolator;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		return super.onInterceptTouchEvent(ev);
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		if (ev.getAction() != MotionEvent.ACTION_DOWN && mTouchView == null)
			return super.onTouchEvent(ev);
		int action = MotionEventCompat.getActionMasked(ev);
		action = ev.getAction();
		switch (action) {
			case MotionEvent.ACTION_DOWN:
				mLastY = ev.getRawY();

				int oldPos = mTouchPosition;
				mDownX = ev.getX();
				mDownY = ev.getY();
				mTouchState = TOUCH_STATE_NONE;

				mTouchPosition = pointToPosition((int) ev.getX(), (int) ev.getY());

				if (mTouchPosition == oldPos && mTouchView != null
						&& mTouchView.isOpen()) {
					mTouchState = TOUCH_STATE_X;
					mTouchView.onSwipe(ev);
					return true;
				}

				View view = getChildAt(mTouchPosition - getFirstVisiblePosition());

				if (mTouchView != null && mTouchView.isOpen()) {
					mTouchView.smoothCloseMenu();
					mTouchView = null;
					// return super.onTouchEvent(ev);
					// try to cancel the touch event
					MotionEvent cancelEvent = MotionEvent.obtain(ev);
					cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
					onTouchEvent(cancelEvent);
					return true;
				}
				if (view instanceof SwipeMenuLayout) {
					mTouchView = (SwipeMenuLayout) view;
				}
				if (mTouchView != null) {
					mTouchView.onSwipe(ev);
				}
				break;
			case MotionEvent.ACTION_MOVE:
				float dy = Math.abs((ev.getY() - mDownY));
				float dx = Math.abs((ev.getX() - mDownX));
				if (mTouchState == TOUCH_STATE_X) {
					if (mTouchView != null) {
						mTouchView.onSwipe(ev);
					}
					getSelector().setState(new int[] { 0 });
					ev.setAction(MotionEvent.ACTION_CANCEL);
					super.onTouchEvent(ev);
					return true;
				} else if (mTouchState == TOUCH_STATE_NONE) {
					if (Math.abs(dy) > MAX_Y) {
						mTouchState = TOUCH_STATE_Y;
					} else if (dx > MAX_X) {
						mTouchState = TOUCH_STATE_X;
						if (mOnSwipeListener != null) {
							mOnSwipeListener.onSwipeStart(mTouchPosition);
						}
					}
				}

				final float deltaY = ev.getRawY() - mLastY;
				mLastY = ev.getRawY();
				System.out.println("数据监测：" + getFirstVisiblePosition() + "---->"
						+ getLastVisiblePosition());
				if (getFirstVisiblePosition() == 0
						&& (mHeaderView.getVisiableHeight() > 0 || deltaY > 0)) {
					// 第一项显示,标题显示或拉下来.
					if(mEnablePullRefresh) {
						updateHeaderHeight(deltaY / OFFSET_RADIO);
					}
					invokeOnScrolling();
				} else if (getLastVisiblePosition() == mTotalItemCount - 1
						&& (mFooterView.getBottomMargin() > 0 || deltaY < 0)) {
					// 最后一页，已停止或者想拉起
					if(mEnablePullLoad) {
						updateFooterHeight(-deltaY / OFFSET_RADIO);
					}
				}

				break;
			case MotionEvent.ACTION_UP:
				mLastY = -1; // 重置
				if (getFirstVisiblePosition() == 0) {
					// 调用刷新,如果头部视图高度大于设定高度。
					if (mEnablePullRefresh
							&& mHeaderView.getVisiableHeight() > mHeaderViewHeight) {
						mPullRefreshing = true;// 那么刷新
						mHeaderView.setState(XListViewHeader.STATE_REFRESHING);
						if (mListViewListener != null) {
							mListViewListener.onRefresh();
						}
					}
					resetHeaderHeight();// 刷新完毕，重置头部高度，也就是返回上不
				}
				if (getLastVisiblePosition() == mTotalItemCount - 1) {
					// 调用加载更多.
					if (mEnablePullLoad
							&& mFooterView.getBottomMargin() > PULL_LOAD_MORE_DELTA) {
						startLoadMore();// 如果底部视图高度大于可以加载高度，那么就开始加载
					}
					resetFooterHeight();// 重置加载更多视图高度
				}
				if (mTouchState == TOUCH_STATE_X) {
					if (mTouchView != null) {
						mTouchView.onSwipe(ev);
						if (!mTouchView.isOpen()) {
							mTouchPosition = -1;
							mTouchView = null;
						}
					}
					if (mOnSwipeListener != null) {
						mOnSwipeListener.onSwipeEnd(mTouchPosition);
					}
					ev.setAction(MotionEvent.ACTION_CANCEL);
					super.onTouchEvent(ev);
					return true;
				}

				break;
			default:
				mLastY = -1; // 重置
				if (getFirstVisiblePosition() == 0) {
					// 调用刷新,如果头部视图高度大于设定高度。
					if (mEnablePullRefresh
							&& mHeaderView.getVisiableHeight() > mHeaderViewHeight) {
						mPullRefreshing = true;// 那么刷新
						mHeaderView.setState(XListViewHeader.STATE_REFRESHING);
						if (mListViewListener != null) {
							mListViewListener.onRefresh();
						}
					}
					resetHeaderHeight();// 刷新完毕，重置头部高度，也就是返回上不
				}
				if (getLastVisiblePosition() == mTotalItemCount - 1) {
					// 调用加载更多.
					if (mEnablePullLoad
							&& mFooterView.getBottomMargin() > PULL_LOAD_MORE_DELTA) {
						startLoadMore();// 如果底部视图高度大于可以加载高度，那么就开始加载
					}
					resetFooterHeight();// 重置加载更多视图高度
				}
				break;
		}
		return super.onTouchEvent(ev);
	}

	public void smoothOpenMenu(int position) {
		if (position >= getFirstVisiblePosition()
				&& position <= getLastVisiblePosition()) {
			View view = getChildAt(position - getFirstVisiblePosition());
			if (view instanceof SwipeMenuLayout) {
				mTouchPosition = position;
				if (mTouchView != null && mTouchView.isOpen()) {
					mTouchView.smoothCloseMenu();
				}
				mTouchView = (SwipeMenuLayout) view;
				mTouchView.smoothOpenMenu();
			}
		}
	}

	private int dp2px(int dp) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
				getContext().getResources().getDisplayMetrics());
	}

	public void setMenuCreator(SwipeMenuCreator menuCreator) {
		this.mMenuCreator = menuCreator;
	}

	public void setOnMenuItemClickListener(
			OnMenuItemClickListener onMenuItemClickListener) {
		this.mOnMenuItemClickListener = onMenuItemClickListener;
	}

	public void setOnSwipeListener(OnSwipeListener onSwipeListener) {
		this.mOnSwipeListener = onSwipeListener;
	}

	public static interface OnMenuItemClickListener {
		boolean onMenuItemClick(int position, SwipeMenu menu, int index);
	}

	public static interface OnSwipeListener {
		void onSwipeStart(int position);

		void onSwipeEnd(int position);
	}



	/**
	 * 启用或禁用下拉刷新功能.
	 *
	 * @param enable
	 */
	public void setPullRefreshEnable(boolean enable) {
		mEnablePullRefresh = enable;
		if (!mEnablePullRefresh) { // 禁用,隐藏内容
			mHeaderViewContent.setVisibility(View.INVISIBLE);// 如果为false则隐藏下拉刷新功能
		} else {
			mHeaderViewContent.setVisibility(View.VISIBLE);// 否则就显示下拉刷新功能
		}
	}

	/**
	 * 启用或禁用加载更多的功能.
	 *
	 * @param enable
	 */
	public void setPullLoadEnable(boolean enable) {
		mEnablePullLoad = enable;
		if (!mEnablePullLoad) {
			mFooterView.hide();// 隐藏
			mFooterView.setOnClickListener(null);
		} else {
			mPullLoading = false;
			mFooterView.show();// 显示
			mFooterView.setState(XListViewFooter.STATE_NORMAL);
			// both "上拉" 和 "点击" 将调用加载更多.
			mFooterView.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					startLoadMore();
				}
			});
		}
	}

	/**
	 * 停止刷新, 重置头视图.
	 */
	public void stopRefresh() {
		if (mPullRefreshing == true) {
			mPullRefreshing = false;
			resetHeaderHeight();
		}
	}

	/**
	 * stop load more, reset footer view.
	 */
	public void stopLoadMore() {
		if (mPullLoading == true) {
			mPullLoading = false;
			mFooterView.setState(XListViewFooter.STATE_NORMAL);
		}
	}

	/**
	 * 設置最後一次刷新時間
	 *
	 * @param time
	 */
	@SuppressLint("SimpleDateFormat")
	public void setRefreshTime(String time) {
		SimpleDateFormat   formatter   =   new   SimpleDateFormat   ("yyyy年MM月dd日   HH:mm:ss     ");
		Date   curDate   =   new   Date(System.currentTimeMillis());
		//获取当前时间
		String   str   =   formatter.format(curDate);
		mHeaderTimeView.setText(str);
	}

	private void invokeOnScrolling() {
		if (mScrollListener instanceof OnXScrollListener) {
			OnXScrollListener l = (OnXScrollListener) mScrollListener;
			l.onXScrolling(this);
		}
	}

	private void updateHeaderHeight(float delta) {
		mHeaderView.setVisiableHeight((int) delta
				+ mHeaderView.getVisiableHeight());
		if (mEnablePullRefresh && !mPullRefreshing) { // 未处于刷新状态，更新箭头
			if (mHeaderView.getVisiableHeight() > mHeaderViewHeight) {
				mHeaderView.setState(XListViewHeader.STATE_READY);
			} else {
				mHeaderView.setState(XListViewHeader.STATE_NORMAL);
			}
		}
		setSelection(0); // scroll to top each time
	}

	/**
	 * 重置头视图的高度
	 */
	private void resetHeaderHeight() {
		int height = mHeaderView.getVisiableHeight();
		if (height == 0) // 不显示.
			return;
		// 不显示刷新和标题的时候，什么都不显示
		if (mPullRefreshing && height <= mHeaderViewHeight) {
			return;
		}
		int finalHeight = 0; // 默认：滚动回头.
		// 当滚动回显示所有头标题时候，刷新
		if (mPullRefreshing && height > mHeaderViewHeight) {
			finalHeight = mHeaderViewHeight;
		}
		mScrollBack = SCROLLBACK_HEADER;
		mScroller.startScroll(0, height, 0, finalHeight - height,
				SCROLL_DURATION);
		// 触发刷新
		invalidate();
	}

	// 改变底部视图高度
	private void updateFooterHeight(float delta) {
		int height = mFooterView.getBottomMargin() + (int) delta;
		if (mEnablePullLoad && !mPullLoading) {
			if (height > PULL_LOAD_MORE_DELTA) { // 高度足以调用加载更多
				mFooterView.setState(XListViewFooter.STATE_READY);
			} else {
				mFooterView.setState(XListViewFooter.STATE_NORMAL);
			}
		}
		mFooterView.setBottomMargin(height);

		// setSelection(mTotalItemCount - 1); // scroll to bottom
	}

	private void resetFooterHeight() {
		int bottomMargin = mFooterView.getBottomMargin();
		if (bottomMargin > 0) {
			mScrollBack = SCROLLBACK_FOOTER;
			mScroller.startScroll(0, bottomMargin, 0, -bottomMargin,
					SCROLL_DURATION);
			invalidate();
		}
	}

	// 开始加载更多
	private void startLoadMore() {
		mPullLoading = true;
		mFooterView.setState(XListViewFooter.STATE_LOADING);
		if (mListViewListener != null) {
			mListViewListener.onLoadMore();
		}
	}

	@Override
	public void computeScroll() {
		if (mScroller.computeScrollOffset()) {
			if (mScrollBack == SCROLLBACK_HEADER) {
				mHeaderView.setVisiableHeight(mScroller.getCurrY());
			} else {
				mFooterView.setBottomMargin(mScroller.getCurrY());
			}
			postInvalidate();
			invokeOnScrolling();
		}
		super.computeScroll();
	}

	@Override
	public void setOnScrollListener(OnScrollListener l) {
		mScrollListener = l;
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		if (mScrollListener != null) {
			mScrollListener.onScrollStateChanged(view, scrollState);
		}
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem,
						 int visibleItemCount, int totalItemCount) {
		// 发送到用户的监听器
		mTotalItemCount = totalItemCount;
		if (mScrollListener != null) {
			mScrollListener.onScroll(view, firstVisibleItem, visibleItemCount,
					totalItemCount);
		}
	}

	public void setXListViewListener(IXListViewListener l) {
		mListViewListener = l;
	}

	/**
	 * 你可以监听到列表视图，OnScrollListener 或者这个. 他将会被调用 , 当头部或底部触发的时候
	 */
	public interface OnXScrollListener extends OnScrollListener {
		public void onXScrolling(View view);
	}

	/**
	 * 实现这个接口来刷新/负载更多的事件
	 */
	public interface IXListViewListener {
		public void onRefresh();

		public void onLoadMore();
	}

}
