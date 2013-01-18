package com.astrider.mahjongTileRecognizer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MahjongTileRecognizerActivity extends Activity {
	private static final String TAG = "Mahjong::Activity";
	
	private final static String SAVE_FOLDER_NAME = "/mahjong/";
	private FrameLayout mFrameLayout;
	private SurfaceView mCameraView;
	private OverlayView mOverlayView;	
	private Camera mCamera;
	
	private BaseLoaderCallback mOpenCVCallBack = new BaseLoaderCallback(this) {
	   @Override
	   public void onManagerConnected(int status) {
	     switch (status) {
	       case LoaderCallbackInterface.SUCCESS:
	       {
	          Log.i(TAG, "OpenCV loaded successfully");
	          
	          onOpenCVLoad();
	       } break;
	       default:
	       {
	          super.onManagerConnected(status);
	       } break;
	     }
	   }
	};

	/** Call on every application resume **/
	@Override
	protected void onResume()
	{
	    Log.i(TAG, "called onResume");
	    super.onResume();

	    Log.i(TAG, "Trying to load OpenCV library");
	    if (!OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_2, this, mOpenCVCallBack))
	    {
	        Log.e(TAG, "Cannot connect to OpenCV Manager");
	    }
	}
		
    /** Called when the activity is first created. */
	@Override
    public void onCreate(Bundle savedInstanceState) {
    	// initialize
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // initialize views
        mFrameLayout = new FrameLayout(this);
        mCameraView = new SurfaceView(this);
        mOverlayView = new OverlayView(this);
    }
	
	private void onOpenCVLoad() {
        // abort if camera not detected
    	if(!isCameraAvailable()) {
    		int duration = Toast.LENGTH_LONG;
    		CharSequence text = "This app requires Camera";
    		Toast toast = Toast.makeText(this, text, duration);
    		toast.show();
    		
    		finish();
    	}
    	
		// setup cameraView
        SurfaceHolder holder = mCameraView.getHolder();
        holder.addCallback(mSurfaceHolderCallback);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        Log.i(TAG, "set up cameraView");
        
        // setup overlayView
        mOverlayView.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				if(event.getAction() == MotionEvent.ACTION_DOWN) {
					autoFocus();
				}
				
				if(event.getAction() == MotionEvent.ACTION_UP) {
					takePicture();
				}
				return true;
			}
		});
        
        mFrameLayout.addView(mCameraView);
        mFrameLayout.addView(mOverlayView);
        setContentView(mFrameLayout);
	}

	private Camera.PictureCallback mPictureCallback = new Camera.PictureCallback () {
		public void onPictureTaken(byte[] data, Camera camera) {
			mCamera.stopPreview();
			
			// restart if capture failed
			if(data == null) {
				Log.d("TAG", "failed to get data");
				mCamera.startPreview();
				return;
			}

			// decode picture and do things
			Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
			saveImageToSDCard(bitmap);
			
			CaptureHelper helper = new CaptureHelper(bitmap, getBaseContext().getResources(), getPackageName());
			String[] tiles = helper.getDetectedTileNames();
			float[] similarities = helper.getSimilarities();
			mOverlayView.setResult(tiles, similarities);
			
			Bitmap[] slicedImages = helper.getSlicedImages();
			for (Bitmap image : slicedImages) {
				saveImageToSDCard(image);
			}
			
			mCamera.startPreview();
		}
	};

	private SurfaceHolder.Callback mSurfaceHolderCallback = new SurfaceHolder.Callback() {
		public void surfaceCreated(SurfaceHolder holder) {
			mCamera = Camera.open();
			try {
				mCamera.setPreviewDisplay(holder);
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
		
		public void surfaceChanged(SurfaceHolder holder,
				int format, int width, int height) {
			mCamera.startPreview();
		}
		
		public void surfaceDestroyed(SurfaceHolder holder) {
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
	};

	private void autoFocus() {
		mCamera.autoFocus(null);
	}
	
	private void takePicture() {
		mCamera.stopPreview();
//			mCamera.setDisplayOrientation(90);
		
		// sleep and take picture
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					Thread.sleep(1000);
				} catch (Exception e) {
				}
				
				mCamera.takePicture(null, null, mPictureCallback);
			}
		});
		t.start();
	}
	
	private void saveImageToSDCard(Bitmap bitmap) {
		if(!isSDCardMounted()) {
			Log.d("TAG", "SDCard is not mounted");
			mCamera.startPreview();
			return;
		}
		
		FileOutputStream out = null;
		try {
			String path = Environment.getExternalStorageDirectory().getPath();
			Log.d("TAG", path);
			
			File dir = new File(path + SAVE_FOLDER_NAME);
			if(!dir.exists()) {
				dir.mkdir();
			}
			
			long date = System.currentTimeMillis();
			
			String filename = path + SAVE_FOLDER_NAME + date + ".jpg";
			
			out = new FileOutputStream(filename);
			bitmap.compress(CompressFormat.JPEG, 100, out);
			out.close();
			
			try {
				// register image to Gallery
	            ContentValues values = new ContentValues();
	            ContentResolver contentResolver = getApplicationContext().getContentResolver();
	            values.put (Images.Media.MIME_TYPE, "image/jpeg");
	            values.put (Images.Media.DATA, filename);
	            values.put (Images.Media.SIZE, new File(filename).length ());
	            values.put (Images.Media.DATE_ADDED, date);
	            values.put (Images.Media.DATE_TAKEN, date);
	            values.put (Images.Media.DATE_MODIFIED, date);

	            contentResolver.insert (MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
	        } catch (Exception e) {
	        	e.printStackTrace ();
	        } finally {
	            if (out != null) {
	            	out.close ();
	            }
	        }
			
			Log.d("TAG", "saved image to SDCard");
		} catch (Exception e) {
			mCamera.release();
			Log.d("TAG", "Failed to save image to SDCard");
		}
	}
	
	private boolean isCameraAvailable() {
		Context context = getApplicationContext();
		return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
	}
	
	private boolean isSDCardMounted() {
		String state = Environment.getExternalStorageState();
		return (Environment.MEDIA_MOUNTED.equals(state));
	}
}