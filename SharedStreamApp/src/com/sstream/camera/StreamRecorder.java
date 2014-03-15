package com.sstream.camera;

import java.io.FileDescriptor;

import android.hardware.Camera;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class StreamRecorder {
	
	private static final String TAG = StreamRecorder.class.getName();
	private boolean recording = false;
	private MediaRecorder recorder = null;
	private Camera camera = null;
	private SurfaceView surface = null;
	private FileDescriptor currFD = null;
	
	public StreamRecorder(Camera camera, SurfaceView surface){
		if(camera == null){
			throw new IllegalArgumentException("null camera");
		}
		if(surface == null){
			throw new IllegalArgumentException("null surface");
		}
		this.camera = camera;
		this.surface = surface;
	}
	
	public void setCamera(Camera camera) {
		this.camera = camera;
	}
	
	public Camera getCamera() {
		return camera;
	}
	
	public void setSurface(SurfaceView surface) {
		this.surface = surface;
	}
	
	public SurfaceView getSurface() {
		return surface;
	}
	
	public boolean isRecording() {
		return recording;
	}
	
	public void start(){
		start(currFD);
	}
	
	public void start(FileDescriptor fd) {
		if (!recording) {
			if (fd == null) {
				throw new IllegalArgumentException("null file descriptor");
			}			
			boolean prepared = prepareVideoRecorder(fd);

			if (!prepared) {
				throw new IllegalStateException("MediaRecorder could not be prepared");
			}

			try {
				recorder.start();
				recording = true;
				this.currFD = fd;

			} catch (Exception ex) {
				Log.e(TAG, "Error starting MediaRecorder", ex);
				recording = false;
				releaseMediaRecorder();
			}
		}
		
	}
	
	public void stop(){
		if(recording){
			try{
				Log.w(TAG, "Stopping recorder");
				recorder.stop();
			}catch(Error ex){
				Log.e(TAG, "Error stopping MediaRecorder", ex);
			}finally{
				recording = false;
				releaseMediaRecorder();
			}
			
		}
	}
		
	private void releaseMediaRecorder(){
		if(recorder != null){
			Log.w(TAG, "resetting recorder");
			recorder.reset();
			Log.w(TAG, "releasing recorder");
			recorder.release();
			recorder = null;
			Log.w(TAG, "unlocking camera");
			camera.lock();
			recording = false;
		}
	}
	
	private boolean prepareVideoRecorder(FileDescriptor fd){
		boolean prepared = false;
		
		recorder = new MediaRecorder();
		// Step 1: Unlock and set camera to MediaRecorder		
		camera.unlock();
		recorder.setCamera(camera);
		
		// Step 2: Set sources
		recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);     

		// Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
		
		// Step 4: Set output file		
		recorder.setOutputFile(fd);
		recorder.setOutputFormat(8);//MPEG4 ST
		//recorder.setVideoEncodingBitRate(90);
		
		recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		
		
		
		// Step 5: Set the preview output
		recorder.setPreviewDisplay(surface.getHolder().getSurface());
		
		
		// Step 6: Prepare configured MediaRecorder
		try{
			recorder.prepare();
			prepared = true;
		}catch(Exception ex){
			Log.d(TAG, "Problem preparing MediaRecorder", ex);
	        releaseMediaRecorder();
		}
		
		return prepared;

	}

}
