/*******************************************************************************
 * Copyright (c) 2022 IBM Corporation and others.
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
package com.ibm.ws.javaee.ddmodel.suite.simple.war;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.ws.javaee.ddmodel.suite.simple.CommonTests_Simple;

import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;

// Module extension supplied context root ("web").
// Overridden by module configuration extension supplied context root ("ext").

@RunWith(FATRunner.class)
@Mode(TestMode.FULL)
public class DDSimpleWarTests_Web_ConfigExt_Exp extends CommonTests_Simple {
    public static final Class<?> TEST_CLASS = DDSimpleWarTests_Web_ConfigExt_Exp.class;

    @BeforeClass
    public static void setUp() throws Exception {
        commonSetUp(TEST_CLASS, "server_simple_war_ext_exp.xml", setUpSimpleWarExt);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        commonTearDown(TEST_CLASS, tearDownTestModules, NO_ALLOWED_ERRORS);
    }

    @Test
    public void testContextRoot_War_Web_ConfigExt_Exp() throws Exception {
        testHello(TEST_CLASS, EXP_CONTEXT_ROOT);
    }
}
