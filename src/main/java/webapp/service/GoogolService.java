package webapp.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import jakarta.annotation.PostConstruct;
import rmi.Config;
import rmi.GatewayInterface;
import rmi.SystemStats;
import rmi.SearchResult;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

@Service
public class GoogolService {
    
    private GatewayInterface gateway;
    private boolean connected = false;
    
    @PostConstruct
    public void init() {
        connectToGateway();
    }

    // Estabelece ligação ao Gateway RMI
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
    
    // Verifica se está ligado ao Gateway
    public boolean isConnected() {
        return connected;
    }
    
    // Tenta reconectar se a ligação foi perdida
    private void ensureConnected() throws Exception {
        if (!connected) {
            connectToGateway();
            if (!connected) {
                throw new Exception("Not connected to Gateway. Please start the RMI services.");
            }
        }
    }
    // FUNCIONALIDADE 1: Indexar um novo URL    
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
    
    // FUNCIONALIDADE 2: Pesquisar páginas
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
    
    // FUNCIONALIDADE 3: Pesquisar com paginação
    public List<SearchResult> searchPaginated(String query, int page, int pageSize) throws Exception {
        ensureConnected();
        try {
            return gateway.searchPaginated(query, page, pageSize);
        } catch (RemoteException e) {
            connected = false;
            throw new Exception("Search failed: " + e.getMessage());
        }
    }
    
    // FUNCIONALIDADE 4: Obter links que apontam para um URL
    public Set<String> getIncomingLinks(String url) throws Exception {
        ensureConnected();
        try {
            return gateway.getIncomingLinks(url);
        } catch (RemoteException e) {
            connected = false;
            throw new Exception("Failed to get incoming links: " + e.getMessage());
        }
    }

    // FUNCIONALIDADE 5: Obter estatísticas do sistema
    public SystemStats getStatistics() throws Exception {
        ensureConnected();
        try {
            return gateway.getStatistics();
        } catch (RemoteException e) {
            connected = false;
            throw new Exception("Failed to get statistics: " + e.getMessage());
        }
    }

    // FUNCIONALIDADE 6: Indexar histórias do HackerNews
    public void indexHackerNews() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String topStoriesUrl = "https://hacker-news.firebaseio.com/v0/topstories.json";

            // Obter Histórias
            ResponseEntity<List<Integer>> response = restTemplate.exchange(
                topStoriesUrl,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Integer>>() {}
            );
            
            List<Integer> storyIds = response.getBody();

            if (storyIds != null) {
                int limit = Math.min(storyIds.size(), 10);

                for (int i = 0; i < limit; i++) {
                    Integer id = storyIds.get(i);
                    String storyUrl = "https://hacker-news.firebaseio.com/v0/item/" + id + ".json";

                    // Obter Detalhes de cada história
                    ResponseEntity<Map<String, Object>> storyResponse = restTemplate.exchange(
                        storyUrl,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<Map<String, Object>>() {}
                    );

                    Map<String, Object> story = storyResponse.getBody();

                    if (story != null && story.containsKey("url")) {
                        String urlToIndex = (String) story.get("url");
                        indexURL(urlToIndex);
                        System.out.println("HackerNews story indexed: " + urlToIndex);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro na API HackerNews: " + e.getMessage());
        }
    }

    // FUNCIONALIDADE 7: Geração de Summaries
    @SuppressWarnings("unchecked")
    public String generateAISummary(String query, List<String> snippets) {
        String apiKey = Config.getGeminiApiKey();

        try {
            RestTemplate restTemplate = new RestTemplate();
            // URL da API do Gemini
            String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;
            // Headers
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            // Preparação do prompt
            String prompt = "Resume em 1 parágrafo o que sabes sobre " + query + " com base nestes excertos: "
                    + String.join("|", snippets);
            Map<String, String> part = new HashMap<>();
            part.put("text", prompt);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", Collections.singletonList(part));

            Map<String, Object> body = new HashMap<>();
            body.put("contents", Collections.singletonList(content));

            org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            // Extrair resposta do JSON
            if (response.getBody() != null) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.getBody().get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map<String, Object> finalContent = (Map<String, Object>) candidates.get(0).get("content");
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) finalContent.get("parts");
                    if (!parts.isEmpty()) {
                        return (String) parts.get(0).get("text");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro na API Gemini: " + e.getMessage());
            return "Não foi possível gerar o resumo Gemini no momento (Verificar Logs)..";
        }
        return "Sem resposta do Gemini.";
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