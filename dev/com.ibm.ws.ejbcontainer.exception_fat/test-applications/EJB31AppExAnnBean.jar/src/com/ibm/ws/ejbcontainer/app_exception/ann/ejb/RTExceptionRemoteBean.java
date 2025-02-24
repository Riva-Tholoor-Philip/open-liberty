/*******************************************************************************
 * Copyright (c) 2009, 2019 IBM Corporation and others.
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

package com.ibm.ws.ejbcontainer.app_exception.ann.ejb;

import static javax.ejb.TransactionAttributeType.REQUIRES_NEW;

import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.EJBContext;
import javax.ejb.Remote;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;

@Stateless
@Remote(RTExRemoteInterface.class)
public class RTExceptionRemoteBean extends RTExceptionBean {

    private static final String CLASSNAME = RTExceptionRemoteBean.class.getName();
    private static final Logger svLogger = Logger.getLogger(CLASSNAME);

    @Resource
    private EJBContext ivContext;

    @EJB
    private RTExRemoteInterface ivBean;

    @Override
    @TransactionAttribute(REQUIRES_NEW)
    public ResultObject test(int i) {
        try {
            ivBean.throwException(i);
        } catch (Throwable t) {
            ResultObject r = new ResultObject(ivContext.getRollbackOnly(), t);
            svLogger.info("--> BEAN INFO: " + r);
            return r;
        }
        throw new Error("Did not throw an exception.");

    }
}
