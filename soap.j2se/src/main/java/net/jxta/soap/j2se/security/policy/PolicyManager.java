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


package net.jxta.soap.j2se.security.policy;


import java.util.Iterator;
import java.util.LinkedList;

/**
 * This class handles service invocation security policies.
 * The default policy allows only to instantiate a secure connection
 * (TLS-based) between the client and the server, but a custom number 
 * of policies can be adopted in addition, more suitable to specific service
 * requirements.
 */  
public class PolicyManager {
	
    private LinkedList<Policy> registeredPolicies = null;
    
    /**
     * Standard constructor
     */	 	
    public PolicyManager() {
	registeredPolicies = new LinkedList<Policy>();
	// Add default security policy
	registerPolicy( new DefaultTLSPolicy() );
    }
    
    /**
     * Standard constructor
     */	 	
    public PolicyManager( Policy policy ) {
	registeredPolicies = new LinkedList<Policy>();
	// Add default security policy
	registerPolicy( policy );
    }
    
    /**
     * Add a new policy to the registered policies
     */	 	
    public void registerPolicy( Policy policy ) {
	registeredPolicies.add( policy );
    }
    
    /**
     * Replace the specified policy with a new one
     */	 	
    public void replacePolicy( Policy oldpolicy, Policy newpolicy ) {
	registeredPolicies.remove( oldpolicy );	
	registeredPolicies.add( newpolicy );
    }
    
    /**
     * Returns the requested policy specification or 
     * <code>null</code> if it is not found	 
     */	 		
    public Policy getRegisteredPolicy( String name ) {
	Iterator<Policy> listIterator = registeredPolicies.iterator();
	while( listIterator.hasNext() ) {
	    Policy policy = listIterator.next();
			if( policy.getName().equals(name) )
			    return policy;
	}		
	return null;
    }
    
    /**
     * Returns the parameters extracted from client authentication request
     */	 		
    public Object[] extractParams( String name, Object dataSource ) {
	if( getRegisteredPolicy(name) == null ) {
	    System.out.println("Specified policy [" + name + "] is not registered!");
			return null;
	}
	return getRegisteredPolicy(name).extractParams( dataSource );
    }
    
    /**
     * Returns true if the provieded user parameters is suitable
     * to the registered policy, false otherwise.
     */	 		
    public boolean checkParams( String name, Object[] params ) {
		if( getRegisteredPolicy(name) == null ) {
		    System.out.println("Specified policy [" + name + "] is not registered!");
		    return false;
		}
		return getRegisteredPolicy(name).checkParams( params );
    }
    
    /**
     * Returns the authentication parameter which will be saved
     * in authentication list
     */	 
    public Object getAuthenticationParam( String name ) {
	if( getRegisteredPolicy(name) == null ) {
	    System.out.println("Specified policy [" + name + "] is not registered!");
	    return null;
	}
	return getRegisteredPolicy(name).getAuthenticationParam();	
    }
}
