package webapp.service;

import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import javax.naming.directory.SearchResult;

import java.io.ObjectInputFilter.Config;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

/**
 * GoogolService - Camada de serviço que comunica com o Gateway RMI
 * 
 * Esta classe é um @Service do Spring, o que significa:
 * - Spring cria UMA instância (singleton)
 * - Pode ser injetada noutras classes (@Autowired)
 * - Gere a ligação RMI ao Gateway da Meta 1
 * 
 * PADRÃO MVC:
 * Controller → Service → RMI Gateway → Storage Barrels
 */
@Service
public class GoogolService {
    
    private GatewayInterface gateway;
    private boolean connected = false;
    
    /**
     * @PostConstruct é chamado automaticamente depois do construtor
     * É aqui que fazemos a ligação ao RMI
     */
    @PostConstruct
    public void init() {
        connectToGateway();
    }
    
    /**
     * Estabelece ligação ao Gateway RMI (da Meta 1)
     */
    private void connectToGateway() {
        try {
            // Lê configuração (assumindo que Config.java está acessível)
            // Se não estiver, usa valores por defeito
            String rmiHost = getConfigProperty("rmi.host", "localhost");
            int rmiPort = getConfigPropertyInt("rmi.port", 1099);
            
            System.out.println("Connecting to RMI Gateway at " + rmiHost + ":" + rmiPort);
            
            Registry registry = LocateRegistry.getRegistry(rmiHost, rmiPort);
            gateway = (GatewayInterface) registry.lookup("Gateway");
            
            connected = true;
            System.out.println("✓ Connected to Googol Gateway successfully!");
            
        } catch (Exception e) {
            connected = false;
            System.err.println("✗ Failed to connect to Gateway: " + e.getMessage());
            System.err.println("  Make sure URLQueue, Gateway and Barrels are running!");
        }
    }
    
    /**
     * Verifica se está ligado ao Gateway
     */
    public boolean isConnected() {
        return connected;
    }
    
    /**
     * Tenta reconectar se a ligação foi perdida
     */
    private void ensureConnected() throws Exception {
        if (!connected) {
            connectToGateway();
            if (!connected) {
                throw new Exception("Not connected to Gateway. Please start the RMI services.");
            }
        }
    }
    
    /**
     * FUNCIONALIDADE 1: Indexar um novo URL
     */
    public void indexURL(String url) throws Exception {
        ensureConnected();
        try {
            gateway.indexURL(url);
            System.out.println("URL sent to indexing queue: " + url);
        } catch (RemoteException e) {
            connected = false;
            throw new Exception("Failed to index URL: " + e.getMessage());
        }
    }
    
    /**
     * FUNCIONALIDADE 2: Pesquisar páginas
     * Retorna todos os resultados (sem paginação por agora)
     */
    public List<SearchResult> search(String query) throws Exception {
        ensureConnected();
        try {
            List<SearchResult> results = gateway.search(query);
            System.out.println("Search completed for '" + query + "': " + results.size() + " results");
            return results;
        } catch (RemoteException e) {
            connected = false;
            throw new Exception("Search failed: " + e.getMessage());
        }
    }
    
    /**
     * FUNCIONALIDADE 3: Pesquisar com paginação
     */
    public List<SearchResult> searchPaginated(String query, int page, int pageSize) throws Exception {
        ensureConnected();
        try {
            return gateway.searchPaginated(query, page, pageSize);
        } catch (RemoteException e) {
            connected = false;
            throw new Exception("Search failed: " + e.getMessage());
        }
    }
    
    /**
     * FUNCIONALIDADE 4: Obter links que apontam para um URL
     */
    public Set<String> getIncomingLinks(String url) throws Exception {
        ensureConnected();
        try {
            return gateway.getIncomingLinks(url);
        } catch (RemoteException e) {
            connected = false;
            throw new Exception("Failed to get incoming links: " + e.getMessage());
        }
    }
    
    /**
     * FUNCIONALIDADE 5: Obter estatísticas do sistema
     */
    public SystemStats getStatistics() throws Exception {
        ensureConnected();
        try {
            return gateway.getStatistics();
        } catch (RemoteException e) {
            connected = false;
            throw new Exception("Failed to get statistics: " + e.getMessage());
        }
    }
    
    // ========== Métodos auxiliares para ler configuração ==========
    
    private String getConfigProperty(String key, String defaultValue) {
        try {
            // Tenta usar a classe Config da Meta 1
            return Config.getProperty(key, defaultValue);
        } catch (Exception e) {
            // Se não conseguir, usa valor por defeito
            return defaultValue;
        }
    }
    
    private int getConfigPropertyInt(String key, int defaultValue) {
        try {
            String value = getConfigProperty(key, String.valueOf(defaultValue));
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}

/**
 * NOTA IMPORTANTE:
 * 
 * Estas interfaces (GatewayInterface, SearchResult, SystemStats, etc.)
 * têm de estar ACESSÍVEIS a este código.
 * 
 * Opções:
 * 1. Copiar as classes para o package webapp.model
 * 2. Usar as classes originais da Meta 1 (adicionar ao classpath)
 * 3. Criar um JAR com as classes comuns
 * 
 * Por agora, vou assumir opção 2 (mais simples).
 * Se deres erro de compilação, avisa que eu ajudo!
 */