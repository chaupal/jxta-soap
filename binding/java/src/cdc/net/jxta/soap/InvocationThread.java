/*
 * Copyright (c) 2005 JXTA-SOAP Project
 *
 * Redistributions in source code form must reproduce the above copyright
 * and this condition. The contents of this file are subject to the
 * Sun Project JXTA License Version 1.1 (the "License"); you may not use
 * this file except in compliance with the License.
 * A copy of the License is available at http://www.jxta.org/jxta_license.html.
 *
 */


package net.jxta.soap;

import net.jxta.peer.PeerID;
import net.jxta.pipe.InputPipe;

public class InvocationThread extends Thread {

	private SOAPService service = null;
	private PeerID clientID = null;
	private InputPipe serviceSecurePipe = null;
	
	public InvocationThread( SOAPService service, PeerID clientID, 
							 InputPipe serviceSecurePipe ) {
		this.service = service;
		this.clientID = clientID;
		this.serviceSecurePipe = serviceSecurePipe;
	}
	
	public void run() {	
		while( true ) {
			try {
				// Wait for incoming messages				
				service.acceptOnSecurePipe( serviceSecurePipe );
					
			} catch( Exception e ) {
				e.printStackTrace();
			}
		}	
	}
	
	public PeerID getClientID() {
		return clientID;
	}
	
	public InputPipe getSecurePipe() {
		return serviceSecurePipe;
	}
	
}
