package rmi;

import java.io.*;
import java.util.Properties;

/**
 * Configuration Loader - Loads settings from config.properties file
 * Eliminates hardcoded values and allows runtime configuration changes
 * without recompilation
 */
public class Config {
    private static Properties props = new Properties();
    private static final String CONFIG_FILE = "config.properties";
    
    static {
        loadProperties();
    }
    
    /**
     * Load configuration from properties file
     */
    private static void loadProperties() {
        InputStream input = null;
        try {
            // Try to load from file system first
            File configFile = new File(CONFIG_FILE);
            if (configFile.exists()) {
                input = new FileInputStream(configFile);
                props.load(input);
                System.out.println("Configuration loaded from " + CONFIG_FILE);
            } else {
                System.err.println("Warning: Could not find " + CONFIG_FILE + ", using defaults");
                setDefaults();
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load " + CONFIG_FILE + ", using defaults: " + e.getMessage());
            setDefaults();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }
    
    /**
     * Set default values if config file not found
     */
    private static void setDefaults() {
        props.setProperty("rmi.host", "localhost");
        props.setProperty("rmi.port", "1099");
        props.setProperty("server.host", "localhost");
        props.setProperty("server.port", "8080");
        props.setProperty("downloader.retry.count", "3");
        props.setProperty("downloader.retry.delay.ms", "1000");
        props.setProperty("downloader.sleep.no.urls.ms", "2000");
        props.setProperty("downloader.jsoup.timeout.ms", "5000");
        props.setProperty("downloader.user.agent", "Mozilla/5.0 (Googol Bot)");
        props.setProperty("search.page.size", "10");
        props.setProperty("search.min.word.length", "3");
        props.setProperty("gateway.barrel.selection", "round-robin");
        props.setProperty("gateway.cache.enabled", "true");
        props.setProperty("gateway.stats.update.interval.ms", "1000");
        props.setProperty("barrel.persistence.enabled", "true");
        props.setProperty("barrel.persistence.directory", "data");
        props.setProperty("barrel.persistence.autosave.interval.ms", "30000");
        props.setProperty("barrel.sync.timeout.ms", "5000");
        props.setProperty("queue.bloom.filter.enabled", "false");
        props.setProperty("logging.level", "INFO");
    }
    
    // RMI Configuration
    public static String getRMIHost() {
        return props.getProperty("rmi.host", "localhost");
    }
    
    public static int getRMIPort() {
        return Integer.parseInt(props.getProperty("rmi.port", "1099"));
    }
    
    // Downloader Configuration
    public static int getDownloaderRetryCount() {
        return Integer.parseInt(props.getProperty("downloader.retry.count", "3"));
    }
    
    public static int getDownloaderRetryDelay() {
        return Integer.parseInt(props.getProperty("downloader.retry.delay.ms", "1000"));
    }
    
    public static int getDownloaderSleepNoURLs() {
        return Integer.parseInt(props.getProperty("downloader.sleep.no.urls.ms", "2000"));
    }
    
    public static int getDownloaderJsoupTimeout() {
        return Integer.parseInt(props.getProperty("downloader.jsoup.timeout.ms", "5000"));
    }
    
    public static String getDownloaderUserAgent() {
        return props.getProperty("downloader.user.agent", "Mozilla/5.0 (Googol Bot)");
    }
    
    // Search Configuration
    public static int getSearchPageSize() {
        return Integer.parseInt(props.getProperty("search.page.size", "10"));
    }
    
    public static int getSearchMinWordLength() {
        return Integer.parseInt(props.getProperty("search.min.word.length", "3"));
    }
    
    // Gateway Configuration
    public static String getGatewayBarrelSelection() {
        return props.getProperty("gateway.barrel.selection", "round-robin");
    }
    
    public static boolean getGatewayCacheEnabled() {
        return Boolean.parseBoolean(props.getProperty("gateway.cache.enabled", "true"));
    }
    
    public static int getGatewayStatsUpdateInterval() {
        return Integer.parseInt(props.getProperty("gateway.stats.update.interval.ms", "1000"));
    }
    
    // Storage Barrel Configuration
    public static boolean getBarrelPersistenceEnabled() {
        return Boolean.parseBoolean(props.getProperty("barrel.persistence.enabled", "true"));
    }
    
    public static String getBarrelPersistenceDirectory() {
        return props.getProperty("barrel.persistence.directory", "data");
    }
    
    public static int getBarrelPersistenceAutosaveInterval() {
        return Integer.parseInt(props.getProperty("barrel.persistence.autosave.interval.ms", "30000"));
    }
    
    public static int getBarrelSyncTimeout() {
        return Integer.parseInt(props.getProperty("barrel.sync.timeout.ms", "5000"));
    }
    
    // Queue Configuration
    public static boolean getQueueBloomFilterEnabled() {
        return Boolean.parseBoolean(props.getProperty("queue.bloom.filter.enabled", "false"));
    }
    
    public static int getQueueBloomFilterSize() {
        return Integer.parseInt(props.getProperty("queue.bloom.filter.size", "10000"));
    }
    
    public static String getLoggingLevel() {
        return props.getProperty("logging.level", "INFO");
    }
    
    public static void reload() {
        loadProperties();
    }
    
    public static String getProperty(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public static int getWebServerPort() {
        return Integer.parseInt(props.getProperty("server.port", "8080"));
    }

    public static String getWebServerHost() {
        return props.getProperty("server.host", "localhost");
    }
    
    public static String getGeminiApiKey() {
        return props.getProperty("gemini.api.key", "");
    }    
}