package com.nm.webmonitor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;

import static org.testng.Assert.assertEquals;

public class WebMonitor {

    private static Logger logger = LogManager.getLogger();

    private WebDriver driver;
    private Properties properties;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String INPUT_EMAIL = "//input[@id='Email']";
    private static final String INPUT_PASS = "//input[@id='Password']";
    private static final String GST_NM_LT = "//div[@class='gst-nm-lt']";
    private static final String URL = "";

    private static final String PROP_HISTORY = "history";
    private static final String PROP_ACCESS = "access";
    private static final String PROP_KEY = "key";
    private static final String PROP_DEST = "dest";

    public static void main(String... args) throws IOException {
        if (args.length == 0) {
            logger.error("Missing properties file. Bye");
            return;
        }

        logger.debug("Loading configurations from " + args[0]);
        WebMonitor webMonitor = new WebMonitor();
        webMonitor.loadProps(args[0]);
        webMonitor.runMonitor();
    }

    public void runMonitor() throws IOException {
        boolean in = false;
        boolean notifyUser = false;
        boolean newHistory = true;

        driver = new ChromeDriver();
        Path path = Paths.get(properties.getProperty(PROP_HISTORY));
        String current = "$10,011";
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss"));

        current = getCurrentPrice();

        List<String> historyData = Files.readAllLines(path);
        String lastHistory = historyData.get(historyData.size()-1);

        String[] historyRegistry = lastHistory.split("[|]");

        if (historyRegistry.length > 2 && historyRegistry[2].compareTo("IT") == 0) {
            in = true;
        }

        Integer lastPrice = Integer.valueOf(historyRegistry[1].replaceAll("[$$\\,]", ""));
        Integer currentPrice = Integer.valueOf(current.replaceAll("[$$\\,]", ""));

        Integer delta = Math.subtractExact(currentPrice, lastPrice);

        String message;
        byte[] historyEntry = ("\n" + date + "|" + current).getBytes();

        if (!in && Math.abs(delta) == 0) {
            message = "No price changes detected. Bye.";
            newHistory = false;
        } else if (!in && Math.abs(delta) < 20) {
            message = String.format("Small price change detected: %s to %s ($%s)", lastHistory, current, delta);
        } else if (in) {
            if (Math.abs(delta) < 10) {
                message = String.format("DONE: %s to %s ($%s)", lastHistory, current, delta);
                notifyUser = true;
            } else {
                message = "In progress. Enjoy!";
                historyEntry = ("\n" + date + "|" + current +"|IT").getBytes();
            }
        } else {
            message = String.format("Price change detected: %s to %s ($%s)", lastHistory, current, delta);
            historyEntry = ("\n" + date + "|" + current +"|IT").getBytes();
            notifyUser = true;
        }

        if (newHistory) {
            Files.write(path, historyEntry, StandardOpenOption.APPEND);
        }

        logger.info(message);

        if (notifyUser) {
            SnsClient snsClient = SnsClient.builder()
                    .region(Region.US_EAST_1)
                    .build();
            pubTextSMS(snsClient, message, properties.getProperty(PROP_DEST));
            snsClient.close();
        }

        driver.quit();
    }

    private String getCurrentPrice() {
        String current;
        driver.get(URL);

        final WebElement email = driver.findElement(By.xpath(INPUT_EMAIL));
        final WebElement pass = driver.findElement(By.xpath(INPUT_PASS));

        assertEquals("Email/Username", email.getAttribute("placeholder"));
        assertEquals("Password", pass.getAttribute("placeholder"));

        email.sendKeys(properties.getProperty(PROP_ACCESS));
        pass.sendKeys(properties.getProperty(PROP_KEY));
        pass.submit();

        new WebDriverWait(driver, TIMEOUT)
                .until(ExpectedConditions.presenceOfElementLocated(By.xpath(GST_NM_LT)));

        final List<WebElement> dataList = driver.findElements(By.xpath(GST_NM_LT));

        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

        current = dataList.get(dataList.size()-1).getText();
        return current;
    }

    private void pubTextSMS(SnsClient snsClient, String message, String phoneNumber) {
        try {
            PublishRequest request = PublishRequest.builder()
                    .message(message)
                    .phoneNumber(phoneNumber)
                    .build();

            PublishResponse result = snsClient.publish(request);
            logger.info(result.messageId() + " Message sent. Status was " + result.sdkHttpResponse().statusCode());
        } catch (SnsException e) {
            logger.error(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    private void loadProps(String file) throws IOException {
        properties = PropertiesLoader.loadProperties(file);
    }
}
