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


package net.jxta.soap.security.policy.transport;

import net.jxta.soap.security.policy.Policy;

import java.io.StringReader;
import java.util.Enumeration;
import java.util.HashMap;

import net.jxta.document.AdvertisementFactory;
import net.jxta.document.Attribute;
import net.jxta.document.Element;
import net.jxta.document.MimeMediaType;
import net.jxta.document.StructuredDocument;
import net.jxta.document.StructuredDocumentFactory;
import net.jxta.document.TextElement;
import net.jxta.document.XMLElement;
import net.jxta.protocol.PeerAdvertisement;

/**
 *                         (Val)     
 *                           |         
 *  -------------------------|----------
 *  | "ClientAdv"  | PeerAdvertisement |
 *  ------------------------------------
 *         K		         V
 *
 */ 
public class DefaultTLSPolicy implements Policy {
	
    private String policyName = "DefaultTLSPolicy";
    private String policyType = "TLS-based";
    private HashMap<String,Object> policyParams = null;
    private PeerAdvertisement clientAdv = null;
    
    public DefaultTLSPolicy() {
	policyParams = new HashMap<String,Object>();
	policyParams.put("ClientAdv", null);
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
	if( !StructuredDocument.class.isInstance( dataSource ) ) {
	    throw new IllegalArgumentException("Provided data source is not a StructuredDocument!");
	}
	
	Object[] params = new Object[ 1 ];
	initialize( (StructuredDocument) dataSource, params );
	return params;
    }
    
    /**
     * Parsing client auth request content
     * looking for all the required parameters	 
     */	 	
    private void initialize( Element root, Object[] params ) throws IllegalArgumentException { 
	if( !XMLElement.class.isInstance( root ) )
	    throw new IllegalArgumentException( "authRequest only supports XMLElement" );
	
	XMLElement doc = (XMLElement) root;	
	String typedoctype = "";	
	Attribute itsType = doc.getAttribute( "type" );
	if( null != itsType )
	    typedoctype = itsType.getValue();
	
	String doctype = doc.getName();		
	if( !doctype.equals("jxta:AuthRequest") && !typedoctype.equals("jxta:AuthRequest") )
	    throw new IllegalArgumentException( "Could not construct : "
					+ "authRequest from doc containing a " + doctype );
	
	Enumeration elements = doc.getChildren();	
	while (elements.hasMoreElements()) {
	    XMLElement elem = (XMLElement) elements.nextElement();		    
	    if( !handleElement( elem, params ) ) {
		System.out.println("Unhandled element '" + elem.getName() + "' in " + doc.getName() );
	    }
	}
    }
    
    /**
     * Handling each single authentication request element
     */	 	
    private boolean handleElement( XMLElement elem, Object[] params ) {
	//System.out.println(" Handle element: " + elem.getName() );
	// Client peer advertisement
	if( elem.getName().equals("ClientAdv")) {
	    String value = elem.getTextValue();
	    value = value.trim();			
	    try {
		StructuredDocument doc = StructuredDocumentFactory.newStructuredDocument(MimeMediaType.XMLUTF8, 
																				 new StringReader( elem.getTextValue() ) );
		clientAdv = (PeerAdvertisement) AdvertisementFactory.newAdvertisement( (XMLElement) doc );
	    } catch( Exception e ) {
		System.out.println("Exception in reading client advertisement!");
		e.printStackTrace();
	    }			
	    params[ 0 ] = clientAdv;			
	    return true;
	}
	
	// element was not handled
	return false;
    }
    
    /**
     * Check provided parameters validity
     */	 		
    public boolean checkParams( Object[] params ) {
	System.out.println("### DEFAULT SECURITY POLICY - CHECK PARAMS ###");
	if( policyParams.size() != params.length ) {
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
	return true;
    }
    
    public Object getAuthenticationParam() {
	return clientAdv;
    }
    
    /**
     * Build a StructuredDocument which includes the policy description
     * and all the authentication requirements 
     */	 	 	
    public StructuredDocument getDocument() {
	StructuredDocument policyCharterDoc = 
	    StructuredDocumentFactory.newStructuredDocument(new MimeMediaType("text/xml"), "Invocation-Charter");
	
	TextElement pcNameElem = (TextElement) policyCharterDoc.createElement("PCName", getName());
	policyCharterDoc.appendChild( pcNameElem );	
	
	TextElement pcTypeElem = (TextElement) policyCharterDoc.createElement("PCType", getType());
	policyCharterDoc.appendChild( pcTypeElem );	
	
	String pcDesc = "Requirements:ClientAdv#";
	TextElement pcDescElem = (TextElement) policyCharterDoc.createElement("PCDesc", pcDesc);
	policyCharterDoc.appendChild( pcDescElem );
	
	String clientAdvDesc = "";
	TextElement pcClientAdvElem = (TextElement) policyCharterDoc.createElement("ClientAdv", clientAdvDesc);
	policyCharterDoc.appendChild( pcClientAdvElem );
	
	return policyCharterDoc;
    }
    
}
