package com.rey.material.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.AppCompatButton;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.rey.material.app.ThemeManager;
import com.rey.material.drawable.RippleDrawable;
import com.rey.material.util.ViewUtil;

public class Button extends AppCompatButton implements ThemeManager.OnThemeChangedListener {

    private RippleManager mRippleManager;

    /**
     * {@code ThemableView_v_styleId}的属性值，即设置控件的样式风格
     */
    protected int mStyleId;
    protected int mCurrentStyle = ThemeManager.THEME_UNDEFINED;

    public Button(Context context) {
        super(context);

        init(context, null, 0, 0);
    }

    public Button(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context, attrs, 0, 0);
    }

    public Button(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init(context, attrs, defStyleAttr, 0);
    }

    /**
     * 初始化，设置字体属性（tv_fontFamily属性值来设置），设置控件背景，获取
     * {@code ThemableView_v_styleId}的属性值
     */
    protected void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        ViewUtil.applyFont(this, attrs, defStyleAttr, defStyleRes);
        applyStyle(context, attrs, defStyleAttr, defStyleRes);
        if (!isInEditMode())
            mStyleId = ThemeManager.getStyleId(context, attrs, defStyleAttr, defStyleRes);
    }

    /**
     * 设置控件的各种基本属性（如间距、阴影等）
     *
     * @see ViewUtil#applyStyle(View, AttributeSet, int, int)
     */
    public void applyStyle(int resId) {
        ViewUtil.applyStyle(this, resId);
        applyStyle(getContext(), null, 0, resId);
    }

    /**
     * 这个只是用来设置控件背景，完整的控件属性设置还是得交给{@link #applyStyle(int)}
     */
    protected void applyStyle(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        getRippleManager().onCreate(this, context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void setTextAppearance(int resId) {
        ViewUtil.applyTextAppearance(this, resId);
    }

    @Override
    public void setTextAppearance(Context context, int resId) {
        ViewUtil.applyTextAppearance(this, resId);
    }

    @Override
    public void onThemeChanged(ThemeManager.OnThemeChangedEvent event) {
        int style = ThemeManager.getInstance().getCurrentStyle(mStyleId);
        if (mCurrentStyle != style) {
            mCurrentStyle = style;
            applyStyle(mCurrentStyle);
        }
    }

    /**
     * 当此{@link View}附加到窗体上时调用该方法，要保证该方法在{@link View#onDraw(Canvas)}之前调用，
     * 也就是说在{@link View}还没开始绘制的时候调用，可以在此方法中去执行一些初始化的操作
     * <p>
     * 值得注意的是，该方法可能在调用{@link View#onDraw(Canvas)}之前的任何时刻调用，包括可能在
     * {@link View#onMeasure(int, int)}之前调用，但也可能在其调用之后才进行调用
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mStyleId != 0) {
            ThemeManager.getInstance().registerOnThemeChangedListener(this);
            onThemeChanged(null);
        }
    }

    /**
     * 将视图从窗体上分离的时候调用该方法，这时视图已经不具有可绘制部分
     * <p>
     * {@link View#onDetachedFromWindow()}正好与{@link View#onAttachedToWindow()}的用法相反，
     * 在控件销毁的时候调用，所以可以加入取消广播注册等的操作
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        RippleManager.cancelRipple(this);
        if (mStyleId != 0)
            ThemeManager.getInstance().unregisterOnThemeChangedListener(this);
    }

    @Override
    public void setBackgroundDrawable(Drawable drawable) {
        Drawable background = getBackground();
        if (background instanceof RippleDrawable && !(drawable instanceof RippleDrawable))
            ((RippleDrawable) background).setBackgroundDrawable(drawable);
        else
            super.setBackgroundDrawable(drawable);
    }

    protected RippleManager getRippleManager() {
        if (mRippleManager == null) {
            synchronized (RippleManager.class) {
                if (mRippleManager == null)
                    mRippleManager = new RippleManager();
            }
        }

        return mRippleManager;
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        RippleManager rippleManager = getRippleManager();
        if (l == rippleManager)
            super.setOnClickListener(l);
        else {
            rippleManager.setOnClickListener(l);
            setOnClickListener(rippleManager);
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        boolean result = super.onTouchEvent(event);
        return getRippleManager().onTouchEvent(this, event) || result;
    }

}
