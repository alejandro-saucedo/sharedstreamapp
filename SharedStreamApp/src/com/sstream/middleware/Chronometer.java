package com.sstream.middleware;

import com.sstream.middleware.util.Interruption;

/**
 * Sistemas Distribuidos
 * Miercoles 5 de Febrero 2014
 * 
 * CINVESTAV
 * 
 * 
 * Autores:
 * 
 * Juan Carlos Reyes Martinez
 * Hector Alejandro Saucedo Briseno.
 * Luis Angel Ramos Cobarruvias
 * 
 * 
 * 
 */

public class Chronometer  extends Thread{
    
    
    private boolean isThreadAlive = false ;
    private int time ;
    private int limit = 0;
    private Interruption interruption ;
    private long processId ;
    private int type ;
  
    private int timesExecuted = 0;
    
    private final int TASA_INC = 1;
    public int maxExecutions;
	public static final int DEFAULT = 0;
    public static final int PERIODIC = 1;
    public static final int DELIMITED = 2;
    /*
    *
    */
    public Chronometer ( ) {
    	isThreadAlive = false ;
        time = 0;
        this.processId = 0 ;
        this.type =  DEFAULT ;
    }
    public Chronometer (int type ) {
    	isThreadAlive = false ;
        time = 0;
        this.processId = 0 ;
        this.type =  type ;
    }
    public Chronometer (int type , long processId) {
    	isThreadAlive = false ;
        time = 0;
        this.processId = processId ;
        this.type = type ;
    }
    
    public Chronometer (int type , long processId, int repeatLimit) {
    	isThreadAlive = false ;
        time = 0;
        this.processId = processId ;
        this.type = type ;
        this.maxExecutions = repeatLimit;
    }
    
    public void setInterruptionListener (Interruption in) {
    	interruption = in;
    }
    /*
    *
    */
	private void addSeconds(int seconds) {

		try {
			
			synchronized (this) {
				if (isThreadAlive) {
					
					if (limit <= time) {
						
						interruption.doInterruption(processId);
						timesExecuted++;
						
						if (type == PERIODIC) {
							time = 0; // reset the counter
							
						} else if (type == DELIMITED) {
							time = 0;
							if (timesExecuted == maxExecutions) {
								stopChronometer();
							}
							
						}else {
							stopChronometer ();
						}

						
					}
				}
			}
			Thread.sleep(seconds * 1000);
			time++;
		} catch (InterruptedException err) {
			err.printStackTrace();
		}
	}
    /*
    *
    */
    public void run () {
        
        do {
            addSeconds (TASA_INC);
            
        }while (isThreadAlive) ;
    }
    
    /*
    *
    */
    public int  getTime () {
        return time ;
    }
    
    /*
    *
    */
    public void startChronometer (int limit ) {
    	isThreadAlive = true ;
        time = 0;
        this.limit = limit ;
        start () ;
    }
    /*
    *
    */
    public void stopChronometer () {
    	isThreadAlive = false ;
    }
    
    public int getType () {
        return type ;
    }
    
    public int getMaxExecutionTimes () {
        return this.maxExecutions ;
    }
    
    public long getProcessId () {
        return processId ;
    }
    public int getTimesExecuted() {
		return timesExecuted;
	}
    
    
    public void setMaxExecutions (int repeatLimit) {
    	this.maxExecutions = repeatLimit;
    }
    
    public void setType (int type) {
    	this.type = type;
    }
    
    public void setProcessId( int p) {
    	this.processId = p;
    }
    
    public boolean hasReachedMaxExecutions() {
    	return this.getTimesExecuted() == this.getMaxExecutionTimes();
    }
	
}
