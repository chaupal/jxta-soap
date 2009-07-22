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

import net.jxta.soap.j2se.security.policy.Policy;
import net.jxta.soap.j2se.security.wss4j.WSSecurity;

import java.security.cert.X509Certificate;
import java.util.HashMap;
 
import net.jxta.document.MimeMediaType;
import net.jxta.document.StructuredDocument;
import net.jxta.document.StructuredDocumentFactory;
import net.jxta.document.TextElement;

import org.apache.axis.Message;
import org.apache.axis.AxisFault;
import org.apache.axis.message.SOAPEnvelope;
import org.w3c.dom.Document;

/**
 */ 
public class DefaultWSSPolicy implements Policy {
	
    private String policyName = "DefaultWSSPolicy";
    private String policyType = "WSS-based";
    private HashMap<String,Object> policyParams = null;
    private X509Certificate clientX509Cert = null;
    
    public DefaultWSSPolicy() {
	policyParams = new HashMap<String,Object>();
	policyParams.put("ClientX509Cert", null);
    }	
    
    public String getName() {
	return policyName;
    }	
    
    public String getType() {
	return policyType;
    }	
    
    /**
     * Returns the parameters extracted from client authentication request
     */	 		
	public Object[] extractParams( Object dataSource ) throws IllegalArgumentException {
	    if( !Message.class.isInstance( dataSource ) ) {
		throw new IllegalArgumentException("Provided data source is not an axis message!");
	    }
	    
	    Object[] params = new Object[ 3 ];			
	    initialize( (Message) dataSource, params );
	    return params;
	}
    
    /**
     * Parsing client auth request content
     * looking for all the required parameters	 
     */	 	
    private void initialize( Message soapMsg, Object[] params ) {
	try {
	    params[ 0 ] = soapMsg.getSOAPEnvelope();				// Message envelope 
	    params[ 1 ] = soapMsg.getSOAPEnvelope().getHeaders();	// Message headers vector
	    params[ 2 ] = soapMsg.getSOAPEnvelope().getBodyElements();	// Message bodies vector
	} catch( AxisFault af ) {
	    System.out.println("Exception in getting SOAP message elements!");
	    af.printStackTrace();
	}
    }
    
    
    /**
     * Check provided parameters validity
     */	 		
    public boolean checkParams( Object[] params ) {
	System.out.println("### DEFAULT WSS SECURITY POLICY - CHECK PARAMS ###");
	if( policyParams.size() + 2 != params.length ) {
	    System.out.println("Specified parameters set do not bind to policy params set!");
	    return false;
	}
	// Check parameter integrity
	for( int i = 0; i < policyParams.size(); i++ ) {
	    if( params[ i ] == null ) {
		System.out.println("Parameter #" + (i+1) + " is null!");
		return false;
	    }
	}
	
	// Process security header
	Document wssDoc = null;
	try {
	    wssDoc = ((SOAPEnvelope) params[ 0 ]).getAsDocument();
	} catch( Exception e ) {
	    System.out.println("Exception in converting SOAP Envelope to W3C Document!");
	    e.printStackTrace();
	    return false;
	}
	
	// Instantiating the WSS manager object for checking message content
	WSSecurity wssManager = new WSSecurity( ((SOAPEnvelope) params[ 0 ]).toString() );
	try {
	    clientX509Cert = wssManager.verifyEncryptionSTR( wssDoc );
	    System.out.println("Encryption verification OK!");
	} catch( Exception e ) {
	    System.out.println("Encryption verification FAILED!");
	    e.printStackTrace();		
	    return false;
	}
	
	// Extracting user X509 certificate
	//...
	
	return true;
    }
    
    public Object getAuthenticationParam() {
	return clientX509Cert;
    }
    
    /**
     * Build a StructuredDocument which includes the policy description
     * and all the authentication requirements 
     */	 	 	
    public StructuredDocument getDocument() {
	StructuredDocument policyCharterDoc = 
	    StructuredDocumentFactory.newStructuredDocument(new MimeMediaType("text/xml"), "Invocation-Charter");
	
	TextElement pcNameElem = (TextElement) 
	    policyCharterDoc.createElement("PCName", getName());
	policyCharterDoc.appendChild( pcNameElem );	
	
	TextElement pcTypeElem = (TextElement) 
	    policyCharterDoc.createElement("PCType", getType());
	policyCharterDoc.appendChild( pcTypeElem );	
	
	String pcDesc = "Requirements:ClientX509Cert#";
	TextElement pcDescElem = (TextElement) policyCharterDoc.createElement("PCDesc", pcDesc);
	policyCharterDoc.appendChild( pcDescElem );
	
	String clientX509Desc = "";
	TextElement pcClientX509Elem = (TextElement) policyCharterDoc.createElement("ClientX509Cert", clientX509Desc);
	policyCharterDoc.appendChild( pcClientX509Elem );
	
	return policyCharterDoc;
    }
    
}
