package com.vaadin.tests.tb3;

import java.util.logging.Logger;

import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.Platform;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.ie.InternetExplorerOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.safari.SafariOptions;

import com.vaadin.testbench.parallel.Browser;
import com.vaadin.testbench.parallel.DefaultBrowserFactory;

/**
 * Browser factory for the cloud test provider BrowserStack.
 */
public class BrowserStackBrowserFactory extends DefaultBrowserFactory {

    @Override
    public DesiredCapabilities create(Browser browser, String version,
            Platform platform) {
        MutableCapabilities caps;

        switch (browser) {
        /* Ignored browsers */
        case CHROME:
            caps = new ChromeOptions();
            break;
        case PHANTOMJS:
            caps = DesiredCapabilities.phantomjs();
            break;
        case SAFARI:
            caps = new SafariOptions();
            break;
        case FIREFOX:
            caps = new FirefoxOptions();
            break;
        /* Actual browsers */
        case IE11:
            caps = new InternetExplorerOptions();
            caps.setCapability(CapabilityType.VERSION, "11");
            caps.setCapability("browser", "IE");
            caps.setCapability("browser_version", "11.0");
            // There are 2 capabilities ie.ensureCleanSession and
            // ensureCleanSession in Selenium
            // IE 11 uses ie.ensureCleanSession
            caps.setCapability("ie.ensureCleanSession", true);
            caps.setCapability(InternetExplorerDriver.IE_ENSURE_CLEAN_SESSION,
                    true);
            break;
        default:
            caps = new FirefoxOptions();
        }

        // BrowserStack specific parts

        // for now, run all tests on Windows 7
        caps.setCapability("os", "Windows");
        caps.setCapability("os_version", "7");
        caps.setCapability(CapabilityType.PLATFORM, Platform.WINDOWS);

        // enable logging on BrowserStack
        caps.setCapability("browserstack.debug", "true");

        // tunnel
        caps.setCapability("browserstack.local", "true");
        String localIdentifier = System.getProperty("browserstack.identifier",
                "");
        if (!localIdentifier.isEmpty()) {
            caps.setCapability("browserstack.localIdentifier", localIdentifier);
        }

        // build name for easy identification in BrowserStack UI
        caps.setCapability("build",
                "BrowserStack Tests" + (localIdentifier.isEmpty() ? ""
                        : " [" + localIdentifier + "]"));

        // accept self-signed certificates
        caps.setCapability("acceptSslCerts", "true");

        caps.setCapability("resolution", "1680x1050");

        getLogger().info("Using BrowserStack capabilities " + caps);

        return new DesiredCapabilities(caps);
    }

    private static final Logger getLogger() {
        return Logger.getLogger(BrowserStackBrowserFactory.class.getName());
    }
}
