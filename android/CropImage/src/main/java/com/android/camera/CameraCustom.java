/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera;

import android.os.Bundle;
import android.provider.MediaStore;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.content.pm.ActivityInfo;

/**
 * The activity can crop specific region of interest from an image.
 */
public class CameraCustom extends MonitoredActivity {
    private static final String TAG = "Camera";
    private boolean started = false;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        if (started) {
            setResult(RESULT_CANCELED);
            finish();
        }

        Intent localIntent = getIntent();
        Bundle extras = localIntent.getExtras();
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtras(extras);

        if (cameraIntent.resolveActivity(getPackageManager()) == null) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        this.started = true;
        startActivityForResult(cameraIntent, 1, null);
    }
    
    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        // user cancel
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if (resultCode != RESULT_OK) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        setResult(RESULT_OK);
        finish();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("React,CameraCustom","onDestroy");
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setResult(RESULT_CANCELED);
        finish();
    }

}

