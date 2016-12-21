package com.sothree.slidinguppanel;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

import com.nineoldandroids.view.animation.AnimatorProxy;
import com.sothree.slidinguppanel.library.R;

public class SlidingUpPanelLayout extends ViewGroup {

    private static final String TAG = SlidingUpPanelLayout.class.getSimpleName();

    /**
     * Default peeking out panel height
     */
    private static final int DEFAULT_PANEL_HEIGHT = 68; // dp;

    /**
     * Default anchor point height
     */
    private static final float DEFAULT_ANCHOR_POINT = 1.0f; // In relative %

    /**
     * Default initial state for the component
     */
    private static PanelState DEFAULT_SLIDE_STATE = PanelState.COLLAPSED;

    /**
     * Default height of the shadow above the peeking out panel
     */
    private static final int DEFAULT_SHADOW_HEIGHT = 4; // dp;

    /**
     * If no fade color is given by default it will fade to 80% gray.
     */
    private static final int DEFAULT_FADE_COLOR = 0x99000000;

    /**
     * Default Minimum velocity that will be detected as a fling
     */
    private static final int DEFAULT_MIN_FLING_VELOCITY = 400; // dips per second
    /**
     * Default is set to false because that is how it was written
     */
    private static final boolean DEFAULT_OVERLAY_FLAG = false;
    /**
     * Default is set to true for clip panel for performance reasons
     */
    private static final boolean DEFAULT_CLIP_PANEL_FLAG = true;
    /**
     * Default attributes for layout
     */
    private static final int[] DEFAULT_ATTRS = new int[]{
            android.R.attr.gravity
    };

    /**
     * Minimum velocity that will be detected as a fling
     */
    private int mMinFlingVelocity = DEFAULT_MIN_FLING_VELOCITY;

    /**
     * The fade color used for the panel covered by the slider. 0 = no fading.
     */
    private int mCoveredFadeColor = DEFAULT_FADE_COLOR;

    /**
     * Default parallax length of the main view
     */
    private static final int DEFAULT_PARALLAX_OFFSET = 0;

    /**
     * The paint used to dim the main layout when sliding
     */
    private final Paint coveredFadePaint = new Paint();

    /**
     * Drawable used to draw the shadow between panes.
     */
    private final Drawable shadowDrawable;

    /**
     * The size of the overhang in pixels.
     */
    private int panelHeight = -1;

    /**
     * The size of the shadow in pixels.
     */
    private int shadowHeight = -1;

    /**
     * Parallax offset
     */
    private int parallaxOffset = -1;

    /**
     * True if the collapsed panel should be dragged up.
     */
    private boolean isSlidingUp;

    /**
     * Panel overlays the windows instead of putting it underneath it.
     */
    private boolean overlayContent = DEFAULT_OVERLAY_FLAG;

    /**
     * The main view is clipped to the main top border
     */
    private boolean clipPanel = DEFAULT_CLIP_PANEL_FLAG;

    /**
     * If provided, the panel can be dragged by only this view. Otherwise, the entire panel can be
     * used for dragging.
     */
    private View dragView;

    /**
     * If provided, the panel can be dragged by only this view. Otherwise, the entire panel can be
     * used for dragging.
     */
    private int dragViewResId = -1;

    /**
     * If provided, the panel will transfer the scroll from this view to itself when needed.
     */
    private View scrollableView;
    private int scrollableViewResId;

    private ScrollableViewHelper scrollableViewHelper = new ScrollableViewHelper();

    /**
     * The child view that can slide, if any.
     */
    private View slideableView;

    /**
     * The main view
     */
    private View mainView;

    /**
     * The floating action button, if provided
     */
    private View floatingActionButton;

    /**
     * Current state of the slideable view.
     */
    public enum PanelState {
        EXPANDED,
        COLLAPSED,
        ANCHORED,
        HIDDEN,
        DRAGGING
    }

    private PanelState slideState = DEFAULT_SLIDE_STATE;

    private boolean alreadyCollapsedStateY;

    private boolean alreadyExpandedStateY;

    /**
     * If the current slide state is DRAGGING, this will store the last non dragging state
     */
    private PanelState lastNotDraggingSlideState = DEFAULT_SLIDE_STATE;

    /**
     * How far the panel is offset from its expanded position.
     * range [0, 1] where 0 = collapsed, 1 = expanded.
     */
    private float slideOffset;

    /**
     * How far in pixels the slideable panel may move.
     */
    private int slideRange;

    /**
     * An anchor point where the panel can stop during sliding
     */
    private float anchorPoint = 1.f;

    /**
     * A panel view is locked into internal scrolling or another condition that
     * is preventing a drag.
     */
    private boolean isUnableToDrag;

    /**
     * Flag indicating that sliding feature is enabled\disabled
     */
    private boolean isTouchEnabled;

    private float prevMotionY;
    private float initialMotionX;
    private float initialMotionY;
    private boolean isScrollableViewHandlingTouch = false;

    private PanelSlideListener panelSlideListener;

    private final ViewDragHelper dragHelper;

    /**
     * Stores whether or not the pane was expanded the last time it was slideable.
     * If expand/collapse operations are invoked this state is modified. Used by
     * instance state save/restore.
     */
    private boolean firstLayout = true;

    private final Rect tmpRect = new Rect();

    /**
     * Listener for monitoring events about sliding panes.
     */
    public interface PanelSlideListener {
        /**
         * Called when a sliding pane's position changes.
         *
         * @param panel       The child view that was moved
         * @param slideOffset The new offset of this sliding pane within its range, from 0-1
         */
        public void onPanelSlide(View panel, float slideOffset);

        /**
         * Called when a sliding panel becomes slid completely collapsed.
         *
         * @param panel The child view that was slid to an collapsed position
         */
        public void onPanelCollapsed(View panel);

        /**
         * Called when a sliding panel becomes slid completely expanded.
         *
         * @param panel The child view that was slid to a expanded position
         */
        public void onPanelExpanded(View panel);

        /**
         * Called when a sliding panel becomes anchored.
         *
         * @param panel The child view that was slid to a anchored position
         */
        public void onPanelAnchored(View panel);

        /**
         * Called when a sliding panel becomes completely hidden.
         *
         * @param panel The child view that was slid to a hidden position
         */
        public void onPanelHidden(View panel);

        /**
         * Called when a sliding panel gets hidden via hidePanel.
         *
         * @param panel The child view that was hidden
         */
        public void onPanelHiddenExecuted(View panel, Interpolator interpolator, int duration);

        /**
         * Called when a sliding panel gets shown via showPanel.
         *
         * @param panel The child view that was shown
         */
        public void onPanelShownExecuted(View panel, Interpolator interpolator, int duration);

        /**
         * Called when a sliding panel touches the top.
         *
         * @param panel   The child view that was shown
         * @param reached Whether the panel has just reached the top position (true) or left it (false)
         */
        public void onTopReached(View panel, boolean reached);

        /**
         * Called when a sliding panel touches the bottom.
         *
         * @param panel   The child view that was shown
         * @param reached Whether the panel has just reached the bottom position (true) or left it (false)
         */
        public void onBottomReached(View panel, boolean reached);

        /**
         * Called when a sliding panel touches the top.
         *
         * @param panel The child view that was shown
         * @param state The panel state that was used for layout
         */
        public void onPanelLayout(View panel, PanelState state);
    }

    /**
     * No-op stubs for {@link PanelSlideListener}. If you only want to implement a subset
     * of the listener methods you can extend this instead of implement the full interface.
     */
    public static class SimplePanelSlideListener implements PanelSlideListener {
        @Override
        public void onPanelSlide(View panel, float slideOffset) {
        }

        @Override
        public void onPanelCollapsed(View panel) {
        }

        @Override
        public void onPanelExpanded(View panel) {
        }

        @Override
        public void onPanelAnchored(View panel) {
        }

        @Override
        public void onPanelHidden(View panel) {
        }

        @Override
        public void onPanelHiddenExecuted(View panel, Interpolator interpolator, int duration) {
        }

        @Override
        public void onPanelShownExecuted(View panel, Interpolator interpolator, int duration) {
        }

        @Override
        public void onTopReached(View panel, boolean reached) {
        }

        @Override
        public void onBottomReached(View panel, boolean reached) {
        }

        @Override
        public void onPanelLayout(View panel, PanelState state) {
        }
    }

    public SlidingUpPanelLayout(Context context) {
        this(context, null);
    }

    public SlidingUpPanelLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SlidingUpPanelLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (isInEditMode()) {
            shadowDrawable = null;
            dragHelper = null;
            return;
        }

        Interpolator scrollerInterpolator = null;
        if (attrs != null) {
            TypedArray defAttrs = context.obtainStyledAttributes(attrs, DEFAULT_ATTRS);

            if (defAttrs != null) {
                int gravity = defAttrs.getInt(0, Gravity.NO_GRAVITY);
                setGravity(gravity);
            }

            defAttrs.recycle();

            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.SlidingUpPanelLayout);

            if (ta != null) {
                panelHeight = ta.getDimensionPixelSize(R.styleable.SlidingUpPanelLayout_umanoPanelHeight, -1);
                shadowHeight = ta.getDimensionPixelSize(R.styleable.SlidingUpPanelLayout_umanoShadowHeight, -1);
                parallaxOffset = ta.getDimensionPixelSize(R.styleable.SlidingUpPanelLayout_umanoParallaxOffset, -1);

                mMinFlingVelocity = ta.getInt(R.styleable.SlidingUpPanelLayout_umanoFlingVelocity, DEFAULT_MIN_FLING_VELOCITY);
                mCoveredFadeColor = ta.getColor(R.styleable.SlidingUpPanelLayout_umanoFadeColor, DEFAULT_FADE_COLOR);

                dragViewResId = ta.getResourceId(R.styleable.SlidingUpPanelLayout_umanoDragView, -1);
                scrollableViewResId = ta.getResourceId(R.styleable.SlidingUpPanelLayout_umanoScrollableView, -1);

                overlayContent = ta.getBoolean(R.styleable.SlidingUpPanelLayout_umanoOverlay, DEFAULT_OVERLAY_FLAG);
                clipPanel = ta.getBoolean(R.styleable.SlidingUpPanelLayout_umanoClipPanel, DEFAULT_CLIP_PANEL_FLAG);

                anchorPoint = ta.getFloat(R.styleable.SlidingUpPanelLayout_umanoAnchorPoint, DEFAULT_ANCHOR_POINT);

                slideState = PanelState.values()[ta.getInt(R.styleable.SlidingUpPanelLayout_umanoInitialState, DEFAULT_SLIDE_STATE.ordinal())];

                int interpolatorResId = ta.getResourceId(R.styleable.SlidingUpPanelLayout_umanoScrollInterpolator, -1);
                if (interpolatorResId != -1) {
                    scrollerInterpolator = AnimationUtils.loadInterpolator(context, interpolatorResId);
                }
            }

            ta.recycle();
        }

        final float density = context.getResources().getDisplayMetrics().density;
        if (panelHeight == -1) {
            panelHeight = (int) (DEFAULT_PANEL_HEIGHT * density + 0.5f);
        }
        if (shadowHeight == -1) {
            shadowHeight = (int) (DEFAULT_SHADOW_HEIGHT * density + 0.5f);
        }
        if (parallaxOffset == -1) {
            parallaxOffset = (int) (DEFAULT_PARALLAX_OFFSET * density);
        }
        // If the shadow height is zero, don't show the shadow
        if (shadowHeight > 0) {
            if (isSlidingUp) {
                shadowDrawable = getResources().getDrawable(R.drawable.above_shadow);
            } else {
                shadowDrawable = getResources().getDrawable(R.drawable.below_shadow);
            }
        } else {
            shadowDrawable = null;
        }

        setWillNotDraw(false);

        dragHelper = ViewDragHelper.create(this, 0.5f, scrollerInterpolator, new DragHelperCallback());
        dragHelper.setMinVelocity(mMinFlingVelocity * density);

        isTouchEnabled = true;
    }

    /**
     * Set the Drag View after the view is inflated
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (dragViewResId != -1) {
            setDragView(findViewById(dragViewResId));
        }
        if (scrollableViewResId != -1) {
            setScrollableView(findViewById(scrollableViewResId));
        }
    }

    public void setGravity(int gravity) {
        if (gravity != Gravity.TOP && gravity != Gravity.BOTTOM) {
            throw new IllegalArgumentException("gravity must be set to either top or bottom");
        }
        isSlidingUp = gravity == Gravity.BOTTOM;
        if (!firstLayout) {
            requestLayout();
        }
    }

    /**
     * Set the color used to fade the pane covered by the sliding pane out when the pane
     * will become fully covered in the expanded state.
     *
     * @param color An ARGB-packed color value
     */
    public void setCoveredFadeColor(int color) {
        mCoveredFadeColor = color;
        invalidate();
    }

    /**
     * @return The ARGB-packed color value used to fade the fixed pane
     */
    public int getCoveredFadeColor() {
        return mCoveredFadeColor;
    }

    /**
     * Set sliding enabled flag
     *
     * @param enabled flag value
     */
    public void setTouchEnabled(boolean enabled) {
        isTouchEnabled = enabled;
    }

    public boolean isTouchEnabled() {
        return isTouchEnabled && slideableView != null && slideState != PanelState.HIDDEN;
    }

    /**
     * Set the collapsed panel height in pixels
     *
     * @param val A height in pixels
     */
    public void setPanelHeight(int val) {
        if (getPanelHeight() == val) {
            return;
        }

        panelHeight = val;
        if (!firstLayout) {
            // Request layout on FABLayout if necessary
            if (getParent() != null && getParent() instanceof FloatingActionButtonLayout) {
                FloatingActionButtonLayout floatingActionButtonLayout = (FloatingActionButtonLayout) getParent();
                floatingActionButtonLayout.mFirstLayout = true;
                floatingActionButtonLayout.requestLayout();
            }
            requestLayout();
        }

        if (getPanelState() == PanelState.COLLAPSED) {
            smoothToBottom();
            invalidate();
            return;
        }
    }

    protected void smoothToBottom() {
        smoothSlideTo(0, 0);
    }

    /**
     * @return The current shadow height
     */
    public int getShadowHeight() {
        return shadowHeight;
    }

    /**
     * Set the shadow height
     *
     * @param val A height in pixels
     */
    public void setShadowHeight(int val) {
        shadowHeight = val;
        if (!firstLayout) {
            invalidate();
        }
    }

    /**
     * @return The current collapsed panel height
     */
    public int getPanelHeight() {
        return panelHeight;
    }

    /**
     * @return The current parallax offset
     */
    public int getCurrentParallaxOffset() {
        // Clamp slide offset at zero for parallax computation;
        int offset = (int) (parallaxOffset * Math.max(slideOffset, 0));
        return isSlidingUp ? -offset : offset;
    }

    /**
     * Set parallax offset for the panel
     *
     * @param val A height in pixels
     */
    public void setParallaxOffset(int val) {
        parallaxOffset = val;
        if (!firstLayout) {
            requestLayout();
        }
    }

    /**
     * @return The current minimin fling velocity
     */
    public int getMinFlingVelocity() {
        return mMinFlingVelocity;
    }

    /**
     * Sets the minimum fling velocity for the panel
     *
     * @param val the new value
     */
    public void setMinFlingVelocity(int val) {
        mMinFlingVelocity = val;
    }

    /**
     * Sets the panel slide listener
     *
     * @param listener
     */
    public void setPanelSlideListener(PanelSlideListener listener) {
        panelSlideListener = listener;
    }

    /**
     * Set the draggable view portion. Use to null, to allow the whole panel to be draggable
     *
     * @param dragView A view that will be used to drag the panel.
     */
    public void setDragView(View dragView) {
        if (this.dragView != null) {
            this.dragView.setOnClickListener(null);
        }
        this.dragView = dragView;
        if (this.dragView != null) {
            this.dragView.setClickable(true);
            this.dragView.setFocusable(false);
            this.dragView.setFocusableInTouchMode(false);
            this.dragView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isEnabled() || !isTouchEnabled()) return;
                    if (slideState != PanelState.EXPANDED && slideState != PanelState.ANCHORED) {
                        if (anchorPoint < 1.0f) {
                            setPanelState(PanelState.ANCHORED);
                        } else {
                            setPanelState(PanelState.EXPANDED);
                        }
                    } else {
                        setPanelState(PanelState.COLLAPSED);
                    }
                }
            });
            ;
        }
    }

    public void attachFloatingActionButton(View v, int initialY, int collapsedY, int expandedY, int expandedYSpace, FloatingActionButtonLayout.FabMode fabMode) {
        floatingActionButton = v;
        dragHelper.setFabMode(fabMode);
        dragHelper.setFloatingActionButton(floatingActionButton);
        dragHelper.setFabHideDeltaY(initialY - collapsedY);
        dragHelper.setFabCollapsedY(collapsedY);
        dragHelper.setFabExpandedY(expandedY);
        dragHelper.setFabExpandedYSpace(expandedYSpace);
    }

    public void setFloatingActionButtonAttached(boolean attached) {
        dragHelper.setFabAttached(attached);
    }

    public void setFloatingActionButtonVisibility(int visibility) {
        dragHelper.setFabVisibility(visibility);
    }

    public int getFloatingActionButtonVisibility() {
        return dragHelper.getFabVisibility();
    }

    /**
     * Set the draggable view portion. Use to null, to allow the whole panel to be draggable
     *
     * @param dragViewResId The resource ID of the new drag view
     */
    public void setDragView(int dragViewResId) {
        this.dragViewResId = dragViewResId;
        setDragView(findViewById(dragViewResId));
    }

    /**
     * Set the scrollable child of the sliding layout. If set, scrolling will be transfered between
     * the panel and the view when necessary
     *
     * @param scrollableView The scrollable view
     */
    public void setScrollableView(View scrollableView) {
        this.scrollableView = scrollableView;
    }

    /**
     * Sets the current scrollable view helper. See ScrollableViewHelper description for details.
     * @param helper
     */
    public void setScrollableViewHelper(ScrollableViewHelper helper) {
        scrollableViewHelper = helper;
    }

    /**
     * Set an anchor point where the panel can stop during sliding
     *
     * @param anchorPoint A value between 0 and 1, determining the position of the anchor point
     *                    starting from the top of the layout.
     */
    public void setAnchorPoint(float anchorPoint) {
        if (anchorPoint > 0 && anchorPoint <= 1) {
            this.anchorPoint = anchorPoint;
            if (this.anchorPoint != DEFAULT_ANCHOR_POINT){
                dragHelper.setAnchorY(computePanelTopPosition(this.anchorPoint));
                int anchorTop = computePanelTopPosition(this.anchorPoint);
                dragHelper.setFabRatio(((float) anchorTop - panelHeight) / ((float) anchorTop));
            } else{
                dragHelper.setAnchorY(-1);
                dragHelper.setFabRatio(((float) slideRange - panelHeight) / ((float) slideRange));
            }
        }
    }

    /**
     * Gets the currently set anchor point
     *
     * @return the currently set anchor point
     */
    public float getAnchorPoint() {
        return anchorPoint;
    }

    /**
     * Sets whether or not the panel overlays the content
     *
     * @param overlayed
     */
    public void setOverlayed(boolean overlayed) {
        overlayContent = overlayed;
    }

    /**
     * Check if the panel is set as an overlay.
     */
    public boolean isOverlayed() {
        return overlayContent;
    }

    /**
     * Sets whether or not the main content is clipped to the top of the panel
     *
     * @param clip
     */
    public void setClipPanel(boolean clip) {
        clipPanel = clip;
    }

    /**
     * Check whether or not the main content is clipped to the top of the panel
     */
    public boolean isClipPanel() {
        return clipPanel;
    }

    void dispatchOnPanelSlide(View panel) {
        if (panelSlideListener != null) {
            panelSlideListener.onPanelSlide(panel, slideOffset);
        }
    }

    void dispatchOnPanelExpanded(View panel) {
        if (panelSlideListener != null) {
            panelSlideListener.onPanelExpanded(panel);
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    void dispatchOnPanelCollapsed(View panel) {
        if (panelSlideListener != null) {
            panelSlideListener.onPanelCollapsed(panel);
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    void dispatchOnPanelAnchored(View panel) {
        if (panelSlideListener != null) {
            panelSlideListener.onPanelAnchored(panel);
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    void dispatchOnPanelHidden(View panel) {
        if (panelSlideListener != null) {
            panelSlideListener.onPanelHidden(panel);
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    void dispatchOnPanelHiddenExecuted(View panel, Interpolator interpolator, int duration) {
        if (panelSlideListener != null) {
            panelSlideListener.onPanelHiddenExecuted(panel, interpolator, duration);
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    void dispatchOnPanelShownExecuted(View panel, Interpolator interpolator, int duration) {
        if (panelSlideListener != null) {
            panelSlideListener.onPanelShownExecuted(panel, interpolator, duration);
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    void dispatchOnPanelExpandedStateY(View panel, boolean reached) {
        if (panelSlideListener != null) {
            panelSlideListener.onTopReached(panel, reached);
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    void dispatchOnPanelCollapsedStateY(View panel, boolean reached) {
        if (panelSlideListener != null) {
            panelSlideListener.onBottomReached(panel, reached);
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    void dispatchOnPanelLayout(View panel, PanelState state) {
        if (panelSlideListener != null) {
            panelSlideListener.onPanelLayout(panel, state);
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    void updateObscuredViewVisibility() {
        if (getChildCount() == 0) {
            return;
        }
        final int leftBound = getPaddingLeft();
        final int rightBound = getWidth() - getPaddingRight();
        final int topBound = getPaddingTop();
        final int bottomBound = getHeight() - getPaddingBottom();
        final int left;
        final int right;
        final int top;
        final int bottom;
        if (slideableView != null && hasOpaqueBackground(slideableView)) {
            left = slideableView.getLeft();
            right = slideableView.getRight();
            top = slideableView.getTop();
            bottom = slideableView.getBottom();
        } else {
            left = right = top = bottom = 0;
        }
        View child = getChildAt(0);
        final int clampedChildLeft = Math.max(leftBound, child.getLeft());
        final int clampedChildTop = Math.max(topBound, child.getTop());
        final int clampedChildRight = Math.min(rightBound, child.getRight());
        final int clampedChildBottom = Math.min(bottomBound, child.getBottom());
        final int vis;
        if (clampedChildLeft >= left && clampedChildTop >= top &&
                clampedChildRight <= right && clampedChildBottom <= bottom) {
            vis = INVISIBLE;
        } else {
            vis = VISIBLE;
        }
        child.setVisibility(vis);
    }

    void setAllChildrenVisible() {
        for (int i = 0, childCount = getChildCount(); i < childCount; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == INVISIBLE) {
                child.setVisibility(VISIBLE);
            }
        }
    }

    private static boolean hasOpaqueBackground(View v) {
        final Drawable bg = v.getBackground();
        return bg != null && bg.getOpacity() == PixelFormat.OPAQUE;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        firstLayout = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        firstLayout = true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("Width must have an exact value or MATCH_PARENT");
        } else if (heightMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("Height must have an exact value or MATCH_PARENT");
        }

        final int childCount = getChildCount();

        if (childCount != 2) {
            throw new IllegalStateException("Sliding up panel layout must have exactly 2 children!");
        }

        mainView = getChildAt(0);
        slideableView = getChildAt(1);
        if (dragView == null) {
            setDragView(slideableView);
        }

        // If the sliding panel is not visible, then put the whole view in the hidden state
        if (slideableView.getVisibility() != VISIBLE) {
            slideState = PanelState.HIDDEN;
        }

        int layoutHeight = heightSize - getPaddingTop() - getPaddingBottom();
        int layoutWidth = widthSize - getPaddingLeft() - getPaddingRight();

        // First pass. Measure based on child LayoutParams width/height.
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            // We always measure the sliding panel in order to know it's height (needed for show panel)
            if (child.getVisibility() == GONE && i == 0) {
                continue;
            }

            int height = layoutHeight;
            int width = layoutWidth;
            if (child == mainView) {
                if (!overlayContent && slideState != PanelState.HIDDEN) {
                    height -= panelHeight;
                }

                width -= lp.leftMargin + lp.rightMargin;
            } else if (child == slideableView) {
                // The slideable view should be aware of its top margin.
                // See https://github.com/umano/AndroidSlidingUpPanel/issues/412.
                height -= lp.topMargin;
            }

            int childWidthSpec;
            if (lp.width == LayoutParams.WRAP_CONTENT) {
                childWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST);
            } else if (lp.width == LayoutParams.MATCH_PARENT) {
                childWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            } else {
                childWidthSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
            }

            int childHeightSpec;
            if (lp.height == LayoutParams.WRAP_CONTENT) {
                childHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST);
            } else {
                // Modify the height based on the weight.
                if (lp.weight > 0 && lp.weight < 1) {
                    height = (int) (height * lp.weight);
                } else if (lp.height != LayoutParams.MATCH_PARENT) {
                    height = lp.height;
                }
                childHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
            }

            child.measure(childWidthSpec, childHeightSpec);

            if (child == slideableView) {
                slideRange = slideableView.getMeasuredHeight() - panelHeight;
                if(anchorPoint == DEFAULT_ANCHOR_POINT) {
                    dragHelper.setAnchorY(-1);
                    dragHelper.setFabRatio(((float) slideRange - panelHeight) / ((float) slideRange));
                } else {
                    int anchorTop = computePanelTopPosition(anchorPoint);
                    dragHelper.setAnchorY(anchorTop);
                    dragHelper.setFabRatio(((float) anchorTop - panelHeight) / ((float) anchorTop));
                }
                dragHelper.setPanelCharacteristics(slideRange, panelHeight);
            }
        }

        setMeasuredDimension(widthSize, heightSize);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int paddingLeft = getPaddingLeft();
        final int paddingTop = getPaddingTop();

        final int childCount = getChildCount();

        if (firstLayout) {
            switch (slideState) {
                case EXPANDED:
                    slideOffset = 1.0f;
                    alreadyExpandedStateY = true;
                    break;
                case ANCHORED:
                    slideOffset = anchorPoint;
                    break;
                case HIDDEN:
                    int newTop = computePanelTopPosition(0.0f) + (isSlidingUp ? +panelHeight : -panelHeight);
                    slideOffset = computeSlideOffset(newTop);
                    alreadyCollapsedStateY = true;
                    break;
                default:
                    slideOffset = 0.f;
                    alreadyCollapsedStateY = true;
                    break;
            }
            dispatchOnPanelLayout(slideableView, slideState);
        }

        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            // Always layout the sliding view on the first layout
            if (child.getVisibility() == GONE && (i == 0 || firstLayout)) {
                continue;
            }

            final int childHeight = child.getMeasuredHeight();
            int childTop = paddingTop;

            if (child == slideableView) {
                childTop = computePanelTopPosition(slideOffset);
            }

            if (!isSlidingUp) {
                if (child == mainView && !overlayContent) {
                    childTop = computePanelTopPosition(slideOffset) + slideableView.getMeasuredHeight();
                }
            }
            final int childBottom = childTop + childHeight;
            final int childLeft = paddingLeft + lp.leftMargin;
            final int childRight = childLeft + child.getMeasuredWidth();

            child.layout(childLeft, childTop, childRight, childBottom);
        }

        if (firstLayout) {
            updateObscuredViewVisibility();
        }
        applyParallaxForCurrentSlideOffset();

        firstLayout = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Recalculate sliding panes and their details
        if (h != oldh) {
            firstLayout = true;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // If the scrollable view is handling touch, never intercept
        if (isScrollableViewHandlingTouch) {
            dragHelper.cancel();
            return false;
        }

        final int action = MotionEventCompat.getActionMasked(ev);
        final float x = ev.getX();
        final float y = ev.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                isUnableToDrag = false;
                initialMotionX = x;
                initialMotionY = y;
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                final float adx = Math.abs(x - initialMotionX);
                final float ady = Math.abs(y - initialMotionY);
                final int dragSlop = dragHelper.getTouchSlop();

                if ((ady > dragSlop && adx > ady) || !isViewUnder(dragView, (int) initialMotionX, (int) initialMotionY)) {
                    dragHelper.cancel();
                    isUnableToDrag = true;
                    return false;
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                // If the dragView is still dragging when we get here, we need to call processTouchEvent
                // so that the view is settled
                // Added to make scrollable views work (tokudu)
                if (dragHelper.isDragging()) {
                    dragHelper.processTouchEvent(ev);
                    return true;
                }
                break;
        }
        return dragHelper.shouldInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent ev) {
        if (!isEnabled() || !isTouchEnabled()) {
            return super.onTouchEvent(ev);
        }
        try {
            dragHelper.processTouchEvent(ev);
            return true;
        } catch (Exception ex) {
            // Ignore the pointer out of range exception
            return false;
        }
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
        final int action = MotionEventCompat.getActionMasked(ev);

        if (!isEnabled() || !isTouchEnabled() || (isUnableToDrag && action != MotionEvent.ACTION_DOWN)) {
            dragHelper.cancel();
            return super.dispatchTouchEvent(ev);
        }

        final float y = ev.getY();

        if (action == MotionEvent.ACTION_DOWN) {
            isScrollableViewHandlingTouch = false;
            prevMotionY = y;
        } else if (action == MotionEvent.ACTION_MOVE) {
            float dy = y - prevMotionY;
            prevMotionY = y;

            // If the scroll view isn't under the touch, pass the
            // event along to the dragView.
            if (!isViewUnder(scrollableView, (int) initialMotionX, (int) initialMotionY)) {
                return super.dispatchTouchEvent(ev);
            }

            // Which direction (up or down) is the drag moving?
            if (dy * (isSlidingUp ? 1 : -1) > 0) { // Collapsing
                // Is the child less than fully scrolled?
                // Then let the child handle it.
                if (scrollableViewHelper.getScrollableViewScrollPosition(scrollableView, isSlidingUp) > 0) {
                    isScrollableViewHandlingTouch = true;
                    return super.dispatchTouchEvent(ev);
                }

                // Was the child handling the touch previously?
                // Then we need to rejigger things so that the
                // drag panel gets a proper down event.
                if (isScrollableViewHandlingTouch) {
                    // Send an 'UP' event to the child.
                    MotionEvent up = MotionEvent.obtain(ev);
                    up.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(up);
                    up.recycle();

                    // Send a 'DOWN' event to the panel. (We'll cheat
                    // and hijack this one)
                    ev.setAction(MotionEvent.ACTION_DOWN);
                }

                isScrollableViewHandlingTouch = false;
                return this.onTouchEvent(ev);
            } else if (dy * (isSlidingUp ? 1 : -1) < 0) { // Expanding
                // Is the panel less than fully expanded?
                // Then we'll handle the drag here.
                if (slideOffset < 1.0f) {
                    isScrollableViewHandlingTouch = false;
                    return this.onTouchEvent(ev);
                }

                // Was the panel handling the touch previously?
                // Then we need to rejigger things so that the
                // child gets a proper down event.
                if (!isScrollableViewHandlingTouch && dragHelper.isDragging()) {
                    dragHelper.cancel();
                    ev.setAction(MotionEvent.ACTION_DOWN);
                }

                isScrollableViewHandlingTouch = true;
                return super.dispatchTouchEvent(ev);
            }
        } else if (action == MotionEvent.ACTION_UP && isScrollableViewHandlingTouch) {
            // If the scrollable view was handling the touch and we receive an up
            // we want to clear any previous dragging state so we don't intercept a touch stream accidentally
            dragHelper.setDragState(ViewDragHelper.STATE_IDLE);
        }

        // In all other cases, just let the default behavior take over.
        return super.dispatchTouchEvent(ev);
    }

    private boolean isViewUnder(View view, int x, int y) {
        if (view == null) return false;
        int[] viewLocation = new int[2];
        view.getLocationOnScreen(viewLocation);
        int[] parentLocation = new int[2];
        this.getLocationOnScreen(parentLocation);
        int screenX = parentLocation[0] + x;
        int screenY = parentLocation[1] + y;
        return screenX >= viewLocation[0] && screenX < viewLocation[0] + view.getWidth() &&
                screenY >= viewLocation[1] && screenY < viewLocation[1] + view.getHeight();
    }

    /*
     * Computes the top position of the panel based on the slide offset.
     */
    private int computePanelTopPosition(float slideOffset) {
        int slidingViewHeight = slideableView != null ? slideableView.getMeasuredHeight() : 0;
        int slidePixelOffset = (int) (slideOffset * slideRange);
        // Compute the top of the panel if its collapsed
        return isSlidingUp
                ? getMeasuredHeight() - getPaddingBottom() - panelHeight - slidePixelOffset
                : getPaddingTop() - slidingViewHeight + panelHeight + slidePixelOffset;
    }

    /*
     * Computes the slide offset based on the top position of the panel
     */
    private float computeSlideOffset(int topPosition) {
        // Compute the panel top position if the panel is collapsed (offset 0)
        final int topBoundCollapsed = computePanelTopPosition(0);

        // Determine the new slide offset based on the collapsed top position and the new required
        // top position
        return (isSlidingUp
                ? (float) (topBoundCollapsed - topPosition) / slideRange
                : (float) (topPosition - topBoundCollapsed) / slideRange);
    }

    /**
     * Returns the current state of the panel as an enum.
     *
     * @return the current panel state
     */
    public PanelState getPanelState() {
        return slideState;
    }

    /**
     * Change panel state to the given state with
     *
     * @param state - new panel state
     */
    public void setPanelState(PanelState state) {
        if (state == null || state == PanelState.DRAGGING) {
            throw new IllegalArgumentException("Panel state cannot be null or DRAGGING.");
        }
        if (!isEnabled()
                || (!firstLayout && slideableView == null)
                || state == slideState
                || slideState == PanelState.DRAGGING) return;

        if (firstLayout) {
            slideState = state;
        } else {
            if (slideState == PanelState.HIDDEN) {
                slideableView.setVisibility(View.VISIBLE);
                requestLayout();
            }
            switch (state) {
                case ANCHORED:
                    smoothSlideTo(anchorPoint, 0);
                    break;
                case COLLAPSED:
                    if (slideState == PanelState.HIDDEN){
                        dispatchOnPanelShownExecuted(slideableView, dragHelper.getInterpolator(), dragHelper.computeSettleDuration(slideableView, 0, panelHeight, 0, 0));
                    }
                    smoothSlideTo(0, 0);
                    break;
                case EXPANDED:
                    smoothSlideTo(1.0f, 0);
                    break;
                case HIDDEN:
                    int newTop = computePanelTopPosition(0.0f) + (isSlidingUp ? +panelHeight : -panelHeight);
                    if (slideState != PanelState.HIDDEN){
                        dispatchOnPanelHiddenExecuted(slideableView, dragHelper.getInterpolator(), dragHelper.computeSettleDuration(slideableView, 0, panelHeight, 0, 0));
                    }
                    smoothSlideTo(computeSlideOffset(newTop), 0);
                    break;
            }
        }
    }

    /**
     * Update the parallax based on the current slide offset.
     */
    @SuppressLint("NewApi")
    private void applyParallaxForCurrentSlideOffset() {
        if (parallaxOffset > 0) {
            int mainViewOffset = getCurrentParallaxOffset();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                mainView.setTranslationY(mainViewOffset);
            } else {
                AnimatorProxy.wrap(mainView).setTranslationY(mainViewOffset);
            }
        }
    }

    private void onPanelDragged(int newTop) {
        lastNotDraggingSlideState = slideState;
        slideState = PanelState.DRAGGING;
        // Recompute the slide offset based on the new top position
        slideOffset = computeSlideOffset(newTop);
        applyParallaxForCurrentSlideOffset();
        // Dispatch the slide event
        dispatchOnPanelSlide(slideableView);
        // If the slide offset is negative, and overlay is not on, we need to increase the
        // height of the main content
        LayoutParams lp = (LayoutParams) mainView.getLayoutParams();
        int defaultHeight = getHeight() - getPaddingBottom() - getPaddingTop() - panelHeight;

        if (slideOffset <= 0 && !overlayContent) {
            // expand the main view
            lp.height = isSlidingUp ? (newTop - getPaddingBottom()) : (getHeight() - getPaddingBottom() - slideableView.getMeasuredHeight() - newTop);
            if (lp.height == defaultHeight) {
                lp.height = LayoutParams.MATCH_PARENT;
            }
            mainView.requestLayout();
        } else if (lp.height != LayoutParams.MATCH_PARENT && !overlayContent) {
            lp.height = LayoutParams.MATCH_PARENT;
            mainView.requestLayout();
        }
        if (isSlidingUp) {
            final int collapsedTop = computePanelTopPosition(0.f);
            final int expandedTop = computePanelTopPosition(1.0f);
            if (newTop > expandedTop) {
                if (alreadyExpandedStateY) {
                    dispatchOnPanelExpandedStateY(slideableView, false);
                    alreadyExpandedStateY = false;
                }
            } else {
                if (!alreadyExpandedStateY) {
                    dispatchOnPanelExpandedStateY(slideableView, true);
                    alreadyExpandedStateY = true;
                }
            }
            if (newTop < collapsedTop) {
                if (alreadyCollapsedStateY) {
                    dispatchOnPanelCollapsedStateY(slideableView, false);
                    alreadyCollapsedStateY = false;
                }
            } else {
                if (!alreadyCollapsedStateY) {
                    dispatchOnPanelCollapsedStateY(slideableView, true);
                    alreadyCollapsedStateY = true;
                }
            }
        } else {
            final int collapsedTop = computePanelTopPosition(1.0f);
            final int expandedTop = computePanelTopPosition(0.f);
            if (newTop < expandedTop) {
                if (alreadyExpandedStateY) {
                    dispatchOnPanelExpandedStateY(slideableView, false);
                    alreadyExpandedStateY = false;
                }
            } else {
                if (!alreadyExpandedStateY) {
                    dispatchOnPanelExpandedStateY(slideableView, true);
                    alreadyExpandedStateY = true;
                }
            }
            if (newTop > collapsedTop) {
                if (alreadyCollapsedStateY) {
                    dispatchOnPanelCollapsedStateY(slideableView, false);
                    alreadyCollapsedStateY = false;
                }
            } else {
                if (!alreadyCollapsedStateY) {
                    dispatchOnPanelCollapsedStateY(slideableView, true);
                    alreadyCollapsedStateY = true;
                }
            }
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result;
        final int save = canvas.save(Canvas.CLIP_SAVE_FLAG);

        if (slideableView != child) { // if main view
            // Clip against the slider; no sense drawing what will immediately be covered,
            // Unless the panel is set to overlay content
            canvas.getClipBounds(tmpRect);
            if (!overlayContent) {
                if (isSlidingUp) {
                    tmpRect.bottom = Math.min(tmpRect.bottom, slideableView.getTop());
                } else {
                    tmpRect.top = Math.max(tmpRect.top, slideableView.getBottom());
                }
            }
            if (clipPanel) {
                canvas.clipRect(tmpRect);
            }

            result = super.drawChild(canvas, child, drawingTime);

            if (mCoveredFadeColor != 0 && slideOffset > 0) {
                final int baseAlpha = (mCoveredFadeColor & 0xff000000) >>> 24;
                final int imag = (int) (baseAlpha * slideOffset);
                final int color = imag << 24 | (mCoveredFadeColor & 0xffffff);
                coveredFadePaint.setColor(color);
                canvas.drawRect(tmpRect, coveredFadePaint);
            }
        } else {
            result = super.drawChild(canvas, child, drawingTime);
        }

        canvas.restoreToCount(save);

        return result;
    }

    /**
     * Smoothly animate mDraggingPane to the target X position within its range.
     *
     * @param slideOffset position to animate to
     * @param velocity    initial velocity in case of fling, or 0.
     */
    boolean smoothSlideTo(float slideOffset, int velocity) {
        if (!isEnabled() || slideableView == null) {
            // Nothing to do.
            return false;
        }

        int panelTop = computePanelTopPosition(slideOffset);
        if (dragHelper.smoothSlideViewTo(slideableView, slideableView.getLeft(), panelTop)) {
            setAllChildrenVisible();
            ViewCompat.postInvalidateOnAnimation(this);
            return true;
        }
        return false;
    }

    @Override
    public void computeScroll() {
        if (dragHelper != null && dragHelper.continueSettling(true)) {
            if (!isEnabled()) {
                dragHelper.abort();
                return;
            }

            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    @Override
    public void draw(Canvas c) {
        super.draw(c);

        // draw the shadow
        if (shadowDrawable != null) {
            final int right = slideableView.getRight();
            final int top;
            final int bottom;
            if (isSlidingUp) {
                top = slideableView.getTop() - shadowHeight;
                bottom = slideableView.getTop();
            } else {
                top = slideableView.getBottom();
                bottom = slideableView.getBottom() + shadowHeight;
            }
            final int left = slideableView.getLeft();
            shadowDrawable.setBounds(left, top, right, bottom);
            shadowDrawable.draw(c);
        }
    }

    /**
     * Tests scrollability within child views of v given a delta of dx.
     *
     * @param v      View to test for horizontal scrollability
     * @param checkV Whether the view v passed should itself be checked for scrollability (true),
     *               or just its children (false).
     * @param dx     Delta scrolled in pixels
     * @param x      X coordinate of the active touch point
     * @param y      Y coordinate of the active touch point
     * @return true if child views of v can be scrolled by delta of dx.
     */
    protected boolean canScroll(View v, boolean checkV, int dx, int x, int y) {
        if (v instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) v;
            final int scrollX = v.getScrollX();
            final int scrollY = v.getScrollY();
            final int count = group.getChildCount();
            // Count backwards - let topmost views consume scroll distance first.
            for (int i = count - 1; i >= 0; i--) {
                final View child = group.getChildAt(i);
                if (x + scrollX >= child.getLeft() && x + scrollX < child.getRight() &&
                        y + scrollY >= child.getTop() && y + scrollY < child.getBottom() &&
                        canScroll(child, true, dx, x + scrollX - child.getLeft(),
                                y + scrollY - child.getTop())) {
                    return true;
                }
            }
        }
        return checkV && ViewCompat.canScrollHorizontally(v, -dx);
    }


    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof MarginLayoutParams
                ? new LayoutParams((MarginLayoutParams) p)
                : new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams && super.checkLayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        SavedState ss = new SavedState(superState);
        if (slideState != PanelState.DRAGGING) {
            ss.mSlideState = slideState;
        } else {
            ss.mSlideState = lastNotDraggingSlideState;
        }
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        slideState = ss.mSlideState != null ? ss.mSlideState : DEFAULT_SLIDE_STATE;
    }

    private class DragHelperCallback extends ViewDragHelper.Callback {

        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            if (isUnableToDrag) {
                return false;
            }

            return child == slideableView;
        }

        @Override
        public void onViewDragStateChanged(int state) {
            if (dragHelper.getViewDragState() == ViewDragHelper.STATE_IDLE) {
                slideOffset = computeSlideOffset(slideableView.getTop());
                applyParallaxForCurrentSlideOffset();

                if (slideOffset == 1) {
                    if (slideState != PanelState.EXPANDED) {
                        updateObscuredViewVisibility();
                        slideState = PanelState.EXPANDED;
                        dispatchOnPanelExpanded(slideableView);
                    }
                } else if (slideOffset == 0) {
                    if (slideState != PanelState.COLLAPSED) {
                        slideState = PanelState.COLLAPSED;
                        dispatchOnPanelCollapsed(slideableView);
                    }
                } else if (slideOffset < 0) {
                    slideState = PanelState.HIDDEN;
                    slideableView.setVisibility(View.INVISIBLE);
                    dispatchOnPanelHidden(slideableView);
                } else if (slideState != PanelState.ANCHORED) {
                    updateObscuredViewVisibility();
                    slideState = PanelState.ANCHORED;
                    dispatchOnPanelAnchored(slideableView);
                }
            }
        }

        @Override
        public void onViewCaptured(View capturedChild, int activePointerId) {
            setAllChildrenVisible();
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            onPanelDragged(top);
            invalidate();
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            int target = 0;

            // direction is always positive if we are sliding in the expanded direction
            float direction = isSlidingUp ? -yvel : yvel;

            if (direction > 0 && slideOffset <= anchorPoint) {
                // swipe up -> expand and stop at anchor point
                target = computePanelTopPosition(anchorPoint);
            } else if (direction > 0 && slideOffset > anchorPoint) {
                // swipe up past anchor -> expand
                target = computePanelTopPosition(1.0f);
            } else if (direction < 0 && slideOffset >= anchorPoint) {
                // swipe down -> collapse and stop at anchor point
                target = computePanelTopPosition(anchorPoint);
            } else if (direction < 0 && slideOffset < anchorPoint) {
                // swipe down past anchor -> collapse
                target = computePanelTopPosition(0.0f);
            } else if (slideOffset >= (1.f + anchorPoint) / 2) {
                // zero velocity, and far enough from anchor point => expand to the top
                target = computePanelTopPosition(1.0f);
            } else if (slideOffset >= anchorPoint / 2) {
                // zero velocity, and close enough to anchor point => go to anchor
                target = computePanelTopPosition(anchorPoint);
            } else {
                // settle at the bottom
                target = computePanelTopPosition(0.0f);
            }

            dragHelper.settleCapturedViewAt(releasedChild.getLeft(), target);
            invalidate();
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return slideRange;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            final int collapsedTop = computePanelTopPosition(0.f);
            final int expandedTop = computePanelTopPosition(1.0f);
            if (isSlidingUp) {
                return Math.min(Math.max(top, expandedTop), collapsedTop);
            } else {
                return Math.min(Math.max(top, collapsedTop), expandedTop);
            }
        }
    }

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        private static final int[] ATTRS = new int[]{
                android.R.attr.layout_weight
        };

        public float weight = 0;

        public LayoutParams() {
            super(MATCH_PARENT, MATCH_PARENT);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(int width, int height, float weight) {
            super(width, height);
            this.weight = weight;
        }

        public LayoutParams(android.view.ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(LayoutParams source) {
            super(source);
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            final TypedArray ta = c.obtainStyledAttributes(attrs, ATTRS);
            if (ta != null) {
                this.weight = ta.getFloat(0, 0);
            }

            ta.recycle();
        }
    }

    static class SavedState extends BaseSavedState {
        PanelState mSlideState;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            String panelStateString = in.readString();
            try {
                mSlideState = panelStateString != null ? Enum.valueOf(PanelState.class, panelStateString)
                        : PanelState.COLLAPSED;
            } catch (IllegalArgumentException e) {
                mSlideState = PanelState.COLLAPSED;
            }
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeString(mSlideState == null ? null : mSlideState.toString());
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}
