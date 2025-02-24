/*******************************************************************************
 * Copyright (c) 2012, 2020 IBM Corporation and others.
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
package com.ibm.ws.managedbeans.fat.mb.web;

import java.util.List;

/**
 * Provider access to bean state for interceptors.
 **/
public interface InterceptorAccess {
    /**
     * Returns the current PostConstruct call stack, so an interceptor may
     * add itself to the stack.
     **/
    public List<String> getPostConstructStack();

    /**
     * Returns the current PreDestroy call stack, so an interceptor may
     * add itself to the stack.
     **/
    public List<String> getPreDestroyStack();
}
