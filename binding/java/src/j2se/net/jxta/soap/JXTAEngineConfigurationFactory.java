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

import org.apache.axis.EngineConfigurationFactory;
import org.apache.axis.EngineConfiguration;
import org.apache.axis.configuration.XMLStringProvider;
import org.apache.axis.deployment.wsdd.WSDDConstants;
import org.apache.log4j.Logger;

/**
   This is used by <code>DeploySOAPService</code> when the WSDL
   methods are used, for complicated reasons to do with how Axis'
   Service constructors work.
*/
public class JXTAEngineConfigurationFactory implements EngineConfigurationFactory
{

	private final static Logger LOG = Logger.getLogger(JXTAEngineConfigurationFactory.class.getClass());
	
    public static EngineConfigurationFactory newFactory(Object param) {
        if (param != null)
            return null;  // not for us.
        return new JXTAEngineConfigurationFactory();
    }


    protected JXTAEngineConfigurationFactory() {
    }


     /**
      * Get client engine configuration.
      *
      * @return a client EngineConfiguration
      */
    public EngineConfiguration getClientEngineConfig() {
		LOG.info("JXTAEngineConfigurationFactory:Returning JXTA configuraton");
        return new XMLStringProvider( getWSDD() );
    }


    /**
     * Get server engine configuration.
     *
     * Not sure about this one, but I don't think it's used in
     * JXTA-SOAP.
     *
     * @return a server EngineConfiguration
     */
    public EngineConfiguration getServerEngineConfig() {
        return new XMLStringProvider( getWSDD() );
    }

    
    /*
     * Helper method
     */
    private String getWSDD() {
        //FIXME: I know this is ugly: migrate this to use JDOM so it is more
        //readable?

        //NOTE: by default we allow all methods.  This was easier to do by
        //default because we don't have to use reflection.  Consider changing
        //this in the future
        
	return "<deployment xmlns=\"" + WSDDConstants.URI_WSDD + "\" " +
	    "            xmlns:java=\"" + WSDDConstants.URI_WSDD_JAVA + "\">\n" +
	    "    <handler name=\"JXTASOAPTransportSender\" type=\"java:net.jxta.soap.transport.JXTASOAPTransportSender\"/>\n" +
	    "    <transport name=\"JXTASOAPTransport\" pivot=\"JXTASOAPTransportSender\"/>\n" +
	    //                "     <service name=\"" + descriptor.getName() + "\" provider=\"java:RPC\">\n" +
	    //                "         <parameter name=\"allowedMethods\" value=\"*\"/>\n" +
	    //                "         <parameter name=\"className\" value=\"" + descriptor.getClassname() + "\"/>\n" +
	    //                "     </service>\n" +
	    "</deployment>";
    }

} 
