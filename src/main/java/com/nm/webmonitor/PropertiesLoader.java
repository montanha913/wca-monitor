package com.nm.webmonitor;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class PropertiesLoader {

    public static Properties loadProperties(String resourceFileName) throws IOException {
        Properties configuration = new Properties();
        configuration.load(new FileInputStream(resourceFileName));
        return configuration;
    }

}