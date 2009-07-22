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


package net.jxta.soap.j2se.bootstrap;

import org.apache.axis.server.AxisServer;
import org.apache.log4j.Logger;

/**
 * Bootstrap the JXTA bridge.
 *
 */
public class AXISBootstrap {
    
	private final static Logger LOG = Logger.getLogger(AXISBootstrap.class.getName());
	
    private AxisServer axisServer = null;
    
    /**
     * Instance member for <code>AXISBootstrap</code>
     */
    private static AXISBootstrap instance = null;

    private AXISBootstrap() {
		try{
			this.bootstrap();
		} catch(Exception e){
			LOG.error("Error bootstrapping AXIS", e);
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
        	LOG.info( "creating new instance..." );
            instance = new AXISBootstrap();
        }
        else
        	LOG.info( "return old instance..." );
        
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
