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


import net.jxta.soap.ServiceDescriptor;

/**
 * Simple SOAP service for saying "hello"
 */

public class HelloService {

    private static int counter = 0;
    
    public HelloService() {
        System.out.println("### CALL TO HELLOSERVICE CONSTRUCTOR! ###");
    }
    
    public static final ServiceDescriptor DESCRIPTOR =
        new ServiceDescriptor("HelloService",     // class
        "HelloService",                       // name
        "0.2",                                // version
        "Distributed Systems Group",          // creator
        "jxta:/a.very.unique.spec/uri",       // specURI
        "The simple hello service example",   // description
        "urn:jxta:uuid-9AB310FDB28043008B69B1865E15F35602",  // peergroup ID
        "PrivateJXTA-SOAPNetPeerGroup",                      // PeerGroup name
        "Private JXTA-SOAP NetPeerGroup",                    // PeerGroup description
        true,                   // secure policy flag (use default=false)
        "WSS-based");           // security policy type (use WSS-based policy)
        
    
    /**
     * Allow someone to say hello and tell them how many times this function
     * has been called
     */
    public static String sayHello( String message ) {
        System.out.println(" <<< Invocating sayHello() >>>");
        return "How is it going buddy! - " + message + " Called " + ++counter + " times";
    }
    
}
