package webapp.scheduler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rmi.SystemStats;
import webapp.service.GoogolService;

@Component
public class StatsScheduler {
    
    @Autowired
    private SimpMessagingTemplate template; // Enviar mensagens via WebSocket

    @Autowired
    private GoogolService googolService;

    @Scheduled(fixedRate = 3000)
    public void sendStats() {
        if (googolService.isConnected()) {
            try {
                // Pede stats atualizados ao Gateway RMI
                SystemStats stats = googolService.getStatistics();

                template.convertAndSend("/topic/stats", stats);
            } catch (Exception e) {
                System.err.println("Erro ao atualizar stats via Websocket: " + e.getMessage());
            }
        }
    }
}
