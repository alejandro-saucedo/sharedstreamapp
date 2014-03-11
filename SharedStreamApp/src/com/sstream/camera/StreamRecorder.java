package com.sstream.camera;

import java.io.FileDescriptor;

import android.hardware.Camera;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.SurfaceHolder;

public class StreamRecorder {
	
	private static final String TAG = StreamRecorder.class.getName();
	private boolean prepared = false;
	private boolean recording = false;
	private MediaRecorder recorder = null;
	private Camera camera = null;
	private SurfaceHolder holder = null;
	private FileDescriptor fd = null;
	
	public StreamRecorder(Camera camera, SurfaceHolder holder, FileDescriptor fd){
		this.camera = camera;
		this.holder = holder;
		this.fd = fd;
	}
	
	
	public void record() {
		if(!prepared){
			prepared = prepareVideoRecorder(fd);
		}
		
		if (!prepared) {
			throw new IllegalStateException("MediaRecorder has not been prepared");
		}
		
		//(new Thread(){ public void run() {
		try {
			recorder.start();
			recording = true;

		} catch (Exception ex) {
			Log.e(TAG, "Error at recorder.start()", ex);
			recording = false;
			prepared = false;
			releaseMediaRecorder();
		}
		//};}).start();
	}
	
	public void pause(){
		if(recording){
			try{
				recorder.stop();
			}catch(Exception ex){
				Log.e(TAG, "Error at recorder.stop()", ex);
			}finally{
				recording = false;
			}
			
		}
	}
	
	public void stop(){
		pause();
		releaseMediaRecorder();
	}
	
	private void releaseMediaRecorder(){
		if(recorder != null){
			recorder.reset();
			recorder.release();
			recorder = null;
			camera.lock();
			prepared = false;
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
		recorder.setPreviewDisplay(holder.getSurface());
		
		
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
