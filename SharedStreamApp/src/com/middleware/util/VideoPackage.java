package com.middleware.util;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.HashMap;

import com.middleware.VideoContext;
/**
 * Sistemas Distribuidos
 * Miercoles 5 de Febrero 2014
 * 
 * CINVESTAV
 * 
 * Autores:
 * 
 * Juan Carlos Reyes Martinez
 * Hector Alejandro Saucedo Briseno.
 * Luis Angel Ramos Cobarruvias
 * 
 * 
 */
public class VideoPackage  implements Serializable, VideoInterface {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private String 	messageId ;
	private int 	messageType;
	
	
	private long globalTime ; // when the video package was generated ( global clock)
	private VideoContext videoContext ;
	private InetAddress originNode;
	private InetAddress targetNode;
	
	private HashMap <String, String> controlParameters = new HashMap<String, String> ( );
	
	
	
	public void addControlParameter (String key , String value) {
		controlParameters.put(key, value);
	}
	public void removeControlParameter (String key ) {
		controlParameters.remove(key);
	}
	
	public HashMap<String, String> getControlParameters() {
		return controlParameters;
	}
	

	public InetAddress getOriginNode() {
		return originNode;
	}


	public void setOriginNode(InetAddress origin) {
		this.originNode = origin;
	}
	public InetAddress getTargetNode() {
		return targetNode;
	}


	public void setTargetNode(InetAddress target) {
		this.targetNode = target;
	}

	public String getMessageId() {
		return messageId;
	}


	public void setMessageId(String s) {
		this.messageId = s;
	}


	public int getMessageType() {
		return messageType;
	}


	public void setMessageType(int messageType) {
		this.messageType = messageType;
	}


	public long getGlobalTime() {
		return globalTime;
	}


	public void setGlobalTime(long globalTime) {
		this.globalTime = globalTime;
	}


	public VideoContext getVideoContext() {
		return videoContext;
	}


	public void setVideoContext(VideoContext videoContext) {
		this.videoContext = videoContext;
	}
	

}
