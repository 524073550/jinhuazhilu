package com.ke.zhu.camera_simple;

import android.os.Build;
import android.os.Bundle;
import android.view.TextureView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MainActivity extends AppCompatActivity {

    private CameraHelp cameraHelp;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextureView txt_view = findViewById(R.id.txt_view);
        cameraHelp = new CameraHelp(txt_view, this);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraHelp.releaseCamera();
    }
}
