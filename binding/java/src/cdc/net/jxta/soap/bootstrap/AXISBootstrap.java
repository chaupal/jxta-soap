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


package net.jxta.soap.bootstrap;

import org.apache.axis.server.AxisServer;

/**
 * Bootstrap the JXTA bridge.
 *
 */
public class AXISBootstrap {
    
    private AxisServer axisServer = null;
    
    /**
     * Instance member for <code>AXISBootstrap</code>
     */
    private static AXISBootstrap instance = null;

    private AXISBootstrap() {
		try{
			this.bootstrap();
		} catch(Exception e){
			System.out.println("Error bootstrapping AXIS");
			e.printStackTrace();
		}
    }

    /**
     * Create a new AxisServer instance and initialize it    
     */
    public void bootstrap() throws Exception {

        if ( this.axisServer == null ) {
            axisServer = new AxisServer();
            axisServer.init();
        } 
        
    }

    /**
     * Get an instance of the <code>AXISBootstrap</code>.
     */
    public static AXISBootstrap getInstance() {
        
        if ( instance == null ) {
        	System.out.println( "-> AXISBootstrap:getInstance() - creating new instance..." );
            instance = new AXISBootstrap();
        }
        else
        	System.out.println( "-> AXISBootstrap:getInstance() - return old instance..." );
        
        return instance;
        
    }

    /**
     * Set the value of <code>axisServer</code>.
     */
    public void setAxisServer( AxisServer axisServer ) { 
        
        this.axisServer = axisServer;
        
    }

    /**
     * Get the value of <code>axisServer</code>.
     */
    public AxisServer getAxisServer() { 
        
        return this.axisServer;
        
    }

}
