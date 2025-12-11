package webapp.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import webapp.service.GoogolService;
import rmi.SearchResult;
import java.util.List;

@Controller
public class SearchController {
    
    @Autowired
    private GoogolService googolService;

    // Página Inicial
    @GetMapping("/")
    public String index(Model model) {
        try {
            if (googolService.isConnected()){
                model.addAttribute("stats", googolService.getStatistics());
            }
        } catch (Exception e) {
            model.addAttribute("error", "Erro na ligação ao Gateway");
        }
        return "index";
    }

    @GetMapping("/search")
    public String search (@RequestParam("q") String query, 
                          @RequestParam(value = "p", defaultValue = "0") int page, 
                          Model model) {
        try {
            List<SearchResult> results = googolService.searchPaginated(query, page, 10);

            model.addAttribute("results", results);
            model.addAttribute("query", query);
            model.addAttribute("page", page);

            // Botões next/previous
            model.addAttribute("nextPage", page + 1);
            model.addAttribute("prevPage", page > 0 ? page - 1 : 0);
            model.addAttribute("showPrev", page > 0);
            // Simplificação: só mostra "Próximo" se vieram 10 resultados
            model.addAttribute("showNext", results.size() >= 10); 

        } catch (Exception e) {
            model.addAttribute("error", "Erro na pesquisa: " + e.getMessage());
        }
        return "search"; // Procura search.html
    }

    @PostMapping("/index")
    public String indexURL(@RequestParam("url") String url, Model model){
        try {
            googolService.indexURL(url);
            model.addAttribute("message", "URL enviado para indexação: " + url);
        } catch (Exception e) {
            model.addAttribute("error", "Erro ao indexar URL: " + e.getMessage());
        }
        return index(model);
    }

    @PostMapping("/index/hackernews")
    public String indexHackerNews(Model model) {
        try {
            // Thread separada para não haver bloqueios na interface
            new Thread(() -> googolService.indexHackerNews()).start();

            model.addAttribute("message", "Indexando Top Stories do HackerNews...");
        } catch (Exception e) {
            model.addAttribute("error", "Erro ao iniciar indexação do HackerNews: " + e.getMessage());
        }
        return index(model);
    }

    @GetMapping("/search/summary")
    public String searchWithSummary(@RequestParam("q") String query, Model model) {
        try {
            // Pesquisa normal
            List<SearchResult> results = googolService.searchPaginated(query, 0, 10);

            // Extração de snippets
            List<String> snippets = new java.util.ArrayList<>();
            for (int i = 0; i < Math.min(results.size(), 5); i++) {
                snippets.add(results.get(i).citation);
            }

            String aiSummary = googolService.generateAISummary(query, snippets);

            model.addAttribute("results", results);
            model.addAttribute("query", query);
            model.addAttribute("page", 0);
            model.addAttribute("aiSummary", aiSummary);

            // Botões next/previous
            model.addAttribute("nextPage", 1);
            model.addAttribute("prevPage", 0);
            model.addAttribute("showPrev", false);
            model.addAttribute("showNext", results.size() >= 10);

        } catch (Exception e) {
            model.addAttribute("error", "Erro ao gerar " + e.getMessage());
        }
        return "search";
    }
}