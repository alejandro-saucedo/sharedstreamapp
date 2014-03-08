package com.middleware.util;

public class PendingVideoPackage {

	private int waitingNodes =0;
	private VideoPackage videoPackage ;
	
	public PendingVideoPackage (VideoPackage vp , int wn ) {
		this.waitingNodes = wn;
		this.videoPackage = vp;
	}
	public int getWaitingNodes() {
		return waitingNodes;
	}
	public void setWaitingNodes(int waitingNodes) {
		this.waitingNodes = waitingNodes;
	}
	public VideoPackage getVideoPackage() {
		return videoPackage;
	}
	public void setVideoPackage(VideoPackage videoPackage) {
		this.videoPackage = videoPackage;
	}
	
	
}
