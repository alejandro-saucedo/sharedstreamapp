package com.sstream.camera;

import java.io.IOException;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;

public class CameraPreview extends SurfaceView implements Callback {

	private static final String TAG = CameraPreview.class.getName();
	private Activity activity = null;
	private SurfaceHolder holder = null;
	private Camera camera = null;
	private int cameraId = 0;
	private boolean previewInProgress = false;
	private boolean previewAllowed = false;
	
	public CameraPreview(Activity activity, Camera camera, int cameraId) {
		super(activity);
		this.activity = activity;
		this.camera = camera;
		this.cameraId = cameraId;
		holder = getHolder();
		holder.addCallback(this);
		// deprecated setting, but required on Android versions prior to 3.0
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		
	}
	
	public void openPreview(){
		previewAllowed = true;
		if(!previewInProgress){
			surfaceCreated(holder);
		}
		
	}
	
	public void closePreview(){
		previewAllowed = false;
		if(previewInProgress){
			stopPreview();
		}
	}
	
	
	public Camera getCamera() {
		return camera;
	}
	
	public void setCamera(Camera camera) {
		this.camera = camera;
	}
	
	public int getCameraId() {
		return cameraId;
	}
	
	public void setCameraId(int cameraId) {
		this.cameraId = cameraId;
	}
	
	@Override
	public void surfaceCreated(final SurfaceHolder holder) {
		this.holder = holder;
		if(camera != null && previewAllowed){
			(new Thread() {
				@Override
				public void run() {
					stopPreview();
					updateCameraOrientation();
					startPreview(holder);
				}
			}).start(); 
		}
	}

	@Override
	public void surfaceChanged(final SurfaceHolder holder, int format, int w, int h) {
		this.holder = holder;
		if(camera != null && previewAllowed && this.holder.getSurface() != null){
			(new Thread() {
				@Override
				public void run() {
					stopPreview();
					updateCameraOrientation();
					startPreview(holder);
				}
			}).start(); 
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {

	}
	
	private void startPreview(final SurfaceHolder holder){
		try {
			camera.setPreviewDisplay(holder);
			camera.startPreview();
			previewInProgress = true;
		} catch (Exception ex) {
			Log.e(TAG, "Problem starting destroyed with new holder", ex);
		}
				
	}
	
	private void stopPreview(){
		if(camera != null){
			try{
				camera.stopPreview();
				previewInProgress = false;
			}catch(Exception ex){
				Log.e(TAG, "Problem stopping destroyed", ex);
			}
		}
	}
	
	private void updateCameraOrientation(){
		try {
			int orientation = 0;
			CameraInfo info = new CameraInfo();
			Camera.getCameraInfo(cameraId, info);

			int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
			int degrees = 0;
			switch (rotation) {
			case Surface.ROTATION_0:
				degrees = 0;
				break;
			case Surface.ROTATION_90:
				degrees = 90;
				break;
			case Surface.ROTATION_180:
				degrees = 180;
				break;
			case Surface.ROTATION_270:
				degrees = 270;
				break;
			}

			if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				orientation = (info.orientation + degrees) % 360;
				orientation = (360 - orientation) % 360; // compensate the
															// mirror
			} else { // back-facing
				orientation = (info.orientation - degrees + 360) % 360;
			}
			camera.setDisplayOrientation(orientation);
		} catch (Exception ex) {
			Log.e(TAG, "Proble setting camera display orientation", ex);
		}
	}
	


}
