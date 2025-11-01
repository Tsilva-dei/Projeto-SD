import java.rmi.*;
import java.rmi.registry.*;
import java.util.*;

/**
 * RPC/RMI Client - Simple user interface to interact with Googol
 * 
 * FEATURES:
 * - Connection retry logic
 * - Real-time statistics updates
 * - Paginated search results
 * - Clean error handling
 */
public class Client {
    
    private GatewayInterface gateway;
    private Scanner scanner;
    private Timer statsUpdateTimer;
    
    public Client() {
        this.scanner = new Scanner(System.in);
    }
    
    /**
     * Connect to Gateway with retry
     */
    public boolean connect() {
        int maxRetries = 3;
        int retryDelay = 2000;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Registry registry = LocateRegistry.getRegistry(Config.getRMIHost(), Config.getRMIPort());
                gateway = (GatewayInterface) registry.lookup("Gateway");
                System.out.println("Connected to Googol Gateway at " + Config.getRMIHost() + ":" + Config.getRMIPort());
                return true;
            } catch (Exception e) {
                System.err.println("Connection attempt " + attempt + " failed: " + e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Main menu
     */
    public void run() {
        if (!connect()) {
            System.out.println("Cannot start client without Gateway connection");
            return;
        }
        
        boolean running = true;
        
        while (running) {
            try {
                printMenu();
                String choice = scanner.nextLine().trim();
                
                switch (choice) {
                    case "1":
                        indexURL();
                        break;
                    case "2":
                        search();
                        break;
                    case "3":
                        getIncomingLinks();
                        break;
                    case "4":
                        viewStatistics();
                        break;
                    case "5":
                        viewStatisticsRealTime();
                        break;
                    case "6":
                        running = false;
                        System.out.println("Goodbye!");
                        break;
                    default:
                        System.out.println("Invalid option. Please try again.");
                }
                
                System.out.println();
                
            } catch (RemoteException e) {
                System.err.println("Connection error: " + e.getMessage());
                System.out.println("Attempting to reconnect...");
                if (!connect()) {
                    System.out.println("Failed to reconnect. Exiting.");
                    break;
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        if (statsUpdateTimer != null) {
            statsUpdateTimer.cancel();
        }
        scanner.close();
    }
    
    /**
     * Print main menu
     */
    private void printMenu() {
        System.out.println("\n" + "=".repeat(40));
        System.out.println("     GOOGOL - Web Search Engine");
        System.out.println("=".repeat(40));
        System.out.println("1. Index a URL");
        System.out.println("2. Search");
        System.out.println("3. Get incoming links for a URL");
        System.out.println("4. View statistics (snapshot)");
        System.out.println("5. View statistics (real-time)");
        System.out.println("6. Exit");
        System.out.println("=".repeat(40));
        System.out.print("Choose an option: ");
    }
    
    /**
     * Index a new URL
     */
    private void indexURL() throws RemoteException {
        System.out.print("Enter URL to index: ");
        String url = scanner.nextLine().trim();
        
        if (url.isEmpty()) {
            System.out.println("❌ URL cannot be empty");
            return;
        }
        
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            System.out.println("❌ URL must start with http:// or https://");
            return;
        }
        
        gateway.indexURL(url);
        System.out.println("✅ URL queued for indexing: " + url);
        System.out.println("   The page will be downloaded and indexed shortly.");
    }
    
    /**
     * Search for pages
     */
    private void search() throws RemoteException {
        System.out.print("Enter search terms: ");
        String query = scanner.nextLine().trim();
        
        if (query.isEmpty()) {
            System.out.println("❌ Search query cannot be empty");
            return;
        }
        
        System.out.println("🔍 Searching for: \"" + query + "\"");
        
        int currentPage = 0;
        int pageSize = Config.getSearchPageSize();
        boolean viewingResults = true;
        
        while (viewingResults) {
            List<SearchResult> results = gateway.searchPaginated(query, currentPage, pageSize);
            
            if (results.isEmpty() && currentPage == 0) {
                System.out.println("📭 No results found for \"" + query + "\"");
                return;
            }
            
            if (results.isEmpty()) {
                System.out.println("📭 No more results.");
                if (currentPage > 0) {
                    currentPage--;
                    continue;
                } else {
                    return;
                }
            }
            
            System.out.println("\n" + "=".repeat(80));
            System.out.println("Search Results - Page " + (currentPage + 1) + 
                             " (showing " + results.size() + " results)");
            System.out.println("=".repeat(80));
            
            for (int i = 0; i < results.size(); i++) {
                SearchResult result = results.get(i);
                int globalIndex = currentPage * pageSize + i + 1;
                
                System.out.println("\n[" + globalIndex + "] " + result.title);
                System.out.println("    🔗 " + result.url);
                System.out.println("    📄 " + result.citation);
                System.out.println("    👉 " + result.incomingLinks + " incoming link(s)");
            }
            
            System.out.println("\n" + "-".repeat(80));
            System.out.println("Commands: [N]ext | [P]revious | [B]ack to menu");
            System.out.print("Choose: ");
            String nav = scanner.nextLine().trim().toLowerCase();
            
            switch (nav) {
                case "n":
                case "next":
                case "":
                    currentPage++;
                    break;
                case "p":
                case "previous":
                    if (currentPage > 0) {
                        currentPage--;
                    } else {
                        System.out.println("⚠️  Already on first page");
                    }
                    break;
                case "b":
                case "back":
                    viewingResults = false;
                    break;
                default:
                    System.out.println("⚠️  Invalid option");
            }
        }
    }
    
    /**
     * Get incoming links for a URL
     */
    private void getIncomingLinks() throws RemoteException {
        System.out.print("Enter URL: ");
        String url = scanner.nextLine().trim();
        
        if (url.isEmpty()) {
            System.out.println("❌ URL cannot be empty");
            return;
        }
        
        Set<String> incomingLinks = gateway.getIncomingLinks(url);
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Incoming Links for: " + url);
        System.out.println("=".repeat(80));
        
        if (incomingLinks.isEmpty()) {
            System.out.println("📭 No incoming links found");
        } else {
            int count = 1;
            for (String link : incomingLinks) {
                System.out.println(count++ + ". " + link);
            }
            System.out.println("\n✅ Total: " + incomingLinks.size() + " incoming link(s)");
        }
    }
    
    /**
     * View system statistics (snapshot)
     */
    private void viewStatistics() throws RemoteException {
        SystemStats stats = gateway.getStatistics();
        displayStatistics(stats);
    }
    
    /**
     * View system statistics with real-time updates
     */
    private void viewStatisticsRealTime() {
        System.out.println("\n🔴 REAL-TIME STATISTICS MODE");
        System.out.println("Statistics will update automatically every " + 
                         (Config.getGatewayStatsUpdateInterval() / 1000) + " seconds");
        System.out.println("Press ENTER to stop...\n");
        
        // Cancel any existing timer
        if (statsUpdateTimer != null) {
            statsUpdateTimer.cancel();
        }
        
        // Create new timer for updates
        statsUpdateTimer = new Timer("StatsUpdate", true);
        statsUpdateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    // Clear screen (ANSI escape code - works on most terminals)
                    System.out.print("\033[H\033[2J");
                    System.out.flush();
                    
                    SystemStats stats = gateway.getStatistics();
                    displayStatistics(stats);
                    System.out.println("\n⏱️  Auto-updating... Press ENTER to stop");
                } catch (Exception e) {
                    System.err.println("Failed to update statistics: " + e.getMessage());
                }
            }
        }, 0, Config.getGatewayStatsUpdateInterval());
        
        // Wait for user input to stop
        scanner.nextLine();
        statsUpdateTimer.cancel();
        
        System.out.println("\n✅ Stopped real-time updates");
    }
    
    /**
     * Display statistics in formatted way
     */
    private void displayStatistics(SystemStats stats) {
        System.out.println("╔" + "═".repeat(78) + "╗");
        System.out.println("║" + centerText("SYSTEM STATISTICS", 78) + "║");
        System.out.println("╠" + "═".repeat(78) + "╣");
        
        // Top searches
        System.out.println("║ 🔥 TOP 10 SEARCHES" + " ".repeat(58) + "║");
        System.out.println("╠" + "─".repeat(78) + "╣");
        
        if (stats.topSearches.isEmpty()) {
            System.out.println("║   No searches yet" + " ".repeat(59) + "║");
        } else {
            int rank = 1;
            for (Map.Entry<String, Integer> entry : stats.topSearches.entrySet()) {
                String line = String.format("   %2d. %-50s %5d searches", 
                                           rank++, 
                                           truncate(entry.getKey(), 50), 
                                           entry.getValue());
                System.out.println("║" + padRight(line, 78) + "║");
            }
        }
        
        // Storage barrels
        System.out.println("╠" + "═".repeat(78) + "╣");
        System.out.println("║ 🗄️  STORAGE BARRELS" + " ".repeat(57) + "║");
        System.out.println("╠" + "─".repeat(78) + "╣");
        
        if (stats.barrelStats.isEmpty()) {
            System.out.println("║   ⚠️  No active barrels" + " ".repeat(53) + "║");
        } else {
            for (BarrelStats bs : stats.barrelStats) {
                String line = String.format("   Barrel %s: %,6d pages | Avg search: %.1f deciseconds", 
                                           bs.barrelId, 
                                           bs.indexSize, 
                                           bs.avgSearchTime);
                System.out.println("║" + padRight(line, 78) + "║");
            }
        }
        
        System.out.println("╚" + "═".repeat(78) + "╝");
    }
    
    private String centerText(String text, int width) {
        int padding = (width - text.length()) / 2;
        return " ".repeat(padding) + text + " ".repeat(width - padding - text.length());
    }
    
    private String padRight(String text, int width) {
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        return text + " ".repeat(width - text.length());
    }
    
    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
    
    public static void main(String[] args) {
        Client client = new Client();
        client.run();
    }
}