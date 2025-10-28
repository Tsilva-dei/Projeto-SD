import java.rmi.*;
import java.rmi.registry.*;
import java.util.*;

/**
 * RPC/RMI Client - Simple user interface to interact with Googol
 */
public class Client {
    
    private GatewayInterface gateway;
    private Scanner scanner;
    
    public Client() {
        this.scanner = new Scanner(System.in);
    }
    
    /**
     * Connect to Gateway
     */
    public boolean connect() {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            gateway = (GatewayInterface) registry.lookup("Gateway");
            System.out.println("Connected to Googol Gateway");
            return true;
        } catch (Exception e) {
            System.err.println("Failed to connect to Gateway: " + e.getMessage());
            return false;
        }
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
            }
        }
        
        scanner.close();
    }
    
    /**
     * Print main menu
     */
    private void printMenu() {
        System.out.println("=== GOOGOL - Web Search Engine ===");
        System.out.println("1. Index a URL");
        System.out.println("2. Search");
        System.out.println("3. Get incoming links for a URL");
        System.out.println("4. View statistics");
        System.out.println("5. Exit");
        System.out.print("Choose an option: ");
    }
    
    /**
     * Index a new URL
     */
    private void indexURL() throws RemoteException {
        System.out.print("Enter URL to index: ");
        String url = scanner.nextLine().trim();
        
        if (url.isEmpty()) {
            System.out.println("URL cannot be empty");
            return;
        }
        
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            System.out.println("URL must start with http:// or https://");
            return;
        }
        
        gateway.indexURL(url);
        System.out.println("URL queued for indexing: " + url);
    }
    
    /**
     * Search for pages
     */
    private void search() throws RemoteException {
        System.out.print("Enter search terms: ");
        String query = scanner.nextLine().trim();
        
        if (query.isEmpty()) {
            System.out.println("Search query cannot be empty");
            return;
        }
        
        System.out.println("Searching for: " + query);
        
        int currentPage = 0;
        int pageSize = 10;
        boolean viewingResults = true;
        
        while (viewingResults) {
            List<SearchResult> results = gateway.searchPaginated(query, currentPage, pageSize);
            
            if (results.isEmpty() && currentPage == 0) {
                System.out.println("No results found.");
                return;
            }
            
            if (results.isEmpty()) {
                System.out.println("No more results.");
                currentPage--;
                continue;
            }
            
            System.out.println("\n=== Search Results (Page " + (currentPage + 1) + ") ===");
            for (int i = 0; i < results.size(); i++) {
                SearchResult result = results.get(i);
                System.out.println("\n[" + (currentPage * pageSize + i + 1) + "] " + result.title);
                System.out.println("    URL: " + result.url);
                System.out.println("    " + result.citation);
                System.out.println("    Incoming links: " + result.incomingLinks);
            }
            
            System.out.println("\n[N]ext page | [P]revious page | [B]ack to menu");
            System.out.print("Choose: ");
            String nav = scanner.nextLine().trim().toLowerCase();
            
            switch (nav) {
                case "n":
                case "next":
                    currentPage++;
                    break;
                case "p":
                case "previous":
                    if (currentPage > 0) {
                        currentPage--;
                    } else {
                        System.out.println("Already on first page");
                    }
                    break;
                case "b":
                case "back":
                    viewingResults = false;
                    break;
                default:
                    System.out.println("Invalid option");
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
            System.out.println("URL cannot be empty");
            return;
        }
        
        Set<String> incomingLinks = gateway.getIncomingLinks(url);
        
        if (incomingLinks.isEmpty()) {
            System.out.println("No incoming links found for: " + url);
        } else {
            System.out.println("\nIncoming links for " + url + ":");
            int count = 1;
            for (String link : incomingLinks) {
                System.out.println(count++ + ". " + link);
            }
            System.out.println("\nTotal: " + incomingLinks.size() + " incoming links");
        }
    }
    
    /**
     * View system statistics
     */
    private void viewStatistics() throws RemoteException {
        SystemStats stats = gateway.getStatistics();
        
        System.out.println("\n=== SYSTEM STATISTICS ===");
        
        System.out.println("\nTop 10 Searches:");
        if (stats.topSearches.isEmpty()) {
            System.out.println("  No searches yet");
        } else {
            int rank = 1;
            for (Map.Entry<String, Integer> entry : stats.topSearches.entrySet()) {
                System.out.println("  " + rank++ + ". \"" + entry.getKey() + "\" - " + entry.getValue() + " times");
            }
        }
        
        System.out.println("\nStorage Barrels:");
        if (stats.barrelStats.isEmpty()) {
            System.out.println("  No active barrels");
        } else {
            for (BarrelStats bs : stats.barrelStats) {
                System.out.println("  " + bs);
            }
        }
    }
    
    public static void main(String[] args) {
        Client client = new Client();
        client.run();
    }
}