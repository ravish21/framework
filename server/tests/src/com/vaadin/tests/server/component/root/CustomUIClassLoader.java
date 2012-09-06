package com.vaadin.tests.server.component.root;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import com.vaadin.DefaultDeploymentConfiguration;
import com.vaadin.server.DeploymentConfiguration;
import com.vaadin.server.DefaultUIProvider;
import com.vaadin.server.VaadinService;
import com.vaadin.server.VaadinSession;
import com.vaadin.server.VaadinSession.SessionStartEvent;
import com.vaadin.server.WrappedRequest;
import com.vaadin.ui.UI;

public class CustomUIClassLoader extends TestCase {

    /**
     * Stub root
     */
    public static class MyUI extends UI {
        @Override
        protected void init(WrappedRequest request) {
            // Nothing to see here
        }
    }

    /**
     * Dummy ClassLoader that just saves the name of the requested class before
     * delegating to the default implementation.
     */
    public class LoggingClassLoader extends ClassLoader {

        private List<String> requestedClasses = new ArrayList<String>();

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            requestedClasses.add(name);
            return super.loadClass(name, resolve);
        }
    }

    /**
     * Tests that a UI class can be loaded even if no classloader has been
     * provided.
     * 
     * @throws Exception
     *             if thrown
     */
    public void testWithNullClassLoader() throws Exception {
        VaadinSession application = createStubApplication();
        application.start(new SessionStartEvent(null,
                createConfigurationMock(), null));

        DefaultUIProvider uiProvider = new DefaultUIProvider();
        Class<? extends UI> uiClass = uiProvider.getUIClass(application,
                createRequestMock(null));

        assertEquals(MyUI.class, uiClass);
    }

    private static DeploymentConfiguration createConfigurationMock() {
        Properties properties = new Properties();
        properties.put(VaadinSession.UI_PARAMETER, MyUI.class.getName());
        return new DefaultDeploymentConfiguration(CustomUIClassLoader.class,
                properties);
    }

    private static WrappedRequest createRequestMock(ClassLoader classloader) {
        // Mock a VaadinService to give the passed classloader
        VaadinService configurationMock = EasyMock
                .createMock(VaadinService.class);
        EasyMock.expect(configurationMock.getClassLoader()).andReturn(
                classloader);

        // Mock a WrappedRequest to give the mocked vaadin service
        WrappedRequest requestMock = EasyMock.createMock(WrappedRequest.class);
        EasyMock.expect(requestMock.getVaadinService()).andReturn(
                configurationMock);

        EasyMock.replay(configurationMock, requestMock);
        return requestMock;
    }

    /**
     * Tests that the ClassLoader passed in the ApplicationStartEvent is used to
     * load UI classes.
     * 
     * @throws Exception
     *             if thrown
     */
    public void testWithClassLoader() throws Exception {
        LoggingClassLoader loggingClassLoader = new LoggingClassLoader();

        VaadinSession application = createStubApplication();
        application.start(new SessionStartEvent(null,
                createConfigurationMock(), null));

        DefaultUIProvider uiProvider = new DefaultUIProvider();
        Class<? extends UI> uiClass = uiProvider.getUIClass(application,
                createRequestMock(loggingClassLoader));

        assertEquals(MyUI.class, uiClass);
        assertEquals(1, loggingClassLoader.requestedClasses.size());
        assertEquals(MyUI.class.getName(),
                loggingClassLoader.requestedClasses.get(0));

    }

    private VaadinSession createStubApplication() {
        return new VaadinSession() {
            @Override
            public DeploymentConfiguration getConfiguration() {
                return createConfigurationMock();
            }
        };
    }
}
