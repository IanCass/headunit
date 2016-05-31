package ca.yyx.hu;

import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import ca.yyx.hu.decoder.VideoDecoder;
import ca.yyx.hu.utils.SystemUI;
import ca.yyx.hu.utils.Utils;


public class SurfaceActivity extends Activity implements SurfaceHolder.Callback {
    SurfaceView mSurfaceView;
    VideoDecoder mVideoDecoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);   // !! Keep Screen on !!
        setContentView(R.layout.activity_headunit);

        mVideoDecoder = new VideoDecoder(this);

        mSurfaceView = (SurfaceView) findViewById(R.id.surface);
        mSurfaceView.getHolder().addCallback(this);

        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                SystemUI.hide(getWindow().getDecorView());
            }
        });

    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        SystemUI.hide(getWindow().getDecorView());
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Utils.logd("holder %s, format: %d, width: %d, height: %d", holder, format, width, height);
        mVideoDecoder.onSurfaceHolderAvailable(holder, width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mVideoDecoder.stop();
    }
}