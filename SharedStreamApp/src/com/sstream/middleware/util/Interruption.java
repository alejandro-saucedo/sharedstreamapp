package com.sstream.middleware.util;

/**
 * Sistemas Distribuidos
 * Miercoles 5 de Febrero 2014
 * 
 * CINVESTAV
 * 
 * Practica 1.
 * Middleware Distribuido
 * 
 * Autores:
 * 
 * Juan Carlos Reyes Martinez
 * Hector Alejandro Saucedo Briseno.
 * Luis Angel Ramos Cobarruvias
 * 
 * 
 */
import java.net.InetAddress;

public interface Interruption {

	public void doInterruption (long processId);
}
