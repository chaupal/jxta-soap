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


package net.jxta.soap.cdc.deploy;

import net.jxta.soap.cdc.bootstrap.AXISBootstrap;
import net.jxta.soap.cdc.ServiceDescriptor;

import java.io.ByteArrayInputStream;
import javax.xml.namespace.QName;
import javax.xml.rpc.ServiceException;

import org.apache.axis.AxisProperties;
import org.apache.axis.client.Service;
import org.apache.axis.configuration.XMLStringProvider;
import org.apache.axis.deployment.wsdd.WSDDConstants;
import org.apache.axis.EngineConfigurationFactory;
import org.apache.axis.MessageContext;
import org.apache.axis.server.AxisServer;
import org.apache.axis.utils.Admin;
import org.apache.axis.utils.XMLUtils;

import org.w3c.dom.*;

//import org.apache.log4j.Level;
//import org.apache.log4j.Logger;


public class SOAPServiceDeployer {

//    private final static Logger LOG = Logger.getLogger(SOAPServiceDeployer.class.getName());
    private ServiceDescriptor descriptor = null;
    private String wsdd = null;
    
    /**
     * Create a new <code>DeploySOAPService</code> instance.
     */
    public SOAPServiceDeployer( ServiceDescriptor descriptor ) {

        this.descriptor = descriptor;
        
        //FIXME: I know this is ugly: migrate this to use JDOM so it is more
        //readable?
	
        //NOTE: by default we allow all methods.  This was easier to do by
        //default because we don't have to use reflection.  Consider changing
        //this in the future
                
        this.wsdd = "<deployment xmlns=\"" + WSDDConstants.URI_WSDD + "\" " + 
            "            xmlns:java=\"" + WSDDConstants.URI_WSDD_JAVA + "\">\n" +
            "    <handler name=\"JXTASOAPTransportSender\" type=\"java:net.jxta.soap.JXTASOAPTransportSender\"/>\n" +
            "    <transport name=\"JXTASOAPTransport\" pivot=\"JXTASOAPTransportSender\"/>\n" +
            "     <service name=\"" + descriptor.getName() + "\" provider=\"java:RPC\">\n" +
            "         <parameter name=\"allowedMethods\" value=\"*\"/>\n" +
            "         <parameter name=\"className\" value=\"" + descriptor.getClassname() + "\"/>\n" +
            "     </service>\n" +
            "</deployment>";
        
    }

    /**
     * Deploy services on specified networks.
     */
    public void deploy( String wsddSvc ) throws Exception {

        System.out.println( "-> DeploySOAPService:deploy() - Deploying SOAP service..." );

        Admin admin = new Admin();

        System.out.println( "-> DeploySOAPService:deploy() - Get AxisServer..." );
        AxisServer server = AXISBootstrap.getInstance().getAxisServer();
        
        System.out.println( "-> DeploySOAPService:deploy() - Create MessageContext..." );
        MessageContext context = new MessageContext( server );

        System.out.println( "-> DeploySOAPService:deploy() - GetWSDD()..." );
        if( wsddSvc != null )
            wsdd = wsddSvc;

        //if( LOG.isEnabledFor(Level.INFO) )
        //    System.out.println("WSDD: " + wsdd );
        
        Document doc = XMLUtils.newDocument( new ByteArrayInputStream( wsdd.getBytes() ) );
        
        System.out.println( "-> DeploySOAPService:deploy() - admin.process(...)" );
        admin.process( context, doc.getDocumentElement() );
        
        System.out.println( "-> DeploySOAPService:deploy() - Deploying SOAP service...done" );

    }

    /**
     * Get a SOAP Service from the current ServiceDescriptor
     */
    public Service getService() {

        Service  service = new Service( new XMLStringProvider( getWSDD()  ) );
        return service;
        
    }

    /**
     * Get a SOAP Service using WSDL.
     *
     * Servicename should have the same namespace as the "targetNamespace"
     * at the top of the wsdl file.
     */
    public Service getService(String wsdlLocation, QName servicename) {
        try{
            if( descriptor.getName() == null ) {
                System.out.println("You must set the classname of the service descriptor" +
                " in order to use the wsdl-enhanced service");
                return null;
            }
            
            AxisProperties.setProperty(EngineConfigurationFactory.SYSTEM_PROPERTY_NAME, 
            "net.jxta.soap.JXTAEngineConfigurationFactory");
            
            Service service = new Service( wsdlLocation, servicename );
            
            return service;
        
        } catch( ServiceException e ){
            System.out.println("DeploySOAPService.getService() failed");
            e.printStackTrace();
            System.exit(1);
        }
        
        return null;
    }
    
    
    /**
     * Return the Web Service Deployment Descriptor
     */
    private String getWSDD() {
        
        return wsdd;
    
    }
    
}
