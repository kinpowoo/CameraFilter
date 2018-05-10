/*
 * Copyright 2016 nekocode
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.nekocode.camerafilter;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author nekocode (nekocode.cn@gmail.com)
 */
public class SelfieActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private static final int REQUEST_STORAGE_PERMISSION = 102;
    ContentResolver resolver;
    private FrameLayout container;
    private SelfieCameraRenderer renderer;
    private TextureView textureView;
    private int filterId = R.id.filter0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(container = new FrameLayout(this));
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setTitle("原始");

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                Toast.makeText(this, "Camera access is required.", Toast.LENGTH_SHORT).show();

            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                        REQUEST_CAMERA_PERMISSION);
            }

        } else {
            setupCameraPreviewView();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CAMERA_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setupCameraPreviewView();
                }
                break;
            case REQUEST_STORAGE_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    capture();
                }
                break;
        }
    }

    void setupCameraPreviewView() {
        renderer = new SelfieCameraRenderer(this);
        textureView = new TextureView(this);
        container.addView(textureView);
        textureView.setSurfaceTextureListener(renderer);

        // Show original frame when touch the view
        textureView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        renderer.setSelectedFilter(R.id.filter0);
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        renderer.setSelectedFilter(filterId);
                        break;
                }
                return true;
            }
        });

        textureView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                renderer.onSurfaceTextureSizeChanged(null, v.getWidth(), v.getHeight());
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.filter, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        filterId = item.getItemId();

        if (filterId == R.id.reverse) {
            renderer.free();
            renderer = null;
            startActivity(new Intent(this, MainActivity.class));
            return true;
        }

        // TODO: need tidy up
        if (filterId == R.id.capture) {
            Toast.makeText(this,
                    capture() ? "The capture has been saved to your sdcard root path." :
                            "Save failed!",
                    Toast.LENGTH_SHORT).show();
            return true;
        }

        setTitle(item.getTitle());

        if (renderer != null)
            renderer.setSelectedFilter(filterId);

        return true;
    }

    private boolean capture() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_STORAGE_PERMISSION);
            return false;
        }


        String mPath = genSaveFileName(getTitle().toString() + "_", ".png");
        File imageFile = new File(mPath);
        if (imageFile.exists()) {
            imageFile.delete();
        }

        // create bitmap screen capture
        Bitmap bitmap = textureView.getBitmap();
        OutputStream outputStream = null;

        try {
            outputStream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream);
            outputStream.flush();
            outputStream.close();

            ContentValues values = new ContentValues();
            if (resolver == null) {
                resolver = this.getContentResolver();
            }
            values.put(MediaStore.Images.ImageColumns.DATA, mPath);
            values.put(MediaStore.Images.ImageColumns.TITLE,
                    mPath.substring(0, mPath.lastIndexOf("/")));
            values.put(MediaStore.Images.ImageColumns.DATE_TAKEN,
                    System.currentTimeMillis());
            values.put(MediaStore.Images.ImageColumns.MIME_TYPE,
                    "image/png");
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private String genSaveFileName(String prefix, String suffix) {
        Date date = new Date();
        SimpleDateFormat dateformat1 = new SimpleDateFormat("yyyyMMdd_hhmmss");
        String timeString = dateformat1.format(date);
        String externalPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/CAMERA";
        return externalPath + "/" + prefix + timeString + suffix;
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == 48||keyCode == 61 || keyCode == 25) {
            Toast.makeText(this,
                    capture() ? "The capture has been saved to your sdcard root path." :
                            "Save failed!",
                    Toast.LENGTH_SHORT).show();
        }

        return true;
    }
}
