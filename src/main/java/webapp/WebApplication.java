package webapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import rmi.Config;
import java.util.Collections;

@SpringBootApplication
@EnableScheduling
public class WebApplication {
    
    public static void main(String[] args) {
        // Configuração
        SpringApplication app = new SpringApplication(WebApplication.class);
        app.setDefaultProperties(Collections.singletonMap("server.port", Config.getWebServerPort()));
        app.run(args);

        // Lê valores para a mensagem
        String host = Config.getWebServerHost();
        int port = Config.getWebServerPort();
        
        System.out.println("\nWeb server is running!");
        System.out.println("Open your browser at: http://" + host + ":" + port);
        System.out.println("Press Ctrl+C to stop\n");
    }
}