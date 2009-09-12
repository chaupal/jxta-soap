package it.unipr.ce.dsg.soap.basic.server.cdc.services;

import it.polimi.si.mas.services.MService;
import net.jxta.soap.cdc.ServiceDescriptor;

public class Reverse implements MService {

	public String describe() {
		return "Reverse a String";
	}

	public void destroy() {
	}

	public void init() {
	}

	public Object process(String methodName, Object[] args) {
		if(methodName.equals("rev")){
			System.out.println("Il metodo e' invocato correttaemnte"); 
			return this.reverse((String) args[0]); }
		System.out.println("Il metodo invocato non e' corretto !!!");
		return "Method not found";
	}
	
	
	public static final ServiceDescriptor DESCRIPTOR =
        new ServiceDescriptor("Reverse",     // class
        "Reverse",                       // name
        "0.1",                                // version
        "Distributed Systems Group",          // creator
        "jxta:/a.very.unique.spec/uri",       // specURI
        "Reverse a given string",   // description
        "urn:jxta:jxta-NetGroup",  // peergroup ID
        "JXTA NetPeerGroup",                      // PeerGroup name
        "JXTA NetPeerGroup",                    // PeerGroup description
        false,            // secure policy flag (use default=false)
        null);            // security policy type (use no policy)
    
	
	private String reverse(String original) {
		StringBuffer rev = new StringBuffer();
		int len = original.length();
		for(int i=0; i<len; i++)
			rev.append(original.charAt(len -i -1));
		return rev.toString();
	}

}
