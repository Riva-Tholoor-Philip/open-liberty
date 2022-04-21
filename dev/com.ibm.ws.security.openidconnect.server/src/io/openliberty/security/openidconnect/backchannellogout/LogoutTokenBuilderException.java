/*******************************************************************************
 * Copyright (c) 2022 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package io.openliberty.security.openidconnect.backchannellogout;

public class LogoutTokenBuilderException extends Exception {

    private static final long serialVersionUID = 1L;

    public LogoutTokenBuilderException(Exception e) {
        super(e);
    }

    public LogoutTokenBuilderException(String errorMsg) {
        super(errorMsg);
    }

    public LogoutTokenBuilderException(String errorMsg, Exception e) {
        super(errorMsg, e);
    }

}