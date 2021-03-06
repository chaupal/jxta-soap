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


import net.jxta.soap.cdc.transport.KSoapPipeTransport;

import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import net.jxta.discovery.DiscoveryService;
import net.jxta.document.Advertisement;
import net.jxta.document.StructuredDocument;
import net.jxta.impl.document.LiteXMLDocument;
import net.jxta.impl.document.LiteXMLElement;
import net.jxta.peer.PeerID;
import net.jxta.peergroup.PeerGroup;
import net.jxta.pipe.OutputPipe;
import net.jxta.protocol.ModuleSpecAdvertisement;
import net.jxta.protocol.PipeAdvertisement;

import org.ksoap2.serialization.*;
import org.ksoap2.SoapEnvelope;



public class CallFactory {
    
    /**
     * Instance member for <code>CallFactory</code>
     */
    private static CallFactory instance = null;
    
    private OutputPipe outputpipe = null;

    private Hashtable services = new Hashtable();

    /**
     * Create a Call using discovery in the given peergroup to find the
     * PipeAdvertisement of this given ServiceDescriptor.  Given the PeerID, we
     * find the InputPipe for the given service and use that.
     */
    public SoapObject createCall( ServiceDescriptor descriptor,
                            PeerID peerid, 
                            PeerGroup peergroup ) throws Exception {

        //get all modulespecs from the peer we want to communicate with.
        ModuleSpecAdvertisement[] advs = getModuleSpecAdvertisements( descriptor, peerid, peergroup );

        //ok...go though the advertisements and try to bind an output pipe
        OutputPipe outputpipe = null;

        PipeAdvertisement pipeadv = null; 
        for ( int i = 0; i < advs.length; ++i ) {
			try { 
                outputpipe = peergroup.getPipeService()
                    .createOutputPipe( advs[i].getPipeAdvertisement(), descriptor.getTimeout() );
                pipeadv = advs[i].getPipeAdvertisement();                
                break;                
            } catch ( Throwable t ) {
                //acceptable... probably a stale advertisement.          
            }
        } 

        if ( outputpipe == null ) { 
       // 	System.out.println("Unable to find a valid output pipe for service");
            throw new Exception( "Unable to find a valid output pipe for service" );      
        } 

        //create a call for this and set the output pipe it should use
        //FIXME: AH... infinite loop!
        SoapObject call = createCall( descriptor, pipeadv, peergroup );
        call.addProperty( "outputpipe", outputpipe );
        System.out.println("ho settato la outputpipe");

        return call;        
    }

	/**
     * For the given PeerID, find all given services it is running.
     */
    private ModuleSpecAdvertisement[] getModuleSpecAdvertisements( ServiceDescriptor descriptor,
                                                                   PeerID peerid, 
                                                                   PeerGroup peergroup ) throws Exception {

        //FIXME: try to get remote advertisements if they aren't found locally        
        Vector v = new Vector();        
        DiscoveryService ds = peergroup.getDiscoveryService();

        //get all advertisements for this service...
        Enumeration enumeration = ds.getLocalAdvertisements( DiscoveryService.ADV, "Name", descriptor.getName() );        
        
		//go over the advertisements and find the InputPipe adv
        while ( enumeration.hasMoreElements() ) {
 
            Advertisement current = (Advertisement) enumeration.nextElement();
            //ok... if this is a ModuleSpecAdvertisement it might be what I want.
            if ( current instanceof ModuleSpecAdvertisement ) {

                //ok... see if it has the PeerID I need.
                ModuleSpecAdvertisement msa = (ModuleSpecAdvertisement)current;

                //get the peer-id...
                //WARNING: scary casting ahead!
                StructuredDocument sd = msa.getParam();
                if ( sd == null )
                    continue;

                Enumeration penum = ((LiteXMLDocument)sd).getChildren( "peer-id" );
                if ( penum.hasMoreElements() ) {
                    String speerid = (( LiteXMLElement )penum.nextElement() ).getTextValue();
                    if ( peerid.toString().equals( speerid ) ) {
                        v.addElement( msa );                       
                    } 
                } 
                
            } 
            
        }

        ModuleSpecAdvertisement[] result = new ModuleSpecAdvertisement[ v.size() ];
        v.copyInto( result );

        //sort this array based on freshness.
		//Arrays.sort( result, new MSAdvertisementComparator() );
        
        return result;
    }
    

    /**
     * Create a Call object with a pre-existing advertisement.  You will have to
     * do your own discovery to find this one.
     */
    public SoapObject createCall( ServiceDescriptor descriptor,
                            PipeAdvertisement advert,
                            PeerGroup peergroup ) throws Exception {
   	
    	
		SoapObject call = new SoapObject("http://DefaultNamespace", descriptor.getName()); 
			
		call = this.processCall( call, descriptor, advert, peergroup );  
		return call;   

    }

    
    /**
     * Create a Call object with a pre-existing advertisement.  You will have to
     * do your own discovery to find this one.
     */
 /*   public SoapObject createCall( ServiceDescriptor descriptor,
							PipeAdvertisement advert,
							PeerGroup peergroup,
							String wsdlLocation,
							QName servicename,
							QName portname ) throws Exception {
		
		if( wsdlLocation == null ) {
			System.out.println("CallFactory.createCall() : wsdlLocation parameter == null!");
		}
	//	Service service = this.getService( descriptor, wsdlLocation, servicename );
		
	//	Call call = (Call)service.createCall( portname );
		SoapObject call = new SoapObject("", "");
		call = this.processCall( call, descriptor, advert, peergroup );
		return call;
    }  */
    

    /**
     * Create a Call object with a pre-existing advertisement.  You will have to
     * do your own discovery to find this one.
     */
 /*   public SoapObject createCall( ServiceDescriptor descriptor,
							PipeAdvertisement advert,
							PeerGroup peergroup,
							String wsdlLocation,
							QName servicename,
							QName portname,
							QName operationName ) throws Exception {

    	SoapObject call = new SoapObject("", "");
		call = this.processCall( call, descriptor, advert, peergroup );
		return call;
    }  */


    /**
     * Helper method to set the JXTA-SOAP properties of the Call object
     */     
    private SoapObject processCall( SoapObject call,
							ServiceDescriptor descriptor,
							PipeAdvertisement advert,
							PeerGroup peergroup ) {
		try {
			
			//we need to pass peergroup, advertisement, etc as a property because
			//these are needed by the transport.
			call.addProperty( "peergroup", peergroup );
			call.addProperty( "advertisement", advert );
			call.addProperty( "descriptor", descriptor );
			System.out.println("Ho settato tutte le property");
			return call;
			
		} catch (Exception e){
			System.out.println("Error in CallFactory.processCall()");
			e.printStackTrace();
			System.exit(1);
		}
		return null;
    }
    
    
 public SoapSerializationEnvelope getEnvelope(SoapObject call){
	 SoapSerializationEnvelope envelope= new SoapSerializationEnvelope(SoapEnvelope.VER11);
	 envelope.setOutputSoapObject(call);
	 return envelope;
 }   
	
 
 
 
 public  KSoapPipeTransport  getTransport(SoapObject call){
	 
	 KSoapPipeTransport transportME = new KSoapPipeTransport();
	 transportME.getJXTAproperties(call);
	 return transportME;	 
 }
 
 
 
    /**
     * Get an instance of the <code>CallFactory</code>.
     */
    public static CallFactory getInstance() {
        
        if ( instance == null ) {
            
            instance = new CallFactory();
            
        }
        
        return instance;
        
    }

}


class MSAdvertisementComparator implements Comparator {
    public int compare( Object one, Object two ) throws ClassCastException {
        ModuleSpecAdvertisement msa1 = (ModuleSpecAdvertisement)one;
        ModuleSpecAdvertisement msa2 = (ModuleSpecAdvertisement)two;
	
        //if msa1 is fresher...
	
	//         if ( msa1.getLocalExpirationTime() > msa2.getLocalExpirationTime() ) {
	//             return -1;
	//         } else if ( msa1.getLocalExpirationTime() == msa2.getLocalExpirationTime() ) {
	//             return 0;
	//         } else {
	//             return 1;
	//         }
	
	// ModuleSpecAdvertisements don't support the getLocalExpirationTime()
	// method anymore. Return 0 for equal time.
	return 0;
    }
    
    public boolean equals( Object obj ) {
        return obj.equals( this );
    }
    
}
