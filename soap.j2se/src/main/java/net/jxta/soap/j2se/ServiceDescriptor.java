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

import java.util.Enumeration;
import java.util.ArrayList;

import net.jxta.document.StructuredTextDocument;
import net.jxta.document.TextElement;
import net.jxta.peergroup.PeerGroup;
import net.jxta.protocol.ModuleSpecAdvertisement;


/**
 * Describes all information that a JXTA Service needs to run with.
 */
public class ServiceDescriptor {
    private String peerGroupDescription = "no description";
    private String peerGroupName = "no name";
    private long timeout = 5000;   // changed from
    private String description = null;
    private String specURI = null;
    private String creator = null;
    private String version = null;
    private String classname = null;
    private String name = null;
    private boolean securityEnabled = false;
    private String policyType = "unset";
    private String peerGroupID = null; 
    private ArrayList complexTypeNames = null;
    private ArrayList complexTypePackages = null;
    
    /**
     * Create a new <code>ServiceDescriptor</code> instance.
     */
    public ServiceDescriptor(String classname,
			     String name,
			     String version,
			     String creator,
			     String specURI,
			     String description) {
	
        this.classname = classname;
        this.name = name;
        this.version = version;
        this.creator = creator;
        this.specURI = specURI;
        this.description = description;
        complexTypeNames = new ArrayList();
        complexTypePackages = new ArrayList();
    }


    /**
     * 
     * Create a new <code>ServiceDescriptor</code> instance from a Module Spec
     * Adv and a peergroup. Useful for clients, they should
     * not need the pre-made server side ServiceDescriptor, so they create one
     * from the msadv and peergroup, which they should have first.
     *
     * The classname is only needed if the wsdl-enhanced calls will be used
     * (see SOAPService.java and CallFactory.java). Otherwise, put null.
     *
     */
    public ServiceDescriptor(String classname,
			     ModuleSpecAdvertisement msadv,
			     String policyType,
			     PeerGroup peergroup) {
	this.classname = classname;
	this.name = msadv.getName();
	this.version = msadv.getVersion();
	this.creator = msadv.getCreator();
	this.specURI = msadv.getSpecURI();
	this.description = msadv.getDescription();
	this.peerGroupID = peergroup.getPeerGroupID().toString();
	this.peerGroupName = peergroup.getPeerGroupName();
	this.peerGroupDescription = peergroup.getPeerGroupAdvertisement().getDescription();
	// Extract security information from msadv Param section
	this.securityEnabled = isSecure( msadv );
	if( policyType != null )
	    this.policyType = policyType;
	complexTypeNames = new ArrayList();
	complexTypePackages = new ArrayList();
    }
    
    /**
     * Create a new <code>ServiceDescriptor</code> instance.
     */
    public ServiceDescriptor(String classname,
			     String name,
			     String version,
			     String creator,
			     String specURI,
			     String description,
			     String peerGroupID) {
	
        this.classname = classname;
        this.name = name;
        this.version = version;
        this.creator = creator;
        this.specURI = specURI;
        this.description = description;
        this.peerGroupID = peerGroupID;
        complexTypeNames = new ArrayList();
        complexTypePackages = new ArrayList();
    }

    /**
     * Create a new <code>ServiceDescriptor</code> instance.
     */
    public ServiceDescriptor(String classname,
			     String name,
			     String version,
			     String creator,
			     String specURI,
			     String description,
			     String peerGroupID,
			     String peerGroupName,
			     String peerGroupDescription,
			     boolean secure,
			     String policyType) {

        this.classname = classname;
        this.name = name;
        this.version = version;
        this.creator = creator;
        this.specURI = specURI;
        this.description = description;
        this.peerGroupID = peerGroupID;
        this.peerGroupName = peerGroupName;
        this.peerGroupDescription = peerGroupDescription;
        this.securityEnabled = secure;
	if( policyType != null )
	    this.policyType = policyType;
		complexTypeNames = new ArrayList();
		complexTypePackages = new ArrayList();
    }

    /**
     * Utility method for extracting security information from
     * msadv Param section
     */
    private boolean isSecure( ModuleSpecAdvertisement msadv ) {
	StructuredTextDocument param = (StructuredTextDocument) msadv.getParam();
	Enumeration params = param.getChildren();
	while( params != null && params.hasMoreElements() ) {
	    TextElement elem = (TextElement) params.nextElement();				
	    // Check for SECURE tag
	    if( elem.getName().equals("secure") ) {
		if( elem.getTextValue().equals("true") )  
		    return true;
		else
		    return false;
	    }
	}	
	return false;
    }	 	 	
    
    /** 
     * Print out the fields of the ServiceDescriptor
     */     
    public String toString() {
    	
    	String complexTypeMapping = "Complex type mapping: \n";
    	for (int i = 0; i < this.getComplexTypeMappingSize(); i++) {
    		complexTypeMapping += this.getComplexTypeName(i) + "\n";
    		complexTypeMapping += this.getComplexTypePackage(i) + "\n";
    	}
    	
    	return  "--- Service Descriptor ---\n" + 
	    	"Description: " + getDescription() + "\n" + 
	    	"SpecURI: " + getSpecURI() + "\n" + 
	    	"Classname: " + getClassname() + "\n" + 
	    	"Name: " + getName() + "\n" + 
	    	"PeerGroupID: " + getPeerGroupID() + "\n" + 
	    	"PeerGroupName: " + getPeerGroupName() + "\n" + 
	    	"PeerGroupDescription: " + getPeerGroupDescription() + "\n" + 
	    	"Timeout: " + getTimeout() + "\n" + 
	    	"Creator: " + getCreator() + "\n" + 
	    	"Version: " + getVersion() + "\n" + 
	    	"Secure: " + isSecure() + "\n" +
	    	"Policy Type: " + getPolicyType() + "\n" + 
	    	complexTypeMapping +
	    	"--------------------------";
    }	
    
    
    public void addComplexTypeMapping(String complexTypeName, String complexTypePackage) {
    	complexTypeNames.add(complexTypeName);
    	complexTypePackages.add(complexTypePackage);
    }
       
    public int getComplexTypeMappingSize() {
    	return complexTypeNames.size();
    }
    
    public String getComplexTypeName(int i) {
    	return (String) complexTypeNames.get(i);
    }
    
    public String getComplexTypePackage(int i) {
    	return (String) complexTypePackages.get(i);
    }
    
    
    /**
     * Get the value of <code>description</code>.
     */
    public String getDescription() {    
        return this.description;
    }

    /**
     * Set the value of <code>description</code>.
     */
    public void setDescription( String description ) { 
        this.description = description;
    }
    
    /**
     * Get the value of <code>specURI</code>.
     */
    public String getSpecURI() { 
        return this.specURI;
    }
    
    /**
     * Set the value of <code>specURI</code>.
     */
    public void setSpecURI( String specURI ) {    
        this.specURI = specURI;
    }

    /**
     * Get the value of <code>creator</code>.
     */
    public String getCreator() { 
        return this.creator;
    }

    /**
     * Set the value of <code>creator</code>.
     */
    public void setCreator( String creator ) { 
        this.creator = creator;
    }

    /**
     * Get the value of <code>version</code>.
     */
    public String getVersion() { 
        return this.version;
    }

    /**
     * Set the value of <code>version</code>.
     */
    public void setVersion( String version ) { 
        this.version = version;
    }

    /**
     * Get the value of <code>classname</code>.
     */
    public String getClassname() { 
        return this.classname;
    }

    /**
     * Set the value of <code>classname</code>.
     */
    public void setClassname( String classname ) { 
        this.classname = classname;
    }

    /**
     * Get the value of <code>name</code>.
     */
    public String getName() { 
        return this.name;
    }

    /**
     * Set the value of <code>name</code>.
     */
    public void setName( String name ) { 
        this.name = name;
    }

    /**
     * If true, use secure pipes.
     */
    public void setSecure( boolean secure ) {
        this.securityEnabled = secure;
    }

    /**
     * Specifies security policy type (transport or message)
     */
    public void setPolicyType( String type ) {
        this.policyType = type;
    }

    /**
     * Returns the service security policy type (transport or message)
     */
    public String getPolicyType() {
        return this.policyType;
    }

    /**
     * Return true if we should use secure pipes.
     */
    public boolean isSecure() {
        return this.securityEnabled;
    }

    /**
     * Set the value of <code>timeout</code>.
     */
    public void setTimeout( long timeout ) { 
        this.timeout = timeout;
    }

    /**
     * Get the value of <code>timeout</code>.
     */
    public long getTimeout() { 
        return this.timeout;
    }

    /**
     * Get the value of <code>peerGroupID</code>.    
     */
    public String getPeerGroupID() {
        return this.peerGroupID;
    }

    /**
     * Set the value of <code>peerGroupID</code>.    
     */
    public void setPeerGroupID( String peerGroupID ) {
        this.peerGroupID = peerGroupID;
    }

    /**
     * Set the value of <code>peerGroupName</code>.
     */
    public void setPeerGroupName( String peerGroupName ) { 
        this.peerGroupName = peerGroupName;
    }

    /**
     * Get the value of <code>peerGroupName</code>.
     */
    public String getPeerGroupName() {   
        return this.peerGroupName;
    }

    /**
     * Get the value of <code>peerGroupDescription</code>.
     */
    public String getPeerGroupDescription() { 
        return this.peerGroupDescription;
    }

    /**
     * Set the value of <code>peerGroupDescription</code>.
     */
    public void setPeerGroupDescription( String peerGroupDescription ) { 
        this.peerGroupDescription = peerGroupDescription;
    }

}
