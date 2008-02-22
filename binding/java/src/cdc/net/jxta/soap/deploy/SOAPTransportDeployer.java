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

package net.jxta.soap.deploy;

import net.jxta.soap.bootstrap.AXISBootstrap;

import java.io.ByteArrayInputStream;

import org.apache.axis.deployment.wsdd.WSDDConstants;
import org.apache.axis.MessageContext;
import org.apache.axis.server.AxisServer;
import org.apache.axis.utils.Admin;
import org.apache.axis.utils.XMLUtils;

import org.w3c.dom.Document;

/**
 * Deploy the JXTA SOAP Transport so that Calls can use JXTA. This needs to be
 * called once per SOAP installation.
 * 
 * @author <a href="mailto:burton@openprivacy.org">Kevin A. Burton</a>
 */
public class SOAPTransportDeployer {

	/**
	 * Deploy services on specified networks.
	 */
	public void deploy() throws Exception {

		System.out.println("Deploying SOAP transport...");

		Admin admin = new Admin();

		AxisServer server = AXISBootstrap.getInstance().getAxisServer();

		MessageContext context = new MessageContext(server);

		String wsdd = getWSDD();

		Document doc = XMLUtils.newDocument(new ByteArrayInputStream(wsdd
				.getBytes()));

		admin.process(context, doc.getDocumentElement());

		System.out.println("Deploying SOAP transport...done");

	}

	private String getWSDD() {

		// FIXME: I know this is ugly: migrate this to use JDOM so it is more
		// readable?

		// NOTE: by default we allow all methods. This was easier to do by
		// default because we don't have to use reflection. Consider changing
		// this in the future

		return "<deployment name=\"bridge\" xmlns=\""
				+ WSDDConstants.URI_WSDD
				+ "\"\n"
				+ "            xmlns:java=\""
				+ WSDDConstants.URI_WSDD_JAVA
				+ "\">\n"
				+ "    <handler name=\"JXTASOAPTransportSender\" type=\"java:net.jxta.soap.transport.JXTASOAPTransportSender\"/>\n"
				+ "    <transport name=\"JXTASOAPTransport\" pivot=\"JXTASOAPTransportSender\"/>\n"
				+ "</deployment>";

	}

}
