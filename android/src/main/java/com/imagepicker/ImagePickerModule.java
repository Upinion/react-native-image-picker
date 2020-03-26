package com.imagepicker;

import com.imagepicker.permissions.PermissionUtils;
import com.imagepicker.permissions.OnImagePickerPermissionsCallback;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.Manifest;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.widget.ArrayAdapter;
import android.util.Log;
import android.os.Build;
import android.os.Bundle;
import com.android.camera.CropImageIntentBuilder;
import com.android.camera.CameraCustomIntentBuilder;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;

import com.facebook.react.ReactActivity;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.PermissionListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Date;
import java.lang.Exception;
import java.lang.Boolean;
import java.text.SimpleDateFormat;
import java.lang.ref.WeakReference;

public class ImagePickerModule extends ReactContextBaseJavaModule {

  static final int REQUEST_LAUNCH_IMAGE_CAPTURE     = 1;
  static final int REQUEST_LAUNCH_IMAGE_LIBRARY     = 2;
  static final int REQUEST_LAUNCH_VIDEO_LIBRARY     = 3;
  static final int REQUEST_LAUNCH_VIDEO_CAPTURE     = 4;
  static final int REQUEST_CROP_PICTURE             = 5;
  static final int REQUEST_PERMISSIONS_FOR_CAMERA   = 11;
  static final int REQUEST_PERMISSIONS_FOR_LIBRARY  = 12;

  private final ReactApplicationContext mReactContext;
  private final int dialogThemeId;

  private Activity mReactActivity;
  private Uri mCameraCaptureURI;
  private File mCameraCaptureFile;
  private Uri mCropImagedUri;
  private Callback mCallback;
  private ReadableMap mOptions;
  private Boolean noData = false;
  private Boolean tmpImage;
  private Boolean allowEditing = false;
  private Boolean pickVideo = false;
  private int maxWidth = 0;
  private int maxHeight = 0;
  private int aspectX = 0;
  private int aspectY = 0;
  private int quality = 100;
  private int angle = 0;
  private Boolean forceAngle = false;
  private int videoQuality = 1;
  private int videoDurationLimit = 0;
  private int cropWidth = 0;
  private int cropHeight = 0;
  WritableMap response;

  private final ActivityEventListener mActivityEventListener = new BaseActivityEventListener() {
      @Override
      public void onActivityResult(final Activity mReactActivity, final int requestCode, final int resultCode, final Intent data) {
          //robustness code
          if (mCallback == null || (
                      requestCode != REQUEST_LAUNCH_IMAGE_CAPTURE && requestCode != REQUEST_LAUNCH_IMAGE_LIBRARY
                      && requestCode != REQUEST_LAUNCH_VIDEO_LIBRARY && requestCode != REQUEST_LAUNCH_VIDEO_CAPTURE
                      && requestCode != REQUEST_CROP_PICTURE)) {
              return;
                      }

          // user cancel
          if (resultCode != Activity.RESULT_OK) {
              response.putBoolean("didCancel", true);
              mCallback.invoke(response);
              return;
          }

          Uri uri;
          String realPath=null;
          CropImageIntentBuilder cropImage;
          Intent cropIntent;
          switch (requestCode) {

              case REQUEST_LAUNCH_IMAGE_CAPTURE:
                  if (mCameraCaptureURI != null && mCameraCaptureFile.exists()) {
                      uri = mCameraCaptureURI;
                      cropImage = new CropImageIntentBuilder(maxWidth, maxHeight);
                      cropImage.setOutlineColor(0xFF03A9F4);
                      cropImage.setSourceImage(uri);
                      cropIntent = cropImage.getIntent(mReactContext.getApplicationContext());
                      cropIntent.putExtra("return-data", true);
                      mReactActivity.startActivityForResult(cropIntent, REQUEST_CROP_PICTURE, null);
                  } else {
                      Intent libraryIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);      
                      mReactActivity.startActivityForResult(libraryIntent, requestCode, null);
                  }
                  break;
              case REQUEST_CROP_PICTURE: //used for the cropping functionality
                  response.putString("data", encodeTobase64((Bitmap)data.getExtras().getParcelable("data")));
                  mCallback.invoke(response);
                  break;
              case REQUEST_LAUNCH_IMAGE_LIBRARY:
                  cropImage = new CropImageIntentBuilder(maxWidth, maxHeight);
                  cropImage.setOutlineColor(0xFF03A9F4);
                  cropImage.setSourceImage(data.getData());
                  cropIntent = cropImage.getIntent(mReactContext.getApplicationContext());
                  cropIntent.putExtra("return-data", true);
                  mReactActivity.startActivityForResult(cropIntent, REQUEST_CROP_PICTURE, null);
                  break;
              case REQUEST_LAUNCH_VIDEO_LIBRARY:
                  response.putString("uri", data.getData().toString());
                  mCallback.invoke(response);
                  break;
              case REQUEST_LAUNCH_VIDEO_CAPTURE:
                  response.putString("uri", data.getData().toString());
                  mCallback.invoke(response);
                  break;
          }
      } 
  };

  private PermissionListener permissionListener = new PermissionListener()
  {
    public boolean onRequestPermissionsResult(final int requestCode,
                                              @NonNull final String[] permissions,
                                              @NonNull final int[] grantResults)
    {
      boolean permissionsGranted = true;
      for (int i = 0; i < permissions.length; i++)
      {
        final boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
        permissionsGranted = permissionsGranted && granted;
      }

      if (mCallback == null || mOptions == null)
      {
        return false;
      }

      if (!permissionsGranted)
      {
        response.putBoolean("didCancel", true);
        mCallback.invoke(response);
        return false;
      }

      switch (requestCode)
      {
        case REQUEST_PERMISSIONS_FOR_CAMERA:
          launchCamera(mOptions, mCallback);
          break;

        case REQUEST_PERMISSIONS_FOR_LIBRARY:
          launchImageLibrary(mOptions, mCallback);
          break;

      }
      return true;
    }
  };

  public ImagePickerModule(ReactApplicationContext reactContext,
      @StyleRes final int dialogThemeId)
  {
    super(reactContext);

    this.dialogThemeId = dialogThemeId;
    reactContext.addActivityEventListener(mActivityEventListener);
    mReactContext = reactContext;
  }

  @Override
  public String getName() {
    return "ImagePickerManager";
  }

  @ReactMethod
  public void showImagePicker(final ReadableMap options, final Callback callback) {
    mReactActivity = getCurrentActivity();
    Activity currentActivity = mReactActivity;
    response = Arguments.createMap();

    if (currentActivity == null) {
      response.putString("error", "can't find current Activity");
      callback.invoke(response);
      return;
    }

    final List<String> titles = new ArrayList<String>();
    final List<String> actions = new ArrayList<String>();

    String cancelButtonTitle = getReactApplicationContext().getString(android.R.string.cancel);

    if (options.hasKey("takePhotoButtonTitle")
            && options.getString("takePhotoButtonTitle") != null
            && !options.getString("takePhotoButtonTitle").isEmpty()) {
      titles.add(options.getString("takePhotoButtonTitle"));
      actions.add("photo");
    }
    if (options.hasKey("chooseFromLibraryButtonTitle")
            && options.getString("chooseFromLibraryButtonTitle") != null
            && !options.getString("chooseFromLibraryButtonTitle").isEmpty()) {
      titles.add(options.getString("chooseFromLibraryButtonTitle"));
      actions.add("library");
    }
    if (options.hasKey("cancelButtonTitle")
            && !options.getString("cancelButtonTitle").isEmpty()) {
      cancelButtonTitle = options.getString("cancelButtonTitle");
    }

    if (options.hasKey("customButtons")) {
      ReadableMap buttons = options.getMap("customButtons");
      ReadableMapKeySetIterator it = buttons.keySetIterator();
      // Keep the current size as the iterator returns the keys in the reverse order they are defined
      int currentIndex = titles.size();
      while (it.hasNextKey()) {
        String key = it.nextKey();

        titles.add(currentIndex, key);
        actions.add(currentIndex, buttons.getString(key));
      }
    }

    titles.add(cancelButtonTitle);
    actions.add("cancel");

    ArrayAdapter<String> adapter = new ArrayAdapter<String>(currentActivity,
            android.R.layout.select_dialog_item, titles);
    AlertDialog.Builder builder = new AlertDialog.Builder(currentActivity);
    if (options.hasKey("title") && options.getString("title") != null && !options.getString("title").isEmpty()) {
      builder.setTitle(options.getString("title"));
    }

    builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int index) {
        String action = actions.get(index);

        switch (action) {
          case "photo":
            launchCamera(options, callback);
            break;
          case "library":
            launchImageLibrary(options, callback);
            break;
          case "cancel":
            response.putBoolean("didCancel", true);
            callback.invoke(response);
            break;
          default: // custom button
            response.putString("customButton", action);
            callback.invoke(response);
        }
      }
    });

    final AlertDialog dialog = builder.create();
    /**
     * override onCancel method to callback cancel in case of a touch outside of
     * the dialog or the BACK key pressed
     */
    dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        dialog.dismiss();
        response.putBoolean("didCancel", true);
        callback.invoke(response);
      }
    });
    mCallback = callback;
    dialog.show();
  }

  // NOTE: Currently not reentrant / doesn't support concurrent requests
  @ReactMethod
  public void launchCamera(final ReadableMap options, final Callback callback) {
    int requestCode;
    Intent cameraIntent;
    response = Arguments.createMap();

    mOptions = options;
    mCallback = callback;
    parseOptions(options);

    if (!permissionsCheck(mReactActivity, callback, REQUEST_PERMISSIONS_FOR_CAMERA)) {
      return;
    }

    if (pickVideo == true) {
      requestCode = REQUEST_LAUNCH_VIDEO_CAPTURE;
      cameraIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
      cameraIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, videoQuality);
      if (videoDurationLimit > 0) {
        cameraIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, videoDurationLimit);
      }
    } else {
        requestCode = REQUEST_LAUNCH_IMAGE_CAPTURE;
        CameraCustomIntentBuilder builder = new CameraCustomIntentBuilder();
        cameraIntent = builder.getIntent(mReactContext.getApplicationContext());
        mCameraCaptureFile = createNewFile();
        mCameraCaptureURI = null;
        if (mCameraCaptureFile != null) {
            // Fix for SDK > 24 need to use FileProvider
            if (Build.VERSION.SDK_INT >= 24) {
                mCameraCaptureURI = FileProvider.getUriForFile(
                    mReactContext,
                    mReactContext.getApplicationContext().getPackageName() + ".provider",
                    mCameraCaptureFile
                );
            } else {
                mCameraCaptureURI = Uri.fromFile(mCameraCaptureFile);
            }
        }
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCameraCaptureURI);
    }

    if (cameraIntent.resolveActivity(mReactContext.getPackageManager()) == null) {
      response.putString("error", "Cannot launch camera");
      callback.invoke(response);
      return;
    }

    try {
        mReactActivity.startActivityForResult(cameraIntent, requestCode, null);
    } catch (ActivityNotFoundException e) {
      e.printStackTrace();
    }
  }

  // NOTE: Currently not reentrant / doesn't support concurrent requests
  @ReactMethod
  public void launchImageLibrary(final ReadableMap options, final Callback callback) {
    int requestCode;
    Intent libraryIntent;
    response = Arguments.createMap();

    mOptions = options;
    mCallback = callback;
    parseOptions(options);

    if (!permissionsCheck(mReactActivity, callback, REQUEST_PERMISSIONS_FOR_LIBRARY)) {
      return;
    }

    if (pickVideo == true) {
      requestCode = REQUEST_LAUNCH_VIDEO_LIBRARY;
      libraryIntent = new Intent(Intent.ACTION_PICK);
      libraryIntent.setType("video/*");
    } else {
      requestCode = REQUEST_LAUNCH_IMAGE_LIBRARY;
      libraryIntent = new Intent(Intent.ACTION_PICK,
        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
    }

    if (libraryIntent.resolveActivity(mReactContext.getPackageManager()) == null) {
      response.putString("error", "Cannot launch photo library");
      callback.invoke(response);
      return;
    }

    mCallback = callback;
    try {
      mReactActivity.startActivityForResult(libraryIntent, requestCode, null);
    } catch (ActivityNotFoundException e) {
      e.printStackTrace();
    }
  }

  public @StyleRes int getDialogThemeId()
  {
    return this.dialogThemeId;
  }

  public @NonNull Activity getActivity()
  {
    return getCurrentActivity();
  }

  public Context getContext()
  {
    return getReactApplicationContext();
  }

  private static String encodeTobase64(Bitmap image) {
      Bitmap immagex=image;
      ByteArrayOutputStream baos = new ByteArrayOutputStream();  
      immagex.compress(Bitmap.CompressFormat.JPEG, 100, baos);
      byte[] b = baos.toByteArray();
      String imageEncoded = Base64.encodeToString(b,Base64.NO_WRAP);

      Log.e("LOOK", imageEncoded);
      return imageEncoded;
  }
  
  private String getRealPathFromURI(Uri uri) {
    String result = null;
    String[] projection = {MediaStore.Images.Media.DATA};
    try{
    Cursor cursor = mReactContext.getContentResolver().query(uri, projection, null, null, null);
      if (cursor == null) { // Source is Dropbox or other similar local file path
        result = uri.getPath();
      } else {
        cursor.moveToFirst();
        int idx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        result = cursor.getString(idx);
        cursor.close();
      }
    }catch(Exception e){
        e.printStackTrace();
    }

    return result;
  }

  /**
   * Create a file from uri to allow image picking of image in disk cache
   * (Exemple: facebook image, google image etc..)
   *
   * @doc =>
   * https://github.com/nostra13/Android-Universal-Image-Loader#load--display-task-flow
   *
   * @param uri
   * @return File
   * @throws Exception
   */
  private File createFileFromURI(Uri uri) throws Exception {
    File file = new File(mReactContext.getCacheDir(), "photo-" + uri.getLastPathSegment());
    InputStream input = mReactContext.getContentResolver().openInputStream(uri);
    OutputStream output = new FileOutputStream(file);

    try {
      byte[] buffer = new byte[4 * 1024];
      int read;
      while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
      output.flush();
    } finally {
      output.close();
      input.close();
    }

    return file;
  }

  private String getBase64StringFromFile(String absoluteFilePath) {
    InputStream inputStream = null;
    try {
      inputStream = new FileInputStream(absoluteFilePath);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }

    byte[] bytes;
    byte[] buffer = new byte[8192];
    int bytesRead;
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      while ((bytesRead = inputStream.read(buffer)) != -1) {
        output.write(buffer, 0, bytesRead);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    bytes = output.toByteArray();
    return Base64.encodeToString(bytes, Base64.NO_WRAP);
  }

  /**
   * Create a resized image to fill the maxWidth/maxHeight values,the quality
   * value and the angle value
   *
   * @param realPath
   * @param initialWidth
   * @param initialHeight
   * @return resized file
   */
  private File getResizedImage(final String realPath, final int initialWidth, final int initialHeight) {
    Bitmap photo = BitmapFactory.decodeFile(realPath);

    Bitmap scaledphoto = null;
    if (maxWidth == 0) {
      maxWidth = initialWidth;
    }
    if (maxHeight == 0) {
      maxHeight = initialHeight;
    }
    double widthRatio = (double) maxWidth / initialWidth;
    double heightRatio = (double) maxHeight / initialHeight;

    double ratio = (widthRatio < heightRatio)
            ? widthRatio
            : heightRatio;

    Matrix matrix = new Matrix();
    matrix.postRotate(angle);
    matrix.postScale((float) ratio, (float) ratio);

    scaledphoto = Bitmap.createBitmap(photo, 0, 0, photo.getWidth(), photo.getHeight(), matrix, true);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    scaledphoto.compress(Bitmap.CompressFormat.JPEG, quality, bytes);

    File f = createNewFile();
    FileOutputStream fo;
    try {
      fo = new FileOutputStream(f);
      try {
        fo.write(bytes.toByteArray());
      } catch (IOException e) {
        e.printStackTrace();
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }

    // recycle to avoid java.lang.OutOfMemoryError
    if (photo != null) {
      scaledphoto.recycle();
      photo.recycle();
      scaledphoto = null;
      photo = null;
    }
    return f;
  }

  /**
   * Create a new fileokk
   *
   * @return an empty file
   */
  private File createNewFile() {
      // To be safe, you should check that the SDCard is mounted
      // using Environment.getExternalStorageState() before doing this.

      File mediaStorageDir = Environment.getExternalStoragePublicDirectory(
                  Environment.DIRECTORY_PICTURES);
      // This location works best if you want the created images to be shared
      // between applications and persist after your app has been uninstalled.

      // Create the storage directory if it does not exist
      if (! mediaStorageDir.exists()){
          if (! mediaStorageDir.mkdirs()){
              Log.d("React,Camera", "failed to create directory");
              return null;
          }
      }

      // Create a media file name
      String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
      File mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                  "IMG_"+ timeStamp + ".jpg");

      return mediaFile;
  }

  private void parseOptions(final ReadableMap options) {
      noData = false;
      if (options.hasKey("noData")) {
          noData = options.getBoolean("noData");
      }
      maxWidth = 0;
      if (options.hasKey("maxWidth")) {
          maxWidth = options.getInt("maxWidth");
      }
      maxHeight = 0;
      if (options.hasKey("maxHeight")) {
          maxHeight = options.getInt("maxHeight");
      }
      aspectX = 0;
      if (options.hasKey("aspectX")) {
          aspectX = options.getInt("aspectX");
      }
      aspectY = 0;
      if (options.hasKey("aspectY")) {
          aspectY = options.getInt("aspectY");
      }
      quality = 100;
      if (options.hasKey("quality")) {
          quality = (int) (options.getDouble("quality") * 100);
      }
      tmpImage = true;
      if (options.hasKey("storageOptions")) {
          tmpImage = false;
      }
      allowEditing = false;
      if (options.hasKey("allowsEditing")) {
          allowEditing = options.getBoolean("allowsEditing");
      }
      forceAngle = false;
      angle = 0;
      if (options.hasKey("angle")) {
          forceAngle = true;
          angle = options.getInt("angle");
      }
      pickVideo = false;
      if (options.hasKey("mediaType") && options.getString("mediaType").equals("video")) {
          pickVideo = true;
      }
      videoQuality = 1;
      if (options.hasKey("videoQuality") && options.getString("videoQuality").equals("low")) {
          videoQuality = 0;
      }
      videoDurationLimit = 0;
      if (options.hasKey("durationLimit")) {
          videoDurationLimit = options.getInt("durationLimit");
      }

  }

  private boolean permissionsCheck(
      @NonNull final Activity activity,
      @NonNull final Callback callback,
      @NonNull final int requestCode)
  {
    final int writePermission = ActivityCompat
      .checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    final int cameraPermission = ActivityCompat
      .checkSelfPermission(activity, Manifest.permission.CAMERA);

    final boolean permissionsGrated = writePermission == PackageManager.PERMISSION_GRANTED &&
      cameraPermission == PackageManager.PERMISSION_GRANTED;

    if (!permissionsGrated)
    {
      final Boolean dontAskAgain = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA);

      if (dontAskAgain)
      {
        final androidx.appcompat.app.AlertDialog dialog = PermissionUtils
          .explainingDialog(this, mOptions, new PermissionUtils.OnExplainingPermissionCallback()
        {
          @Override
          public void onCancel(WeakReference<ImagePickerModule> moduleInstance,
              DialogInterface dialogInterface)
          {
            final ImagePickerModule module = moduleInstance.get();
            if (module == null)
            {
              return;
            }
            response = Arguments.createMap();
            response.putBoolean("didCancel", true);
            callback.invoke(response);
          }

          @Override
          public void onReTry(WeakReference<ImagePickerModule> moduleInstance,
              DialogInterface dialogInterface)
          {
            final ImagePickerModule module = moduleInstance.get();
            if (module == null)
            {
              return;
            }
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", module.getContext().getPackageName(), null);
            intent.setData(uri);
            final Activity innerActivity = module.getActivity();
            if (innerActivity == null)
            {
              return;
            }
            innerActivity.startActivityForResult(intent, 1);
          }
        });
        dialog.show();
        return false;
      }
      else
      {
        String[] PERMISSIONS = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA};
        if (activity instanceof ReactActivity)
        {
          ((ReactActivity) activity).requestPermissions(PERMISSIONS, requestCode, permissionListener);
        }
        else if (activity instanceof OnImagePickerPermissionsCallback)
        {
          ((OnImagePickerPermissionsCallback) activity).setPermissionListener(permissionListener);
          ActivityCompat.requestPermissions(activity, PERMISSIONS, requestCode);
        }
        else
        {
          final String errorDescription = new StringBuilder(activity.getClass().getSimpleName())
            .append(" must implement ")
            .append(OnImagePickerPermissionsCallback.class.getSimpleName())
            .toString();
          throw new UnsupportedOperationException(errorDescription);
        }
        return false;
      }
    }
    return true;
  }
}
