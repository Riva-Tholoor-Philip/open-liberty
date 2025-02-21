/*******************************************************************************
 * Copyright (c) 2018, 2023 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package com.ibm.ws.jsf23.fat.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.net.URL;
import java.util.logging.Logger;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.ibm.websphere.simplicity.ShrinkHelper;
import com.ibm.ws.jsf23.fat.CDITestBase;
import com.ibm.ws.jsf23.fat.JSFUtils;

import componenttest.annotation.Server;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.rules.repeater.JakartaEE10Action;
import componenttest.topology.impl.LibertyServer;
import junit.framework.Assert;

/**
 * This is one of four CDI test applications, with configuration loaded in the following manner:
 * CDIInjectionTests - WEB-INF/faces-config.xml
 *
 * We're extending CDITestBase, which has common test code.
 */
@RunWith(FATRunner.class)
public class JSF23CDIInjectionTests extends CDITestBase {
    protected static final Class<?> c = JSF23CDIInjectionTests.class;
    private static final Logger LOG = Logger.getLogger(c.getName());
    private static final String CDI_INJECTION_TESTS_APP_NAME = "CDIInjectionTests.war";
    private static final String ACTION_LISTENER_INJECTION_APP_NAME = "ActionListenerInjection.war";

    @Server("jsf23CDIInjectionServer")
    public static LibertyServer server;

    @BeforeClass
    public static void setup() throws Exception {
        boolean isEE10 = JakartaEE10Action.isActive();

        // Include @Named beans if Faces 4.0 is being tested. Include @ManagedBean beans otherwise.
        // Include correct resources directory, since the faces-config.xml points to different
        // CustomNavigationHandler and CustomActionListener classes.
        if (isEE10) {
            // Create the CDIInjectionTests.war application.
            WebArchive appCDIInjection = ShrinkWrap.create(WebArchive.class, CDI_INJECTION_TESTS_APP_NAME);
            appCDIInjection.addPackages(false, "com.ibm.ws.jsf23.fat.cdi.injection.beans.injected",
                                        "com.ibm.ws.jsf23.fat.cdi.injection.beans.viewscope",
                                        "com.ibm.ws.jsf23.fat.cdi.common.beans.faces40",
                                        "com.ibm.ws.jsf23.fat.cdi.common.beans.factory",
                                        "com.ibm.ws.jsf23.fat.cdi.common.beans.injected",
                                        "com.ibm.ws.jsf23.fat.cdi.common.managed",
                                        "com.ibm.ws.jsf23.fat.cdi.common.managed.faces40",
                                        "com.ibm.ws.jsf23.fat.cdi.common.managed.factories",
                                        "com.ibm.ws.jsf23.fat.cdi.common.managed.factories.client.window");
            ShrinkHelper.addDirectory(appCDIInjection, "test-applications/" + CDI_INJECTION_TESTS_APP_NAME + "/resources");
            ShrinkHelper.addDirectory(appCDIInjection, "test-applications/" + CDI_INJECTION_TESTS_APP_NAME + "/resourcesFaces40");
            ShrinkHelper.exportDropinAppToServer(server, appCDIInjection);

            // Create the ActionListenerInjection.war application.
            WebArchive appActionListenerInjection = ShrinkWrap.create(WebArchive.class, ACTION_LISTENER_INJECTION_APP_NAME);
            appActionListenerInjection.addPackages(false, "com.ibm.ws.jsf23.fat.cdi.common.beans.faces40",
                                                   "com.ibm.ws.jsf23.fat.cdi.common.beans.factory",
                                                   "com.ibm.ws.jsf23.fat.cdi.common.beans.injected",
                                                   "com.ibm.ws.jsf23.fat.cdi.common.managed",
                                                   "com.ibm.ws.jsf23.fat.cdi.common.managed.faces40",
                                                   "com.ibm.ws.jsf23.fat.cdi.common.managed.factories");;
            ShrinkHelper.addDirectory(appActionListenerInjection, "test-applications/" + ACTION_LISTENER_INJECTION_APP_NAME + "/resources");
            ShrinkHelper.addDirectory(appActionListenerInjection, "test-applications/" + ACTION_LISTENER_INJECTION_APP_NAME + "/resourcesFaces40");
            ShrinkHelper.exportDropinAppToServer(server, appActionListenerInjection);
        } else {
            // Create the CDIInjectionTests.war application.
            WebArchive app = ShrinkWrap.create(WebArchive.class, CDI_INJECTION_TESTS_APP_NAME);
            app.addPackages(false, "com.ibm.ws.jsf23.fat.cdi.injection.beans.injected",
                            "com.ibm.ws.jsf23.fat.cdi.injection.beans.viewscope",
                            "com.ibm.ws.jsf23.fat.cdi.common.beans.jsf23",
                            "com.ibm.ws.jsf23.fat.cdi.common.beans.factory",
                            "com.ibm.ws.jsf23.fat.cdi.common.beans.injected",
                            "com.ibm.ws.jsf23.fat.cdi.common.managed",
                            "com.ibm.ws.jsf23.fat.cdi.common.managed.jsf23",
                            "com.ibm.ws.jsf23.fat.cdi.common.managed.factories",
                            "com.ibm.ws.jsf23.fat.cdi.common.managed.factories.client.window");
            ShrinkHelper.addDirectory(app, "test-applications/" + CDI_INJECTION_TESTS_APP_NAME + "/resources");
            ShrinkHelper.addDirectory(app, "test-applications/" + CDI_INJECTION_TESTS_APP_NAME + "/resourcesJSF23");
            ShrinkHelper.exportDropinAppToServer(server, app);

            // Create the ActionListenerInjection.war application.
            WebArchive appActionListenerInjection = ShrinkWrap.create(WebArchive.class, ACTION_LISTENER_INJECTION_APP_NAME);
            appActionListenerInjection.addPackages(false, "com.ibm.ws.jsf23.fat.cdi.common.beans.jsf23",
                                                   "com.ibm.ws.jsf23.fat.cdi.common.beans.factory",
                                                   "com.ibm.ws.jsf23.fat.cdi.common.beans.injected",
                                                   "com.ibm.ws.jsf23.fat.cdi.common.managed",
                                                   "com.ibm.ws.jsf23.fat.cdi.common.managed.jsf23",
                                                   "com.ibm.ws.jsf23.fat.cdi.common.managed.factories");;
            ShrinkHelper.addDirectory(appActionListenerInjection, "test-applications/" + ACTION_LISTENER_INJECTION_APP_NAME + "/resources");
            ShrinkHelper.addDirectory(appActionListenerInjection, "test-applications/" + ACTION_LISTENER_INJECTION_APP_NAME + "/resourcesJSF23");
            ShrinkHelper.exportDropinAppToServer(server, appActionListenerInjection);
        }

        // Start the server and use the class name so we can find logs easily.
        // Many tests use the same server
        server.startServer(c.getSimpleName() + ".log");

    }

    @AfterClass
    public static void tearDown() throws Exception {
        // Stop the server
        if (server != null && server.isStarted()) {
            server.stopServer();
        }
    }

    /**
     * Test to ensure that CDI 2.0 injection works for a custom action listener
     * Field and Method injection, but no Constructor injection.
     * Also tested are use of request and session scope and use of qualifiers.
     *
     * @throws Exception. Content of the response should show if a specific injection failed.
     *
     */
    @Test
    public void testActionListenerInjection_CDIInjectionTests() throws Exception {
        testActionListenerInjectionByApp("ActionListenerInjection", server);
    }

    /**
     * Test to ensure that CDI 2.0 injection works for a custom Navigation Handler
     * Field and Method injection, but no Constructor injection.
     * Also tested are use of request and session scope and use of qualifiers.
     *
     * @throws Exception. Content of the response should show if a specific injection failed.
     *
     */
    @Test
    public void testNavigationHandlerInjection_CDIInjectionTests() throws Exception {
        testNavigationHandlerInjectionByApp("CDIInjectionTests", server);
    }

    /**
     * Test to ensure that CDI 2.0 injection works for a custom EL Resolver
     * Field and Method injection, but no Constructor injection.
     * Also tested are use of request scope and use of qualifiers.
     *
     * @throws Exception. Content of the response should show if a specific injection failed.
     *
     */
    @Test
    public void testELResolverInjection_CDIInjectionTests() throws Exception {
        testELResolverInjectionByApp("CDIInjectionTests", server);
    }

    /**
     * Test method and field injection for Custom resource handler. No intercepter or constructor injection on this.
     *
     * Would like to do something more than look for message in logs, a future improvement.
     *
     * @throws Exception
     */
    @Test
    public void testCustomResourceHandlerInjections_CDIInjectionTests() throws Exception {
        testCustomResourceHandlerInjectionsByApp("CDIInjectionTests", server);

    }

    /**
     * Test method and field injection on custom state manager. No intercepter or constructor tests on this.
     *
     * Would like to do something more than look for message in logs, a future improvement.
     *
     * @throws Exception
     */
    @Test
    public void testCustomStateManagerInjections_CDIInjectionTests() throws Exception {
        testCustomStateManagerInjectionsByApp("CDIInjectionTests", server);
    }

    /**
     * Test that hits most of the managed factory classes, and system-event listener, and phase-listener. See faces-config.xml for details.
     * Most factories use delegate constructor method, so they are limited to tested basic field and method injection.
     *
     * Test Field and Method injection in system-event and phase listeners. No Constructor injection.
     *
     * Tests also use app scope as
     * request/session are not available to these managed classes that I can tell.
     *
     * @throws Exception
     */
    @Test
    public void testFactoryAndOtherScopeInjections_CDIInjectionTests() throws Exception {
        testFactoryAndOtherAppScopedInjectionsByApp("CDIInjectionTests", server);
    }

    /**
     * Test to ensure that the org.apache.myfaces.spi.InjectionProvider specified in the META-INF/services
     * directory is being loaded and the specified InjectionProvider used.
     *
     * Also ensure that when running on a Server with the cdi-2.0 feature enabled that JSF knows that MyFaces CDI Support
     * is enabled.
     *
     * @throws Exception
     */
    @Test
    public void testInjectionProvider() throws Exception {
        String msgToSearchFor1 = JakartaEE10Action
                        .isActive() ? "Using InjectionProvider io.openliberty.faces40.internal.spi.impl.WASCDIAnnotationDelegateInjectionProvider" : "Using InjectionProvider com.ibm.ws.jsf.spi.impl.WASCDIAnnotationDelegateInjectionProvider";

        // The Message that is output by MyFaces was changed in https://issues.apache.org/jira/browse/MYFACES-4334
        // for MyFaces 2.3.7 and newer versions. The original message was "MyFaces CDI support enabled".
        String msgToSearchFor2 = "MyFaces Core CDI support enabled";

        this.verifyResponse("CDIInjectionTests", "index.xhtml", "Hello Worldy world", server);

        // Check the trace.log to see if the proper InjectionProvider is being used.
        String isInjectionProviderBeingLoaded = server.waitForStringInTrace(msgToSearchFor1, 30 * 1000);

        // Reset the log search offset position to avoid conflict with other searches
        server.resetLogOffsets();

        String isCDISupportEnabled = server.waitForStringInLog(msgToSearchFor2, 30 * 1000);

        // There should be a match so fail if there is not.
        assertNotNull("The following message was not found in the trace logs: " + msgToSearchFor1,
                      isInjectionProviderBeingLoaded);

        assertNotNull("The following message was not found in the logs: " + msgToSearchFor2,
                      isCDISupportEnabled);
    }

    /**
     * Test to ensure that CDI 2.0 injection works for a basic managed bean.
     * Field and Method injection, but no Constructor injection.
     * Also tested are use of request scope and use of qualifiers.
     *
     * @throws Exception. Content of the response should show if a specific injection failed.
     *
     */
    @Test
    public void testBeanInjection() throws Exception {
        this.verifyResponse("CDIInjectionTests", "TestBean.jsf", server,
                            ":TestBean:", "class com.ibm.ws.jsf23.fat.cdi.common.beans.injected.TestBeanFieldBean",
                            "com.ibm.ws.jsf23.fat.cdi.common.beans.injected.MethodBean",
                            ":PostConstructCalled:");
    }

    /**
     *
     * Test CDI injections on CDI ViewScope. Tests field, and method injections (no constructor injection) with app, session, request, and dependent scopes.
     * Does some simple verifications of the 4 scopes and instances ( through hashcode) are what is expected for multiple requests.
     */
    @Test
    public void testViewScopeInjections() throws Exception {
        String contextRoot = "CDIInjectionTests";

        try (WebClient webClient = new WebClient(); WebClient webClient2 = new WebClient()) {

            URL url = JSFUtils.createHttpUrl(server, contextRoot, "ViewScope.jsf");
            HtmlPage page = (HtmlPage) webClient.getPage(url);

            // Make sure the page initially renders correctly
            if (page == null) {
                Assert.fail("ViewScope.xhtml did not render properly.");
            }

            LOG.info("First request output is:" + page.asText());

            int app = getAreaHashCode(page, "vab");
            int sess = getAreaHashCode(page, "vsb");
            int req = getAreaHashCode(page, "vrb");
            int dep = getAreaHashCode(page, "vdb");

            HtmlElement button = (HtmlElement) page.getElementById("button:test");
            page = button.click();

            if (page == null) {
                Assert.fail("ViewScope.xhtml did not render properly after button press.");
            }

            LOG.info("After button click content is:" + page.asText());

            int app2 = getAreaHashCode(page, "vab");
            int sess2 = getAreaHashCode(page, "vsb");
            int req2 = getAreaHashCode(page, "vrb");
            int dep2 = getAreaHashCode(page, "vdb");

            Assert.assertEquals("App Scoped beans were not identical for consecutive requests.", app, app2);
            Assert.assertEquals("Session Scoped beans were not identical for consecutive requests.", sess, sess2);
            Assert.assertTrue("Request bean is equivalent when it should not be.", (req != req2));
            Assert.assertTrue("Dependent bean is equivalent when it should not be.", (dep != dep2));

            webClient2.getCookieManager().clearCookies();

            // Construct the URL for the test
            url = JSFUtils.createHttpUrl(server, contextRoot, "ViewScope.jsf");
            HtmlPage page2 = (HtmlPage) webClient2.getPage(url);

            // Make sure the page initially renders correctly
            if (page2 == null) {
                Assert.fail("ViewScope.xhtml did not render properly for second client.");
            }

            LOG.info("Second client page request content:" + page2.asText());

            int app3 = getAreaHashCode(page2, "vab");
            int sess3 = getAreaHashCode(page2, "vsb");
            int req3 = getAreaHashCode(page2, "vrb");
            int dep3 = getAreaHashCode(page2, "vdb");

            Assert.assertEquals("App Scoped beans were not identical for two different clients.", app, app3);
            Assert.assertTrue("Session Scoped bean is equivalent when it should not be.", (sess != sess3));
            Assert.assertTrue("Request bean is equivalent when it should not be.", (req2 != req3));
            Assert.assertTrue("Dependent bean is equivalent when it should not be.", (dep != dep3));
        }

    }

    private int getAreaHashCode(HtmlPage page, String area) {
        int retValue = 0;

        HtmlElement sess = (HtmlElement) page.getElementById(area + "Area");
        if (sess != null) {
            retValue = Integer.valueOf(sess.asText());
        }

        return retValue;
    }

    @Test
    public void testPreDestroyInjection() throws Exception {
        String contextRootCDIInjectionTests = "CDIInjectionTests";
        String contextRootActionListenerInjection = "ActionListenerInjection";

        // Drive requests to ensure all injected objected are created.
        this.verifyStatusCode(contextRootCDIInjectionTests, "index.xhtml", 200, server);
        this.verifyStatusCode(contextRootCDIInjectionTests, "index.xhtml", 200, server);
        this.verifyStatusCode(contextRootCDIInjectionTests, "TestBean.jsf", 200, server);
        this.verifyStatusCode(contextRootActionListenerInjection, "ActionListener.jsf", 200, server);

        // Restart the app so that preDestory gets called;
        // make sure we reset log offsets correctly
        server.setMarkToEndOfLog();
        Assert.assertTrue("The CDIInjectionTests.war application was not restarted.", server.restartDropinsApplication("CDIInjectionTests.war"));
        Assert.assertTrue("The ActionListenerInjection.war application was not restarted.", server.restartDropinsApplication("ActionListenerInjection.war"));

        server.resetLogOffsets();

        // Now check the preDestoys
        assertFalse("CustomActionListener preDestroy not called", server.findStringsInLogs("CustomActionListener preDestroy called.").isEmpty());
        assertFalse("CustomELResolver preDestroy not called", server.findStringsInLogs("CustomELResolver preDestroy called.").isEmpty());
        assertFalse("CustomNavigationHandler preDestroy not called", server.findStringsInLogs("CustomNavigationHandler preDestroy called").isEmpty());
        assertFalse("TestBean preDestroy not called", server.findStringsInLogs("TestBean preDestroy called.").isEmpty());

        assertFalse("CustomStateManager preDestroy not called", server.findStringsInLogs("CustomStateManager preDestroy called").isEmpty());
        assertFalse("CustomResourceHandler preDestroy not called", server.findStringsInLogs("CustomResourceHandler preDestroy called.").isEmpty());

        assertFalse("CustomApplicationFactory preDestroy not called", server.findStringsInLogs("CustomApplicationFactory preDestroy called.").isEmpty());
        assertFalse("CustomLifecycleFactory preDestroy not called", server.findStringsInLogs("CustomLifecycleFactory preDestroy called.").isEmpty());
        assertFalse("CustomExceptionHandlerFactory preDestroy not called",
                    server.findStringsInLogs("CustomExceptionHandlerFactory preDestroy called.").isEmpty());
        assertFalse("CustomExternalContextFactory preDestroy not called",
                    server.findStringsInLogs("CustomExternalContextFactory preDestroy called.").isEmpty());
        assertFalse("CustomFacesContextFactory preDestroy not called",
                    server.findStringsInLogs("CustomFacesContextFactory preDestroy called.").isEmpty());
        assertFalse("CustomRenderKitFactory preDestroy not called", server.findStringsInLogs("CustomRenderKitFactory preDestroy called.").isEmpty());
        assertFalse("CustomViewDeclarationLanguageFactory preDestroy not called",
                    server.findStringsInLogs("CustomViewDeclarationLanguageFactory preDestroy called.").isEmpty());
        assertFalse("CustomTagHandlerDelegateFactory preDestroy not called",
                    server.findStringsInLogs("CustomTagHandlerDelegateFactory preDestroy called.").isEmpty());
        assertFalse("CustomPartialViewContextFactory preDestroy not called",
                    server.findStringsInLogs("CustomPartialViewContextFactory preDestroy called.").isEmpty());
        assertFalse("CustomVisitContextFactory preDestroy not called",
                    server.findStringsInLogs("CustomVisitContextFactory preDestroy called.").isEmpty());
        assertFalse("CustomPhaseListener preDestroy not called", server.findStringsInLogs("CustomPhaseListener preDestroy called.").isEmpty());
        assertFalse("CustomSystemEventListener preDestroy not called",
                    server.findStringsInLogs("CustomSystemEventListener preDestroy called.").isEmpty());
    }

}
