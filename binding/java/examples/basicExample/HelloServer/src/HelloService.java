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

 
import java.util.Map;

import javax.xml.rpc.ServiceException;
import javax.xml.rpc.server.ServiceLifecycle;

import net.jxta.soap.ServiceDescriptor;

/**
 * Simple SOAP service for saying "hello"
 */
public class HelloService implements ServiceLifecycle{
    private static int counter = 0;
    private String serviceName = null;
    
    public HelloService() {
        System.out.println("### CALL TO HELLOSERVICE CONSTRUCTOR! ###");
    }
    
    /**
     * @see javax.xml.rpc.server.ServiceLifecycle#init(java.lang.Object)
     */
    public void init(Object context) throws ServiceException {
	System.out.println(" <<< Invocating init() >>>");
	
	if (context instanceof Map) {
	    Map parameters = (Map) context;
	    String serviceName = (String) parameters.get("serviceName");
	    
	    if (serviceName != null) {
		System.out.println(" <<< Initializing serviceName with value " + serviceName + " >>>");
	    }
	}
    }
    
    /**
     * @see javax.xml.rpc.server.ServiceLifecycle#destroy()
     */
    public void destroy() {
	System.out.println(" <<< Invocating destroy() >>>");
    }
    
    public static final ServiceDescriptor DESCRIPTOR =
        new ServiceDescriptor("HelloService",     // class
        "HelloService",                       // name
        "0.2",                                // version
        "Distributed Systems Group",          // creator
        "jxta:/a.very.unique.spec/uri",       // specURI
        "The simple hello service example",   // description
        "urn:jxta:jxta-NetGroup",  // peergroup ID
        "JXTA NetPeerGroup",                      // PeerGroup name
        "JXTA NetPeerGroup",                    // PeerGroup description
        false,            // secure policy flag (use default=false)
        null);            // security policy type (use no policy)
    
    
    /**
     * Allow someone to say hello and tell them how many times this function
     * has been called
     */
    public static String sayHello( String message ) {	
        //System.out.println(" <<< Invocating sayHello() >>>");
        //return "How is it going buddy! - " + message + " Called " + ++counter + " times";
        return "RESPONSE";
    }
}
