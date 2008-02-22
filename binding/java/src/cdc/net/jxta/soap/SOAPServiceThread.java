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

import net.jxta.soap.SOAPService;

/**
 * A thread that runs Services.
 *
 */
public class SOAPServiceThread extends Thread {

    private SOAPService service = null;
    
    /**
     * Create a new <code>ServiceThread</code> instance.
     */
    public SOAPServiceThread( SOAPService service ) {

        super();
        this.setDaemon( true );

        this.service = service;
    }

    /**
     * Wait forever for incoming messages on service public pipe    
     */
    public void run() {

        while ( true ) {

            try {            
                System.out.println("-> ServiceThread - START wait in accept(...) - PUBLIC Pipe");
                service.acceptOnPublicPipe( service.getInputPipe() );
            } catch ( Throwable t ) {
                
                t.printStackTrace();
                
            }
            
        } 
        
    }
    
}
