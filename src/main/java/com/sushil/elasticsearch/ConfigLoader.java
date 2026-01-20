package com.sushil.elasticsearch;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class ConfigLoader {

    // private static final String CONFIG_PATH =
    // "src\\main\\resources\\config.properties";
    // private static final String CONFIG_PATH =
    // "/elkapp/app/uptimereports/config.properties";

    // private static final String CONFIG_PATH =
    // "src\\main\\resources\\config\\config_final_16jan.properties";
    // private static final String CONFIG_PATH =
    // "/elkapp/app/uptimereports/config/config_final_16jan.properties";

    // private static final String CONFIG_PATH =
    // "src\\main\\resources\\config\\config_final_12feb.properties";
    // private static final String CONFIG_PATH =
    // "/elkapp/app/uptimereports/config/config_final_12feb.properties";

    // private static final String CONFIG_PATH =
    // "src\\main\\resources\\config\\config_final_17mar.properties";
    // private static final String CONFIG_PATH =
    // "/elkapp/app/uptimereports/config/config_final_17mar.properties";

    // private static String
    // CONFIG_PATH=System.getProperty("config.path","src\\main\\resources\\config\\config_final_17mar.properties");

    // private static final Properties properties = new Properties();

    // // Static block to load configuration
    // static {
    // try (FileInputStream input = new FileInputStream(CONFIG_PATH)) {
    // properties.load(input);
    // } catch (IOException e) {
    // throw new RuntimeException("Error loading config.properties from path: " +
    // CONFIG_PATH, e);
    // }
    // }

    private static final String DEFAULT_CONFIG_PATH = "src/main/resources/config/config_final_17mar.properties";
    private static final String CONFIG_PATH = System.getProperty("config.path", System.getenv("CONFIG_PATH")) != null
            ? System.getProperty("config.path", System.getenv("CONFIG_PATH"))
            : DEFAULT_CONFIG_PATH;

    private static final Properties properties = new Properties();

    // Static block to load configuration
    static {
        try (FileInputStream input = new FileInputStream(CONFIG_PATH)) {
            properties.load(input);
            System.out.println("Config loaded from: " + CONFIG_PATH);
        } catch (IOException e) {
            throw new RuntimeException("Error loading config.properties from path: " + CONFIG_PATH, e);
        }
    }

    public static String get(String key) {
        return properties.getProperty(key);
    }

    public static int getInt(String key) {
        return Integer.parseInt(properties.getProperty(key));
    }

    public static List<String> getList(String key) {
        return Arrays.asList(properties.getProperty(key).split(","));
    }
}
