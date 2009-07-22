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


package net.jxta.soap.cdc.security.policy;

import net.jxta.document.StructuredDocument;

public interface Policy {
    
    public String getName();	
    
    public String getType();	
    
    public Object[] extractParams( Object dataSource ) throws IllegalArgumentException;
    
    public boolean checkParams( Object[] params );
    
    public Object getAuthenticationParam();
    
    public StructuredDocument getDocument();
	
}
