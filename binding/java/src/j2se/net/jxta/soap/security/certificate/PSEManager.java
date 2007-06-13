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


package net.jxta.soap.security.certificate;

import java.security.cert.X509Certificate;

import net.jxta.impl.membership.pse.PSEConfig;
import net.jxta.peergroup.PeerGroup;
import net.jxta.protocol.PeerAdvertisement;

public interface PSEManager {

    public PSEConfig getPSEConfig();
    
    public int importCert( PeerGroup pg, PeerAdvertisement pa, boolean trimChain );
    
    public X509Certificate findCert( String issuerName, String subjectName );
    
    public void replaceCert( X509Certificate currentCert, X509Certificate newCert );
    
    public void printPSECert( X509Certificate cert, boolean showCerts, boolean showChains );
    
    public void printAllPSECerts( boolean showCerts, boolean showChains );
}
