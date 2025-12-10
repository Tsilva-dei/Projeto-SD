package webapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WebApplication {
    
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