package org.example.footballleague.Service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseService {
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Map<Long, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    public SseEmitter addEmitter() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        this.emitters.add(emitter);
        emitter.onCompletion(() -> this.emitters.remove(emitter));
        emitter.onTimeout(() -> this.emitters.remove(emitter));
        emitter.onError((error) -> this.emitters.remove(emitter));
        return emitter;
    }

    public SseEmitter addEmitter(Long userId) {
        if (userId == null) {
            return addEmitter();
        }

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        List<SseEmitter> emittersForUser = userEmitters.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>());
        emittersForUser.add(emitter);

        Runnable remove = () -> removeUserEmitter(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError((error) -> remove.run());
        return emitter;
    }

    public void broadcastMatchUpdate(Object eventData) {
        broadcast("match-update", eventData);
    }

    public void broadcastMatchUpdate(Long userId, Object eventData) {
        broadcast(userId, "match-update", eventData);
    }

    public void broadcastGoal(Object eventData) {
        broadcast("goal", eventData);
    }

    public void broadcastGoal(Long userId, Object eventData) {
        broadcast(userId, "goal", eventData);
    }

    public void broadcastRoundComplete(Object eventData) {
        broadcast("round-complete", eventData);
    }

    public void broadcastRoundComplete(Long userId, Object eventData) {
        broadcast(userId, "round-complete", eventData);
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

    private void broadcast(Long userId, String eventName, Object eventData) {
        if (userId == null) {
            broadcast(eventName, eventData);
            return;
        }

        for (SseEmitter emitter : userEmitters.getOrDefault(userId, List.of())) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(eventData));
            } catch (IOException | IllegalStateException e) {
                removeUserEmitter(userId, emitter);
            }
        }
    }

    private void removeUserEmitter(Long userId, SseEmitter emitter) {
        List<SseEmitter> emittersForUser = userEmitters.get(userId);
        if (emittersForUser == null) {
            return;
        }
        emittersForUser.remove(emitter);
        if (emittersForUser.isEmpty()) {
            userEmitters.remove(userId);
        }
    }
}
