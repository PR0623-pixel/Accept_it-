package com.debate.game.Ingame;

import org.springframework.stereotype.Component;

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

    public void remove(String roomCode) {
        states.remove(roomCode);
    }
}