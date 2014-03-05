package com.sstream.middleware.util;


public class VideoException extends Exception implements VideoInterface {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private VideoPackage 	videoPackage ;
	private int 			errorCode;
	
	public int getErrorCode() {
		return errorCode;
	}
	public void setErrorCode(int errorrCode) {
		this.errorCode = errorrCode;
	}
	public VideoException ( ) {
		
		this.videoPackage = null;
	}
	public VideoException ( VideoPackage vp) {
		
		this.videoPackage = vp;
	}
	
	public VideoPackage getVideoPackage() {
		return videoPackage;
	}

	public void setVideoPackage(VideoPackage videoPackage) {
		this.videoPackage = videoPackage;
	}
	
	
}
