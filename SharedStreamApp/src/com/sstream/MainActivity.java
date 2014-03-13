package com.sstream;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;

import com.sharedstreamapp.R;
import com.sstream.camera.CameraPreview;
import com.sstream.camera.StreamPlayer;
import com.sstream.camera.StreamRecorder;
import com.sstream.middleware.Chronometer;
import com.sstream.middleware.Middleware;
import com.sstream.middleware.MiddlewareServer;
import com.sstream.middleware.util.ChronometerInterruptor;
import com.sstream.middleware.util.MSGTypes;
import com.sstream.middleware.util.MessageInterruption;
import com.sstream.middleware.util.VideoException;
import com.sstream.middleware.util.VideoInterface;
import com.sstream.middleware.util.VideoInterruption;
import com.sstream.middleware.util.VideoPackage;
import com.sstream.tcp.VideoClient;
import com.sstream.tcp.VideoServer;
import com.sstream.util.FilePathProvider;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.util.Log;
import android.view.Menu;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;

public class MainActivity extends Activity implements MessageInterruption {
	
	private static final String TAG = MainActivity.class.getName();
	private int cameraId = 1;
	private Camera camera = null;
	private CameraPreview preview = null;
	private boolean previewActive = false;
	private Middleware middleware = null;
	private Handler handler = null;
	private VideoServer videoServer = null;
	private VideoClient videoClient = null;
	private StreamRecorder recorder = null;
	private StreamPlayer player = null;
	private boolean coordinating = false;
	private boolean recording = false;
	private boolean controlRequested = false;
	private FilePathProvider fileManager = null;
	
	private boolean coordinatorSet = false;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		handler = new Handler();
		camera = getCamera();
		createCameraPreview();
		createMiddleware();
		fileManager = new FilePathProvider("SharedStreamApp");
		EditText edit = (EditText) findViewById(R.id.hostEditText);
		edit.setText("192.168.2.2");
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		camera = getCamera();
//		if(previewActive){
//			preview.openPreview();
//		}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		//preview.closePreview();
		releaseCamera();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
//		outState.putBoolean("previewActive", previewActive);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		//previewActive = savedInstanceState.getBoolean("previewActive");
	}

	public Camera getCamera(){
		if(camera == null){
			try {
				camera = Camera.open(0);
			} catch (Exception ex) {
				Log.e(TAG, "Problem accessing camera", ex);
			}
		}
		return camera;
	}
	
	private void releaseCamera(){
		if(camera != null){
			camera.release();
			camera = null;
		}
	}
	
	public String getIpAddr() {
		   WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
		   WifiInfo wifiInfo = wifiManager.getConnectionInfo();
		   int ip = wifiInfo.getIpAddress();

		   String ipString = String.format(
		   "%d.%d.%d.%d",
		   (ip & 0xff),
		   (ip >> 8 & 0xff),
		   (ip >> 16 & 0xff),
		   (ip >> 24 & 0xff));
		   
		   return ipString;
	}
	
	public InetAddress getAddr() throws IOException{
		String ip = getIpAddr();
		return InetAddress.getByName(ip);
	}

	private void createMiddleware() {

		try {
			
			middleware = new Middleware(this, getAddr());
			middleware.createServerListener();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	private void createCameraPreview(){
		FrameLayout previewHolder = (FrameLayout) findViewById(R.id.frameLayout);
		preview  = new CameraPreview(this, camera, cameraId);
		previewHolder.addView(preview);
	}
	
	
	@Override
	public void doInterruption(long processId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void doInterruption(long processId, byte[] message,
			InetAddress origin) {
		//Log.v(TAG, new String (message));
		
	}

	@Override
	public void doInterruption(VideoInterface vp) {
		if ( vp instanceof VideoPackage) {
			processVideoPackage( (VideoPackage) vp );
		} else if ( vp instanceof VideoException) {
			processVideoException( (VideoException) vp);
		}
	}
	
	
	private void processVideoPackage(VideoPackage pck){
		if ( pck.getMessageType() == MSGTypes.NEW_CONTEXT ) {
			//this.setEnableConnectButton( false );
			setButtonEnabled( R.id.requestControlButton,  true );
			setButtonEnabled( R.id.startRecButton,  false );
			setButtonEnabled( R.id.stopRecButton,  false );
			//preview.closePreview();
			//start playing video
	
			coordinatorSet = true;
			startPlaying(pck.getVideoContext().getCoordinator().getHostAddress());
			
			Log.d(TAG, "Playing video ...");
		}
		
		else if ( pck.getMessageType() == MSGTypes.OK_START_RECORDING ) { // I am the server
			setButtonEnabled( R.id.startRecButton,  false );
			setButtonEnabled( R.id.stopRecButton,  true );
			setButtonEnabled( R.id.requestControlButton,  false );
			coordinating  = true;
			coordinatorSet = true;
			stopPlaying();
			try{
				Thread.sleep(1000);
			}catch(InterruptedException ex){}
			startRecording();
			//serverError.setEnabled( false );
			Log.d(TAG, "Recording ...");
		}
		else if ( pck.getMessageType() == MSGTypes.COORDINATOR_POSITION_AVAILABLE ) {
			setButtonEnabled( R.id.startRecButton,  true );
			setButtonEnabled( R.id.stopRecButton,  false );
			setButtonEnabled( R.id.requestControlButton,  false );
			coordinatorSet = false;
			Log.d(TAG, "--> Coordinator position available, press start recording to stream video.");
		}
		else if ( pck.getMessageType() == MSGTypes.COORDINATOR_SET) {
			// connect to origin to stablish the socket tcp connection
			Log.d(TAG, "Playing video ...");
			setButtonEnabled( R.id.requestControlButton,  true );
			setButtonEnabled( R.id.startRecButton,  false );
			setButtonEnabled( R.id.stopRecButton,  false );
			//serverError.setEnabled( false );
			if(recording){
				stopRecording();
				try{
					Thread.sleep(1000);
				}catch(InterruptedException ex){}
			}else{
				try {
					stopPlaying();
					Thread.sleep(1000);
				} catch (Exception ex) {
					Log.e(TAG, "proble stopping playback", ex);
				}
			}
			startPlaying(pck.getVideoContext().getCoordinator().getHostAddress());
			
		}else if ( pck.getMessageType() == MSGTypes.RELEASE ) {
			//setButtonEnabled( R.id.startRecButton,  true );
			//setButtonEnabled( R.id.stopRecButton,  false );
			Log.d(TAG, "Stopped recording ...");
		}
		
	}
	
	
	private void processVideoException(VideoException pck){
		if ( pck.getErrorCode() == MSGTypes.DISCONNECTED ) {
			Log.d(TAG, "Node disconnected: " + pck.getVideoPackage().getTargetNode().getHostAddress());
			Log.d(TAG, "Removing Node ..." );
			middleware.getVideoContext().removeNode( pck.getVideoPackage().getTargetNode() );
			VideoPackage tmp = middleware.createGenericPacket( MSGTypes.UPDATE_CONTEXT);
			
			try {
				tmp.setVideoContext( middleware.getVideoContext());
				middleware.sendToAll( tmp );
			} catch (IOException e) {
				
			}
		}
		else if ( pck.getErrorCode() == MSGTypes.ACQUIRE_VIDEO_NOT_POSIBLE) {
			Log.d(TAG, "Acquire video not posible, put on wait list ...");
			
		}
		else if ( pck.getErrorCode() == MSGTypes.NO_NEXT_COORDINATOR_WARNING ) {
			
			Log.d(TAG, "Stopped recording ... Discovery service still available");
			List<InetAddress> nodes = middleware.getVideoContext().getNodes();
			
			for ( InetAddress n:  nodes) {
				
				VideoPackage tmp = middleware.createGenericPacket( MSGTypes.COORDINATOR_POSITION_AVAILABLE);
				tmp.setTargetNode( n );
				middleware.setWaitingForCoordinator( true );
				try {
					if (! MiddlewareServer.isLocalAddress(n)) {
						middleware.send( n,  tmp );
					}
					
				} catch (IOException e) {
					
					// Error handling for local server
				}
			}
			
		}
		else if ( pck.getErrorCode() == MSGTypes.ERROR_TRY_AGAIN) {
			Log.d(TAG, "Try Again");
			
			if ( false )  {// TO-DO, poner la funcion que regresa true|false si el android detecta dispositivo en la nube
				
				//try to Connect
			} else { // wating for a new server
				
				
				middleware.getVideoContext().setCoordinator( null );
				VideoPackage vp = middleware.createGenericPacket(MSGTypes.TIMEOUT_WAITING_NEW_COORDINATOR);
				Chronometer c = new Chronometer ( Chronometer.DEFAULT , Long.parseLong(vp.getMessageId()) );
				
				c.setInterruptionListener(new ChronometerInterruptor (c , vp , middleware ));
				
				c.startChronometer(Middleware.MAX_WAIT * 2 );
			}
			
		}
	}
	
	public void onStartRecClicked(View view){
		try {
			if(!coordinatorSet){
				setButtonEnabled(R.id.startRecButton, false);
				setButtonEnabled(R.id.requestControlButton, false);
				setButtonEnabled(R.id.stopRecButton, true);
				coordinatorSet = true;
				coordinating = true;
				middleware.setCoordinator(true);
				middleware.getVideoContext().setCoordinator( getAddr() );
				startRecording();
			}else{
				if (coordinating && recorder != null ) {
					//this is the coordinator but 
					recorder.record();
				} else {
					// TODO: request control
					
				}
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		
	}
	
	public void onRequestControlClicked(View view){
		execAsync(new Runnable() {
			
			@Override
			public void run() {
				middleware.acquire();
				
			}
		});
	}
	
	public void onStopRecClicked(View view){
		videoServer.pause();
		execAsync(new Runnable() {
			@Override
			public void run() {
				try{
					middleware.release();
				}catch(Exception ex){
					Log.e(TAG, "Problem releasing control", ex);
				}
			}
		});
	}
		
	public void onConnectClicked(View view){
		EditText edit = (EditText) findViewById(R.id.hostEditText);//192.168.2.2
		String host = edit.getText().toString();
		final VideoPackage vp = middleware.createGenericPacket(MSGTypes.NEW_MEMBER) ;
		try {
			final InetAddress node = InetAddress.getByName( host );
			vp.setTargetNode(node);
			(new Thread() {
				public void run() {
					try {
						middleware.send(node, vp);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}).start();
		} catch (Exception ex) {
			ex.printStackTrace();
		} 
		
	}
	
	private void startRecording() {
		try {
			if (videoServer == null) {
				videoServer = new VideoServer();
			}
			videoServer.start();
			// preview.closePreview();
			if (recorder == null) {
				recorder = new StreamRecorder(getCamera(), preview.getHolder(), videoServer.getFileDescriptor());
			}
			recorder.record();
			recording = true;
		} catch (Exception ex) {
			Log.e(TAG, "Problem trying to start recording", ex);
		}
	}
	
	private void stopRecording(){
		if(recorder != null){
			recorder.stop();
		}
		if(videoServer != null){
			videoServer.close();
		}
		videoServer = null;
		recorder = null;
		recording = false;
		releaseCamera();
	}
	
	private void startPlaying(String host){
		preview.closePreview();
		videoClient = new VideoClient(fileManager);
		player = new StreamPlayer(videoClient, preview.getHolder());
		videoClient.connect( host );
		
	}
	
	
	private void stopPlaying(){
		if(player != null){
			player.stop();
		}
		if(videoClient != null){
			videoClient.close();
		}
		player = null;
		videoClient = null;
		releaseCamera();


	}
	
	private void setButtonEnabled(final int buttonId, final boolean enabled){
		handler.post(new Runnable() {			
			@Override
			public void run() {
				Button button = (Button) findViewById(buttonId);
				button.setEnabled(enabled);
				
			}
		});
	}

	private void execAsync(Runnable runnable){
		Thread t = new Thread(runnable);
		t.start();
	}
	
}
