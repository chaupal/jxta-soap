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

package net.jxta.soap.cdc;

import net.jxta.soap.cdc.bootstrap.AXISBootstrap;
import net.jxta.soap.cdc.deploy.SOAPServiceDeployer;
import net.jxta.soap.cdc.security.policy.Policy;
import net.jxta.soap.cdc.util.URLBase64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.lang.Exception;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import net.jxta.discovery.DiscoveryService;
import net.jxta.document.AdvertisementFactory;
import net.jxta.document.Attributable;
import net.jxta.document.Element;
import net.jxta.document.MimeMediaType;
import net.jxta.document.StructuredDocument;
import net.jxta.document.StructuredDocumentFactory;
import net.jxta.document.StructuredDocumentUtils;
import net.jxta.document.StructuredTextDocument;
import net.jxta.document.TextElement;
import net.jxta.document.XMLDocument;
import net.jxta.document.XMLElement;
import net.jxta.endpoint.ByteArrayMessageElement;
import net.jxta.endpoint.InputStreamMessageElement;
import net.jxta.id.IDFactory;
import net.jxta.impl.document.LiteXMLDocument;
import net.jxta.peer.PeerID;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.pipe.InputPipe;
import net.jxta.pipe.OutputPipe;
import net.jxta.pipe.PipeService;
import net.jxta.platform.ModuleClassID;
import net.jxta.protocol.ModuleClassAdvertisement;
import net.jxta.protocol.ModuleSpecAdvertisement;
import net.jxta.protocol.PeerAdvertisement;
import net.jxta.protocol.PipeAdvertisement;

import org.apache.axis.AxisFault;
import org.apache.axis.Constants;
import org.apache.axis.Message;
import org.apache.axis.MessageContext;
import org.apache.axis.server.AxisServer;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * The SOAPService class supports a mechanism for deploying JXTA services and
 * also providing protocol independent operation. It handles SOAP service
 * requests.
 */
public class SOAPService {

	private final static Logger LOG = Logger.getLogger(SOAPService.class
			.getName());
	private String logLevel = "WARN";

	private LinkedList invocationThreadList = new LinkedList();

	private HashMap authUsers = new HashMap();

	private LinkedList secureInputPipeAdvList = new LinkedList();

	private LinkedList secureInputPipeList = new LinkedList();

	// private PolicyManager policyManager = null;
	private String policyName = null;
	private String policyType = null;
	private Object context = null;

	/**
	 * Internal class to periodically publish the module spec adv when it
	 * expires.
	 */
	private class AdvPublishTask extends TimerTask {

		int count;
		ModuleSpecAdvertisement msadv;

		public AdvPublishTask(ModuleSpecAdvertisement msadv) {
			this.msadv = msadv;
		}

		public boolean cancel() {
			System.out.println("Stopping advertisement-publishing thread");
			return super.cancel();
		}

		public void run() {
			System.out
					.println("Trying to publish a new Module Spec Advertisement (number "
							+ count++ + ")...");
			try {
				advertiseModuleSpec(msadv);
			} catch (Exception e) {
				System.out
						.println("Could not publish module spec advertisement!");
				e.printStackTrace();
			}
			System.out.println("...Success!");
		}

	}

	private ModuleSpecAdvertisement msadv = null;
	private AdvPublishTask advPublishTask = null;
	private Timer publishtimer = null;
	private PeerGroup pg = null;
	private ServiceDescriptor serviceDescriptor = null;
	private DiscoveryService discSvc = null;
	private PipeService pipeSvc = null;
	private PipeAdvertisement inputPipeAdvertisement = null;
	private InputPipe inputPipe = null;

	/**
	 * Standard constructor
	 */
	public SOAPService() {
		this("INFO");
	}

	/**
	 * Standard constructor
	 */
	public SOAPService(String logLevel) {
		this.logLevel = logLevel;
		LOG.setLevel(Level.toLevel(this.logLevel));
	}

	/**
	 * Init this service. This needs to be called at least once for each
	 * service. We handle advertisement publication, request setup, etc.
	 * 
	 * This method allows additional params in the module spec advertisement
	 */
	public void init(PeerGroup pg, ServiceDescriptor descriptor,
			StructuredTextDocument param, Policy servicePolicy)
			throws Exception {
		System.out.println("-> SOAPService:init()");
		this.pg = pg;

		if (pg == null)
			throw new Exception(
					"Must specify a PeerGroup to publish the service!");

		if (!descriptor.getPeerGroupID().equals(pg.getPeerGroupID().toString()))
			throw new Exception(
					"PeerGroup and Descriptor's PeerGroupID do not match!");

		this.setServiceDescriptor(descriptor);

		// ***CHIARA***
		// bootstrap Axis if it hasn't been done before.
		if (LOG.isEnabledFor(Level.INFO))
			System.out.println("-> SOAPService:init() - Axis Bootstrap");
		AXISBootstrap.getInstance().bootstrap();
		// ***CHIARA***

		this.discSvc = pg.getDiscoveryService();
		this.pipeSvc = pg.getPipeService();

		// advertise this service an its input pipe..
		if (LOG.isEnabledFor(Level.INFO))
			System.out.println("-> SOAPService:init() - advertisePublicPipe()");
		PipeAdvertisement pipeadv = this.advertisePublicPipe();

		// create service advertisement.
		if (LOG.isEnabledFor(Level.INFO))
			System.out
					.println("-> SOAPService:init() - advertiseModuleClass()");
		ModuleClassID mcID = advertiseModuleClass();

		// Setting up service security policy
		System.out.println("-> SOAPService:init() - Setting security policy");

		// Setup a thread to publish the module spec adv immediately, and
		// re-publish the advertisement every time it expires.
		msadv = createModuleSpecAdv(mcID, pipeadv, param, servicePolicy);
		this.advertiseModuleSpec(msadv);
		
		/*
		long exptime = discSvc.getAdvExpirationTime(msadv);
		System.out.println("Expiration time for module spec adv is " + exptime
				+ " ms");

		advPublishTask = new AdvPublishTask(msadv);
		publishtimer = new Timer();
		publishtimer.schedule(advPublishTask, exptime, exptime);
		 */
		
		// ok.. deploy this as a SOAP service with Axis
		if (LOG.isEnabledFor(Level.INFO))
			System.out.println("-> SOAPService:init() - Deploying SOAP service");

		// ***CHIARA***
		String wsdd = extractServiceWSDD(param);
		new SOAPServiceDeployer(descriptor).deploy(wsdd);
		// ***CHIARA***
	}

	/**
	 * Extract the service deployment descriptor from msadv Parm section
	 */
	private String extractServiceWSDD(StructuredTextDocument param) {
		String WSDDbuffer = null;
		String decodedWSDD = null;
		// Now get all fields
		Enumeration params = param.getChildren();
		TextElement elem;
		String testtext = new String();
		while (params != null && params.hasMoreElements()) {
			elem = (TextElement) params.nextElement();
			// Check for WSDD service description
			if (elem.getName().equals("WSDD")) {
				WSDDbuffer = new String(elem.getTextValue());
				try {
					decodedWSDD = new String(URLBase64.decode(WSDDbuffer
							.getBytes(), 0, WSDDbuffer.length()));
				} catch (Exception ce) {
					System.out.println("Exception in decoding WSDD content!");
					ce.printStackTrace();
					return null;
				}
			}
		}
		return decodedWSDD;
	}

	/**
	 * Stops the service-publishing thread, so service will disappear after next
	 * module service adv has expired.
	 */
	public void stop() {
		advPublishTask.cancel();
	}

	/**
	 * Creates a Module Spec Advertisement for the service. The same
	 * advertisement is expected to be used throughout the lifetime of the
	 * service (it is stored in the instance variable msadv).
	 */
	private ModuleSpecAdvertisement createModuleSpecAdv(ModuleClassID mcID,
			PipeAdvertisement pipeadv, StructuredTextDocument param,
			Policy servicePolicy) {
		// Create the Module Spec advertisement associated with the service
		// We build the module Spec Advertisement using the advertisement
		// Factory class by passing in the type of the advertisement we want
		// to construct. The Module Spec advertisement will contain all the
		// information necessary for a client to contact the service for
		// instance it will contain a pipe advertisement to be used to
		// contact the service

		ModuleSpecAdvertisement msadv = (ModuleSpecAdvertisement) AdvertisementFactory
				.newAdvertisement(ModuleSpecAdvertisement
						.getAdvertisementType());

		// Setup some of the information field about the servive. In this
		// example, we just set the name, provider and version and a pipe
		// advertisement. The module creates an input pipes to listen
		// on this pipe endpoint.

		msadv.setName(this.getServiceDescriptor().getName());
		msadv.setVersion(this.getServiceDescriptor().getVersion());
		msadv.setCreator(this.getServiceDescriptor().getCreator());
		msadv.setModuleSpecID(IDFactory.newModuleSpecID(mcID));
		msadv.setSpecURI(this.getServiceDescriptor().getSpecURI());
		msadv.setDescription(this.getServiceDescriptor().getDescription());

		// include the input pipe adv that this service is using.
		msadv.setPipeAdvertisement(pipeadv);

		// include the PeerID.
		// WARNING: there are some nasty class casting here!
		if (param == null) {
			// old version
			StructuredTextDocument newparam = (StructuredTextDocument) StructuredDocumentFactory
					.newStructuredDocument(new MimeMediaType("text/xml"),
							"Parm");

			TextElement element = newparam.createElement("peer-id", pg
					.getPeerID().toString());
			((LiteXMLDocument) newparam).appendChild(element);
			msadv.setParam(newparam);
		} else {
			// // add policy Invocation-Charter
			// if( this.getServiceDescriptor().isSecure() ) {
			// System.out.println("Service security policy:\t" + policyName +
			// "\tType: " + policyType );
			// StructuredDocumentUtils.copyElements(param, param.getParent(),
			// policyManager.getRegisteredPolicy( policyName
			// ).getDocument().getParent());
			// }
			// new version allowing additional params from the method argument
			TextElement element = param.createElement("peer-id", pg.getPeerID()
					.toString());
			((LiteXMLDocument) param).appendChild(element);
			msadv.setParam(param);
		}

		System.out.println("msadv:\n" + msadv.toString());
		return msadv;
	}

	/**
	 * New advertiseModuleSpec() method to publish the service adv.
	 */
	private void advertiseModuleSpec(ModuleSpecAdvertisement msadv)
			throws IOException {
		// Ok the Module advertisement was created, just publish
		// it in my local cache and into the NetPeerGroup.
		discSvc.publish(msadv);
		discSvc.remotePublish(msadv);
	}

	public void republish() {
		if (this.msadv != null)
			try {
				this.advertiseModuleSpec(this.msadv);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		else
			System.out.println("No service to republish!");
	}

	/**
	 * Run this JXTA-SOAP service.
	 */
	public void acceptMultiThread(InputPipe pipe) throws Exception {
		if (LOG.isEnabledFor(Level.INFO))
			System.out
					.println("-> SOAPService:acceptOnPublicPipe(...) - waitForMessage()");
		// Listen on the pipe for a client message
		net.jxta.endpoint.Message msg = pipe.waitForMessage();
		System.out
				.println("-> SOAPService:acceptOnPublicPipe(...) - message arrived!");

		// Read the message as
		ByteArrayMessageElement msgString = (ByteArrayMessageElement) msg
				.getMessageElement("message");
		if (LOG.isEnabledFor(Level.INFO))
			System.out
					.println("-> SOAPService:acceptOnPublicPipe(...) - get 'message' element: \n"
							+ msgString.toString());
		if (msgString.toString() == null) {
			throw new Exception(
					"Server: error could not find the 'message' tag!");
		}

		ByteArrayMessageElement remoteInputPipeAdvertisement = (ByteArrayMessageElement) msg
				.getMessageElement("remote-input-pipe");
		if (LOG.isEnabledFor(Level.INFO))
			System.out
					.println("-> SOAPService:acceptOnPublicPipe(...) - get 'remote-input-pipe' element: \n"
							+ remoteInputPipeAdvertisement.toString());
		if (remoteInputPipeAdvertisement == null) {
			throw new Exception(
					"Server: error could not find the 'remote-input-pipe' tag!");
		}

		// Ok... now bind back to the output pipe (remote pipe advertisement)
		StructuredDocument rpaDoc = StructuredDocumentFactory
				.newStructuredDocument(new MimeMediaType("text/xml"),
						new ByteArrayInputStream(remoteInputPipeAdvertisement
								.getBytes(true)));
		PipeAdvertisement rpa = (PipeAdvertisement) AdvertisementFactory
				.newAdvertisement((XMLElement) rpaDoc);

		if (LOG.isEnabledFor(Level.INFO))
			System.out.println("Client PeerID: " + rpa.getDescription());

		// bind an output pipe back.. It is important to do this BEFORE we call
		// service() because it is possible that we can't communicate to the
		// remote peer. If communication is impossible then we shouldn't attempt
		// to invoke this service because we couldn't give the the results if we
		// wanted to.

		int attempt = 1;
		long timeout = serviceDescriptor.getTimeout();
		boolean redo = true;
		OutputPipe output = null;
		do {
			try {
				System.out
						.print("-> SOAPService:acceptOnPublicPipe(...) - binding op with remote ip... ("
								+ attempt + ")\t");
				output = pipeSvc.createOutputPipe(rpa, timeout);
				System.out.println("OK");
				redo = false;
			} catch (Exception e) {
				System.err
						.println(" Exception in remote binding phase! TIMEOUT expired!");
				if (LOG.isEnabledFor(Level.WARN)) {
					e.printStackTrace();
				}
				attempt++;
				timeout *= 2; // "exponential back-off"
			}
		} while (redo && attempt < 5);

		if (attempt < 5) {
			// Check service invocation policy...

			// **********************************************
			// No service security policy is set
			// Go ahead with unsecure service invocation
			// **********************************************
			System.out.println("### NO SECURITY POLICY DEFINED ###");
			this.invokeService(output, msgString.toString());

		}
	}

	/**
	 * Run this JXTA-SOAP service.
	 */
	public void acceptSingleThread(InputPipe pipe) throws Exception {
		System.out
				.println("-> SOAPService:acceptOnSecurePipe(...) - waitForMessage()");
		// Listen on the pipe for a client message
		net.jxta.endpoint.Message msg = pipe.waitForMessage();
		System.out
				.println("-> SOAPService:acceptOnSecurePipe(...) - message arrived!");
		// go ahead and get the next message

		// Read the message as
		ByteArrayMessageElement msgString = (ByteArrayMessageElement) msg
				.getMessageElement("message");
		if (LOG.isEnabledFor(Level.INFO))
			System.out
					.println("-> SOAPService:acceptOnSecurePipe(...) - get 'message' element: \n"
							+ msgString.toString());

		ByteArrayMessageElement remoteInputPipeAdvertisement = (ByteArrayMessageElement) msg
				.getMessageElement("remote-input-pipe");
		if (LOG.isEnabledFor(Level.INFO))
			System.out
					.println("-> SOAPService:acceptOnSecurePipe(...) - get 'remote-input-pipe' element: \n"
							+ remoteInputPipeAdvertisement.toString());

		if (msgString.toString() == null) {
			throw new Exception("Server: error could not find the tag");
		}

		// ok... now bind back to the output pipe (remote pipe advertisement)
		StructuredDocument rpaDoc = StructuredDocumentFactory
				.newStructuredDocument(new MimeMediaType("text/xml"),
						new ByteArrayInputStream(remoteInputPipeAdvertisement
								.getBytes(true)));
		PipeAdvertisement rpa = (PipeAdvertisement) AdvertisementFactory
				.newAdvertisement((XMLElement) rpaDoc);

		// bind an output pipe back.. It is important to do this BEFORE we call
		// service() because it is possible that we can't communicate to the
		// remote peer. If communication is impossible then we shouldn't attempt
		// to invoke this service because we couldn't give the the results if we
		// wanted to.

		int attempt = 1;
		long timeout = serviceDescriptor.getTimeout();
		boolean redo = true;
		OutputPipe output = null;
		do {
			try {
				System.out
						.print("-> SOAPService:acceptOnSecurePipe(...) - binding op with remote ip... ("
								+ attempt + ")\t");
				output = pipeSvc.createOutputPipe(rpa, timeout);
				System.out.println("OK");
				redo = false;
			} catch (Exception e) {
				System.err
						.println(" Exception in remote binding phase! TIMEOUT expired!");
				if (LOG.isEnabledFor(Level.WARN)) {
					e.printStackTrace();
				}
				attempt++;
				timeout *= 2;
			}
		} while (redo && attempt < 5);

		if (attempt < 5) {
			System.out.println("OK");
			this.invokeService(output, msgString.toString());
		}
	}

	/**
	 * Invoke this service and send the response message
	 */
	private void invokeService(OutputPipe output, String requestMessage)
			throws Exception {
		net.jxta.endpoint.Message returnMessage = new net.jxta.endpoint.Message();

		// ok... now serve this...
		Request request = new Request();
		request.setMessage(requestMessage);
		System.out.println("-> SOAPService:invokeService(...) - invoking target service");
		Response response = this.serve(request);
		if (LOG.isEnabledFor(Level.INFO))
			System.out.println("-> SOAPService:invokeService(...) - set response message");

		ByteArrayMessageElement returnMessageElement = new ByteArrayMessageElement(
				"message", null, response.getMessage().getBytes(), null);
		returnMessage.addMessageElement(returnMessageElement);

		System.out.println("-> SOAPService:invokeService(...) - send back response message");
		output.send(returnMessage);
		output.close();
	}

	
	// ***CHIARA***
	// ***CHIARA***
	// ***CHIARA***
	/**
	 * Service this a connection (InputPipe and Output pipe)
	 */
	public Response serve(Request request) throws Exception {
		if (LOG.isEnabledFor(Level.INFO))
			System.out.println("-> SOAPService:service(...) - get AxisServer");

		AxisServer server = AXISBootstrap.getInstance().getAxisServer();

		if (LOG.isEnabledFor(Level.INFO))
			System.out
					.println("-> SOAPService:service(...) - create MessageContext");
		MessageContext msgContext = new MessageContext(server);

		// The following line puts the whole message as the SOAPPart which
		// means that attachments don't work.
		Message msg = new Message(request.getMessage());
		if (LOG.isEnabledFor(Level.INFO))
			System.out
					.println("-> SOAPService:service(...) - set request message");

		if (context != null) {
			if (LOG.isEnabledFor(Level.INFO))
				System.out
						.println("-> SOAPService:service(...) - set context object");
			msgContext.setProperty(Constants.MC_SERVLET_ENDPOINT_CONTEXT,
					context);
		}

		msgContext.setRequestMessage(msg);

		try {
			// System.out.println("Trying to invoke target service: " +
			// msgContext.getTargetService() );
			if (LOG.isEnabledFor(Level.INFO))
				System.out
						.println("-> SOAPService:service(...) - server.invoke(msgContext)");

			server.invoke(msgContext);
			msg = msgContext.getResponseMessage();

			if (LOG.isEnabledFor(Level.INFO))
				System.out
						.println("-> SOAPService:service(...) - got response message");

		} catch (AxisFault af) {
			System.out.println("Axis invoke() returned an AxisFault");
			msg = new Message(af);
			msg.setMessageContext(msgContext);

		} catch (Exception e) {
			System.out.println("Axis invoke() returned an Exception");
			msg = new Message(new AxisFault(e.toString()));
			msg.setMessageContext(msgContext);
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		msg.writeTo(baos);

		String sres = baos.toString();
		System.out
				.println("-> SOAPService:service(...) - create response and return it");
		Response response = new Response();
		response.setMessage(sres);

		return response;
	}

	/**
	 * Build the Authentication Response message to send to client peer with
	 * server peer advertisement and the service private secure pipe
	 */
	private net.jxta.endpoint.Message createAuthResponseMessage(String result,
			PeerAdvertisement serverAdv, PipeAdvertisement securePipe) {
		net.jxta.endpoint.Message returnMessage = new net.jxta.endpoint.Message();
		// Build response doc
		StructuredDocument authResponseDoc = StructuredDocumentFactory
				.newStructuredDocument(MimeMediaType.XMLUTF8,
						"jxta:AuthResponse");

		if (authResponseDoc instanceof XMLDocument) {
			((XMLDocument) authResponseDoc).addAttribute("xmlns:jxta",
					"http://jxta.org");
			((XMLDocument) authResponseDoc).addAttribute("xml:space",
					"preserve");
			((Attributable) authResponseDoc).addAttribute("type",
					"jxta:AuthResponse");
		}

		if (authResponseDoc instanceof Attributable) {
			((Attributable) authResponseDoc).addAttribute("type",
					"jxta:AuthResponse");
		}

		Element e = null;
		// 1. Authentication response
		e = authResponseDoc.createElement("AuthResult", result);
		authResponseDoc.appendChild(e);

		// 2. This Server Peer advertisement
		e = authResponseDoc.createElement("ServerAdv", serverAdv.toString());
		authResponseDoc.appendChild(e);

		// 3. Secure Pipe Advertisement
		if (securePipe == null)
			e = authResponseDoc.createElement("SecurePipe", securePipe);
		else
			e = authResponseDoc.createElement("SecurePipe", securePipe
					.toString());
		authResponseDoc.appendChild(e);
		try {
			InputStreamMessageElement returnMessageElement = new InputStreamMessageElement(
					"AUTHRESPONSE", MimeMediaType.XMLUTF8, authResponseDoc
							.getStream(), null);
			returnMessage.addMessageElement(null, returnMessageElement);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return returnMessage;
	}

	/**
	 * Advertise a public pipe for this service and return it.
	 */
	private PipeAdvertisement advertisePublicPipe() throws Exception {

		// Create a pipe advertisement for the Service. The client MUST use
		// the same pipe advertisement to talk to the server. When the client
		// discovers the module advertisement it will extract the pipe
		// advertisement to create its pipe. So, we are reading the pipe
		// advertisement from a default config file to ensure that the
		// service will always advertise the same pipe
		PipeAdvertisement pipeadv = this.getInputPipeAdvertisement();

		if (pipeadv == null) {
			pipeadv = (PipeAdvertisement) AdvertisementFactory
					.newAdvertisement(PipeAdvertisement.getAdvertisementType());

			PeerGroupID pgid = pg.getPeerGroupID();
			pipeadv.setPipeID(IDFactory.newPipeID(pgid));
			pipeadv.setName(this.getServiceDescriptor().getName());
			pipeadv.setType("JxtaUnicast");
		}

		// save current service input pipe for incoming client connections
		this.setInputPipe(pipeSvc.createInputPipe(pipeadv));

		discSvc.publish(pipeadv);
		discSvc.remotePublish(pipeadv);

		// set the InputPipeAdverisement
		this.setInputPipeAdvertisement(pipeadv);

		return pipeadv;
	}

	/**
	 * Advertise a secure pipe for this service and return it.
	 */
	private PipeAdvertisement createSecurePipe() throws Exception {
		// Create a pipe advertisement for the Service. The client MUST use
		// the same pipe advertisement to talk to the server.
		PipeAdvertisement pipeadv = (PipeAdvertisement) AdvertisementFactory
				.newAdvertisement(PipeAdvertisement.getAdvertisementType());

		PeerGroupID pgid = pg.getPeerGroupID();
		pipeadv.setPipeID(IDFactory.newPipeID(pgid));
		pipeadv.setName(this.getServiceDescriptor().getName());
		pipeadv.setType("JxtaUnicastSecure");

		// create the input pipe endpoint clients will
		// use to connect to the service
		this.secureInputPipeList.add((InputPipe) pipeSvc
				.createInputPipe(pipeadv));
		this.secureInputPipeAdvList.add((PipeAdvertisement) pipeadv);

		return pipeadv;
	}

	/**
	 * Advertise this module class
	 */
	private ModuleClassID advertiseModuleClass() throws Exception {

		// First create the Module class advertisement associated with the
		// service We build the module class advertisement using the
		// advertisement Factory class by passing it the type of the
		// advertisement we want to construct. The Module class
		// advertisement is to be used to simply advertise the existence of
		// the service. This is a a very small advertisement that only
		// advertise the existence of service. In order to access the
		// service, a peer will have to discover the associated module spec
		// advertisement.
		ModuleClassAdvertisement mcadv = (ModuleClassAdvertisement) AdvertisementFactory
				.newAdvertisement(ModuleClassAdvertisement
						.getAdvertisementType());

		mcadv.setName(this.getServiceDescriptor().getName());
		mcadv.setDescription(this.getServiceDescriptor().getDescription());

		ModuleClassID mcID = IDFactory.newModuleClassID();
		mcadv.setModuleClassID(mcID);

		// Ok the Module Class advertisement was created, just publish it in my
		// local cache and to my peergroup. This is the NetPeerGroup
		discSvc.publish(mcadv);
		discSvc.remotePublish(mcadv);

		return mcID;
	}

	/**
	 * Set the value of <code>serviceDescriptor</code>.
	 */
	public void setServiceDescriptor(ServiceDescriptor serviceDescriptor) {
		this.serviceDescriptor = serviceDescriptor;
	}

	/**
	 * Get the value of <code>serviceDescriptor</code>.
	 */
	public ServiceDescriptor getServiceDescriptor() {
		return this.serviceDescriptor;
	}

	/**
	 * Set the value of <code>inputPipeAdvertisement</code>.
	 */
	public void setInputPipeAdvertisement(
			PipeAdvertisement inputPipeAdvertisement) {
		this.inputPipeAdvertisement = inputPipeAdvertisement;
	}

	/**
	 * Get the value of <code>inputPipeAdvertisement</code>.
	 */
	public PipeAdvertisement getInputPipeAdvertisement() {
		return this.inputPipeAdvertisement;
	}

	/**
	 * Get the value of <code>inputPipe</code>.
	 */
	public InputPipe getInputPipe() {
		return this.inputPipe;
	}

	/**
	 * Set the value of <code>inputPipe</code>.
	 */
	public void setInputPipe(InputPipe inputPipe) {
		this.inputPipe = inputPipe;
	}

	/**
	 * Set the value of <code>context</code>.
	 */
	public void setContext(Object context) {
		this.context = context;
	}

}
