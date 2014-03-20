package com.sstream;

import java.io.IOException;
import java.net.InetAddress;
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
import com.sstream.middleware.util.VideoPackage;
import com.sstream.tcp.VideoClient;
import com.sstream.tcp.VideoServer;
import com.sstream.util.FilePathProvider;
import com.sstream.util.NsdHelper;
import com.sstream.util.NsdHelper.NSDListener;

import android.net.nsd.NsdManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.hardware.Camera;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;

public class MainActivity extends Activity implements MessageInterruption, NSDListener {
	
	private static final String TAG = MainActivity.class.getName();
	private int cameraId = 1;
	private Camera camera = null;
	private CameraPreview preview = null;
//	private boolean previewActive = false;
	private Middleware middleware = null;
	private Handler handler = null;
	private VideoServer videoServer = null;
	private VideoClient videoClient = null;
	private boolean coordinating = false;
	private boolean recording = false;
//	private boolean controlRequested = false;
	private FilePathProvider fileManager = null;
	
	private boolean coordinatorSet = false;
	private NsdHelper nsdHelper;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		handler = new Handler();
		createCameraPreview();
		createMiddleware();
		createVideoServer();
		
		fileManager = new FilePathProvider("SharedStreamApp");
		
		EditText edit = (EditText) findViewById(R.id.hostEditText);
		edit.setText("");
		setButtonEnabled( R.id.connectButton,  false );
		setButtonEnabled( R.id.stopRecButton,  false );
		setButtonEnabled( R.id.requestControlButton,  false );
		nsdHelper = new NsdHelper(this,this);
		nsdHelper.initializeNsd();
	}
	
	@Override
	public void serviceStarted(){
		handler.post(new Runnable(){
			public void run() {
				setButtonEnabled( R.id.stopRecButton,  true );
			}
		});
	}

	@Override
	public void serviceDiscoverd(final InetAddress host, int port) {
		handler.post(new Runnable(){
			public void run() {
				EditText edit = (EditText) findViewById(R.id.hostEditText);
				edit.setText(host.getHostAddress());
				setButtonEnabled( R.id.connectButton,  true );
				setButtonEnabled( R.id.startRecButton,  false );
			};
		});
	}


	@Override
	public void serviceLost() {
		handler.post(new Runnable(){
			public void run() {
				EditText edit = (EditText) findViewById(R.id.hostEditText);
				edit.setText("");
				setButtonEnabled( R.id.connectButton,  false );
				setButtonEnabled( R.id.startRecButton,  true );
			};
		});
	}
	
	@Override
	protected void onPause() {
		if (nsdHelper != null) {
			nsdHelper.unregister();
			nsdHelper.stopDiscovery();
        }
		super.onPause();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		camera = getCamera();
//		if (nsdHelper != null) {
//			nsdHelper.discoverServices();
//		}
		// if(previewActive){
		// preview.openPreview();
		// }
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		//preview.closePreview();
		releaseCamera();
		videoServer.close();
		stopPlayback();
		nsdHelper.unregister();
	}
	
	@Override
	public void onBackPressed() {
		super.onBackPressed();
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
	
	public void releaseCamera(){
		if(camera != null){
			camera.release();
			camera = null;
		}
	}
	

	
	public InetAddress getAddress() throws IOException {
		WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
		int ip = wifiInfo.getIpAddress();

		String ipString = String.format("%d.%d.%d.%d", (ip & 0xff),
				(ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
		return InetAddress.getByName(ipString);
	}

	private void createMiddleware() {

		try {
			middleware = new Middleware(this, getAddress());
			middleware.createServerListener();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	private void createCameraPreview(){
		FrameLayout previewHolder = (FrameLayout) findViewById(R.id.frameLayout);
		previewHolder.removeAllViews();
		preview  = new CameraPreview(this, getCamera(), cameraId);
		previewHolder.addView(preview);
	}
	
	private void createVideoServer(){
		try{
			videoServer = new VideoServer(new StreamRecorder(getCamera(), preview));
		}catch(IOException ex){
			Log.e(TAG, "Fatal Error: VideoServer could not be created", ex);
		}
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
			startPlayback(pck.getVideoContext().getCoordinator().getHostAddress());
			
			Log.d(TAG, "Playing video ...");
		}
		
		else if ( pck.getMessageType() == MSGTypes.OK_START_RECORDING ) { // I am the server
			setButtonEnabled( R.id.startRecButton,  false );
			setButtonEnabled( R.id.stopRecButton,  true );
			setButtonEnabled( R.id.requestControlButton,  false );
			coordinating  = true;
			coordinatorSet = true;
			stopPlayback();
			startRecording();
			//serverError.setEnabled( false );
			Log.d(TAG, "Recording ...");
			nsdHelper.registerService(123);
		}
		else if ( pck.getMessageType() == MSGTypes.COORDINATOR_POSITION_AVAILABLE ) {
			setButtonEnabled( R.id.startRecButton,  true );
			setButtonEnabled( R.id.stopRecButton,  false );
			setButtonEnabled( R.id.requestControlButton,  false );
			coordinatorSet = false;
			Log.d(TAG, "--> Coordinator position available, press start recording to stream video.");
			stopPlayback();
		}
		else if ( pck.getMessageType() == MSGTypes.COORDINATOR_SET) {
			// connect to origin to stablish the socket tcp connection
			Log.d(TAG, "Playing video ...");
			setButtonEnabled( R.id.requestControlButton,  true );
			setButtonEnabled( R.id.startRecButton,  false );
			setButtonEnabled( R.id.stopRecButton,  false );
			//serverError.setEnabled( false );
			nsdHelper.unregister();
			if(recording){
				stopRecording();
			}else{
				stopPlayback();
			}
			startPlayback(pck.getVideoContext().getCoordinator().getHostAddress());
			
		}else if ( pck.getMessageType() == MSGTypes.RELEASE ) {
			//setButtonEnabled( R.id.startRecButton,  true );
			//setButtonEnabled( R.id.stopRecButton,  false );
			Log.d(TAG, "Stopped recording ...");
			stopRecording();
			nsdHelper.unregister();
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
		else if ( pck.getErrorCode() == MSGTypes.START_RECORDVIDEO_NOT_POSIBLE) {
			Log.d(TAG, "Start record video not posible, put on wait list ...");
			
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
			nsdHelper.registerService(123);
			if(!coordinatorSet){
				setButtonEnabled(R.id.startRecButton, false);
				setButtonEnabled(R.id.requestControlButton, false);
//				setButtonEnabled(R.id.stopRecButton, true);
				setButtonEnabled(R.id.connectButton, false);
				coordinatorSet = true;
				coordinating = true;
				middleware.setCoordinator(true);
				middleware.getVideoContext().setCoordinator( getAddress() );
				startRecording();
			}else{
				startRecording();
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
		videoServer.pauseRecorder();
		setButtonEnabled(R.id.startRecButton, true);
		setButtonEnabled(R.id.stopRecButton, false);
		
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
		nsdHelper.unregister();
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
//			createCameraPreview();
			videoServer.stream(getCamera(), preview);
			recording = true;
			setButtonEnabled(R.id.startRecButton, false);
//			setButtonEnabled(R.id.stopRecButton, true);
		} catch (Exception ex) {
			Log.e(TAG, "Problem trying to start recording", ex);
		}
	}
	
	private void stopRecording(){
		videoServer.sleep();
		recording = false;
		preview.closePreview();
		releaseCamera();
		setButtonEnabled(R.id.startRecButton, false);
		setButtonEnabled(R.id.stopRecButton, false);
	}
	
	private void startPlayback(final String host){
		handler.post(new Runnable() {
			
			@Override
			public void run() {
				createCameraPreview();
				videoClient = new VideoClient(fileManager, new StreamPlayer(preview),MainActivity.this);
				videoClient.connect( host );
			}
		}); 

		
	}
	
	
	private void stopPlayback(){
		if(videoClient != null){
			videoClient.close();
			videoClient = null;
//			createCameraPreview();
		}
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
	
	public void serverDown(final String host){
		execAsync(new Runnable() {
			
			@Override
			public void run() {
				middleware.handleServerDownError();
				
			}
		});
	}

}
