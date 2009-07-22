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

package net.jxta.soap.j2se;

import net.jxta.peer.PeerID;
import net.jxta.pipe.InputPipe;

public class SOAPServiceThread extends Thread {

	private SOAPService service = null;

	public SOAPServiceThread(SOAPService service) {
		super();
		this.setDaemon(true);

		this.service = service;
	}

	public void run() {
		while (true) {
			try {
				// Wait for incoming messages
				//service.acceptSingleThread(service.getInputPipe());
				service.acceptMultiThread(service.getInputPipe());
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
	}
}
