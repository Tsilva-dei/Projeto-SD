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

    //Página Inicial
    @GetMapping("/")
    public String index(Model model) {
        try {
            if (googolService.isConnected()){
                model.addAttribute("Stats", googolService.getStatistics());
            }
        } catch (Exception e) {
            model.addAttribute("error", "Erro na ligação ao Gateway");
        }
        return "index";
    }

    @GetMapping("/search")
    public String search (@RequestParam("query") String query, @RequestParam("page") int page, Model model) {
        try {
            List<SearchResult> results = googolService.searchPaginated(query, page, 10);

            model.addAttribute("results", results);
            model.addAttribute("query", query);
            model.addAttribute("page", page);

            //Botões next/previous
            model.addAttribute("nextPage", page + 1);
            model.addAttribute("previousPage", page - 1);
            model.addAttribute("showPrevious", page > 0);
            model.addAttribute("showNext", page >= 10);

        } catch (Exception e) {
            model.addAttribute("error", "Erro na pesquisa" + e.getMessage());
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
}