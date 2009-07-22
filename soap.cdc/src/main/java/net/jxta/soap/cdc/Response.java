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

public class Response {

    private String message = null;

    /**
     * Set the value of <code>message</code>.
     */
    public void setMessage( String message ) {       
        this.message = message;
    }

    /**
     * Get the value of <code>message</code>.
     */
    public String getMessage() { 
        return this.message;
    }

}
