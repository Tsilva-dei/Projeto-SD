package webapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Googol Web Application - Meta 2
 * 
 * Esta é a classe principal da aplicação web.
 * O @SpringBootApplication faz 3 coisas:
 * 1. @Configuration - diz que esta classe tem configurações
 * 2. @EnableAutoConfiguration - Spring configura automaticamente baseado nas dependências
 * 3. @ComponentScan - procura por Controllers, Services, etc. neste package
 * 
 * FUNCIONAMENTO:
 * - Inicia um servidor HTTP na porta 8080 (por defeito)
 * - Carrega todos os Controllers e Services
 * - Fica à escuta de pedidos HTTP dos browsers
 */
@SpringBootApplication
public class WebApplication {
    
    /**
     * Ponto de entrada da aplicação web
     * 
     * Para executar:
     * 1. Certifica-te que o URLQueue, Gateway e Barrels estão a correr (Meta 1)
     * 2. Executa: mvn spring-boot:run
     * 3. Abre o browser em: http://localhost:8080
     */
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║           GOOGOL - Web Search Engine                     ║");
        System.out.println("║              Starting Web Server...                      ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        
        // Inicia o Spring Boot
        SpringApplication.run(WebApplication.class, args);
        
        System.out.println("\n✓ Web server is running!");
        System.out.println("✓ Open your browser at: http://localhost:8080");
        System.out.println("✓ Press Ctrl+C to stop\n");
    }
}