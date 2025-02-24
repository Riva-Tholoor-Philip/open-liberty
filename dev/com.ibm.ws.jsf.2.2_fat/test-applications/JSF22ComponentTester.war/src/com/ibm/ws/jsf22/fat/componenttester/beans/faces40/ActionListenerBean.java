/*******************************************************************************
 * Copyright (c) 2015, 2023 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package com.ibm.ws.jsf22.fat.componenttester.beans.faces40;

import java.io.Serializable;

import javax.enterprise.context.SessionScoped;
import javax.faces.event.AjaxBehaviorEvent;
import javax.inject.Named;

/**
 * This managed bean is used to verify the fix provided by MYFACES-3960:
 * https://issues.apache.org/jira/browse/MYFACES-3960
 *
 * It is used to insure the proper ordering of an action vs. an ajax listener.
 * The proper order for this type of markup is ajax listener first then action:
 *
 * <h:commandLink value="GetResult" action="#{actionListenerBean.test('test action called')}">
 * <f:ajax listener="#{actionListenerBean.ajaxListener}"/>
 * </h:commandLink>
 *
 * This bean is used to test both the commandLink and commandButton.
 */

@Named
@SessionScoped
public class ActionListenerBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private String result = "Not called";

    public String test() {
        System.out.println("ActionListenerBean: test action called");
        this.result = "test action called";
        return (null);
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getResult() {
        System.out.println("ActionListenerBean: getResult called");
        return (result);
    }

    public void ajaxListener(final AjaxBehaviorEvent event) {
        System.out.println("ActionListenerBean: ajaxListener called");
        result = "ajaxListener called";
    }
}
