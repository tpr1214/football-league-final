package org.example.footballleague.Service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseService {
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter addEmitter() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        this.emitters.add(emitter);
        emitter.onCompletion(() -> this.emitters.remove(emitter));
        emitter.onTimeout(() -> this.emitters.remove(emitter));
        emitter.onError((error) -> this.emitters.remove(emitter));
        return emitter;
    }

    public void broadcastMatchUpdate(Object eventData) {
        broadcast("match-update", eventData);
    }

    public void broadcastGoal(Object eventData) {
        broadcast("goal", eventData);
    }

    public void broadcastRoundComplete(Object eventData) {
        broadcast("round-complete", eventData);
    }

    private void broadcast(String eventName, Object eventData) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(eventData));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }
}
