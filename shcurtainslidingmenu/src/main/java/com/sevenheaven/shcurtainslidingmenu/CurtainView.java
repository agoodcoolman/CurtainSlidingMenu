package com.sevenheaven.shcurtainslidingmenu;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateInterpolator;

public class CurtainView extends View {

    private Bitmap bitmap;
    private Bitmap shadowMask;
    private Paint paint;
    private Shader maskShader;
    private int maxAlpha = 0xFF;

    private int width, height;
    private int centerX, centerY;

    private int bitmapWidth = 40;
    private int bitmapHeight = 7;

    private int touchX;
    private int touchY;

    private final static int insDistance = 30;

    private boolean newApiFlag;

    private int delayOffsetX;

    private AccelerateInterpolator interpolator;

    public final static int DIRECTION_LEFT = 0;
    public final static int DIRECTION_RIGHT = 1;

    private int direction = DIRECTION_RIGHT;

    private Handler handler = new Handler();
    private Runnable delayRunnable = new Runnable() {
        @Override
        public void run() {
            delayOffsetX += (touchX - delayOffsetX) * 0.3F;

            handler.postDelayed(this, 20);
            invalidate();
        }
    };

    private float[] verts;
    private int[] colors;

    public CurtainView(Context context) {
        this(context, null);
    }

    public CurtainView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CurtainView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

//        bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.snipper);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        handler.post(delayRunnable);

        newApiFlag = Build.VERSION.SDK_INT >= 18;

        interpolator = new AccelerateInterpolator();

        verts = new float[(bitmapWidth + 1) * (bitmapHeight + 1) * 2];
        colors = new int[(bitmapWidth + 1) * (bitmapHeight + 1)];

        setupMask();

    }

    private void setupMask(){
        if (!newApiFlag && bitmap != null) {

            // 硬件加速不支持drawBitmapMesh的colors绘制的情况下,在原bitmap的上层覆盖一个半透明带阴影的bitmap以实现阴影功能
            //when API level lower than 18,the arguments of drawBitmapMesh method won't work when hardware accelerate is activated,
            //so we cover a transparent layer on the top of the origin bitmap to create a shadow effect

            shadowMask =
                    Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),
                            Bitmap.Config.ARGB_8888);

            Canvas maskCanvas = new Canvas(shadowMask);

            float singleWave = bitmap.getWidth() / bitmapWidth * 6.28F;
            int blockPerWave = (int) (singleWave / (bitmap.getWidth() / bitmapWidth));

            if (blockPerWave % 2 == 0)
                blockPerWave++;

            float offset =
                    (float) ((bitmap.getWidth() / singleWave - Math.floor(bitmap.getWidth()
                            / singleWave)) * singleWave) + singleWave / 2;

            int[] colors = new int[blockPerWave];
            float[] offsets = new float[blockPerWave];

            Log.d("singleWave:" + singleWave, "blockPerWave:" + blockPerWave);


            float perOffset = 1.0F / blockPerWave;

            int halfWave = (int) Math.floor((float) blockPerWave / 2.0F);

            int perAlpha = maxAlpha / (halfWave - 1);

            for (int i = -halfWave; i < halfWave + 1; i++) {
                int ii = halfWave - Math.abs(i);
                int iii = i + halfWave;
                colors[iii] =
                        (int) (perAlpha * Math.sin((float) ii / (float) blockPerWave * 3.14F)) << 24;

                offsets[iii] = perOffset * iii;

                Log.d("index:" + i, "colors:0x" + Integer.toHexString(colors[iii]) + ", offset:"
                        + offsets[iii]);
            }

            maskShader =
                    new LinearGradient(offset, 0, singleWave + offset, 0, colors, offsets,
                            Shader.TileMode.REPEAT);

            paint.setShader(maskShader);
            maskCanvas.drawRect(0, 0, bitmap.getWidth(), bitmap.getHeight(), paint);
            paint.setShader(null);

        }
    }

    public void setTexture(int bitmapRes){
        setTexture(BitmapFactory.decodeResource(getResources(), bitmapRes));
    }

    public void setTexture(Bitmap bitmap){
        this.bitmap = bitmap;

        if(shadowMask == null){
            setupMask();
        }

        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        width = MeasureSpec.getSize(widthMeasureSpec);
        height = MeasureSpec.getSize(heightMeasureSpec);

        centerX = width / 2;
        centerY = height / 2;
    }

    public void flip(int x, int y){
        touchX = x;
        touchY = y;

        invalidate();
    }

    public void percentageFlip(float x, float y){
        touchX = (int) (width * x);
        touchY = (int) (height * y);

        invalidate();
    }

    public void setDirection(int direction){
        this.direction = direction;

        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        if(this.bitmap != null){
            int index = 0; // 计数器,按照图片的实际宽高,去存储,是按照一个点一个点的去计算绘制,总数是 (bitmapHeight = 7)*(bitmapWidth = 40)的总数

            float ratio = (float) touchX / (float) width;
            float gap = 60.0F * (direction == DIRECTION_LEFT ? ratio : (1 - ratio));
            int alpha = 0;
            for (int y = 0; y <= bitmapHeight; y++) {
                // height 是控件可显示的的高度  bitmapHeight 固定值 7
                float fy = height / bitmapHeight * y;// 计算的是实际的高除以7然后再乘以y  就是在垂直高度上.
                // touchY 触摸的点的Y  下面这几句的的目的是:最左边在滑动的时候是有一个波浪形状的,这里通过计算触摸点大体分成5个点,然后每个点都用不同的速率,然后就可以有个狐仙
                float longDisSide = touchY > height - touchY ? touchY : height - touchY; //  触摸点,把Y轴分成了上下两个部分,按照长的那个为longDisSide
                float longRatio = Math.abs(fy - touchY) / longDisSide;

                longRatio = interpolator.getInterpolation(longRatio);
                // 下面的xBlock 一直都是固定的   realwidth 是计算出来去移动的宽度的感觉.待定.
                float realWidth = longRatio * (touchX - delayOffsetX);
                float xBlock = (float) width / (float) bitmapWidth;

//                float r = 0;
                for (int x = 0; x <= bitmapWidth; x++) {
                    // 移动之后剩下的部分占整个显示控件宽度的百分比
                    ratio = (touchX - realWidth) / (float) width;

                    switch(direction){
                        case DIRECTION_LEFT:
                            verts[index * 2] = (bitmapWidth - x) * xBlock * ratio + (x * xBlock);
                            break;
                        case DIRECTION_RIGHT:
                            verts[index * 2] =  x * xBlock * ratio;
                            break;
                    }
                    // 利用的是sina函数的值域在(-1, 1) 之间然后计算出的弧度
                    float realHeight = height - ((float) Math.sin(x * 0.5F - Math.PI) * gap + gap);

//                    r += x == 0 ? 0 : bitmapWidth * 0.08F / x;

                    float offsetY = realHeight / bitmapHeight * y;

                    verts[index * 2 + 1] = (height - realHeight) / 2 + offsetY;

                    int color;

                    int channel = 255 - (int) (height - realHeight) * 2;
                    if (channel < 255) {
                        alpha = (int) ((255 - channel) / 120.0F * maxAlpha) * 4;
                    }
                    if (newApiFlag) {
                        channel = channel < 0 ? 0 : channel;
                        channel = channel > 255 ? 255 : channel;

                        color = 0xFF000000 | channel << 16 | channel << 8 | channel;

                        colors[index] = color;
                    }

                    index += 1;
                }
            }

            // 整个文章重点在这里,利用drawBitmapMesh 重新定义网格,然后图片会根据网格进行拉伸.然后就显示出了响应的图片的窗帘效果.
            canvas.drawBitmapMesh(bitmap, bitmapWidth, bitmapHeight, verts, 0, colors, 0, null);
            if (!newApiFlag) {
                alpha = alpha > 255 ? 255 : alpha;
                alpha = alpha < 0 ? 0 : alpha;
                paint.setAlpha(alpha);
                canvas.drawBitmapMesh(shadowMask, bitmapWidth, bitmapHeight, verts, 0, null, 0, paint);
                paint.setAlpha(255);
            }
        }
    }
}