package com.debate.game.Ingame;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RoomGameStateStore {

    private final ConcurrentMap<String, RoomGameState> states =
            new ConcurrentHashMap<>();

    public void save(RoomGameState state) {
        states.put(state.getRoomCode(), state);
    }

    public Optional<RoomGameState> find(String roomCode) {
        return Optional.ofNullable(states.get(roomCode));
    }

    /** Stable list for a timer sweep while rooms may be opened or closed. */
    public List<RoomGameState> snapshot() {
        return List.copyOf(states.values());
    }

    public void remove(String roomCode) {
        states.remove(roomCode);
    }
}