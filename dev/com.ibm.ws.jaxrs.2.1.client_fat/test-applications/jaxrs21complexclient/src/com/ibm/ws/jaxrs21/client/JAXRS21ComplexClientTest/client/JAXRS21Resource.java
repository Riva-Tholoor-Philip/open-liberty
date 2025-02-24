/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.jaxrs21.client.JAXRS21ComplexClientTest.client;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;

@Path("resource")
public class JAXRS21Resource {

    public static final long SLEEP_TIME = 1500L;

    @GET
    @Path("get")
    public String get() {
        return "get";
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("consumesappjson")
    public String consumesAppJson() {
        return MediaType.APPLICATION_JSON;
    }

}