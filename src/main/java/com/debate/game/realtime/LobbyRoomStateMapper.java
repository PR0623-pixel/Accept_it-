package com.debate.game.realtime;

import com.debate.game.dto.LobbySnapshotResponse;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Frontend types.ts RoomState representation for the LOBBY phase only. */
@Component
public class LobbyRoomStateMapper {
    private final Clock clock;

    public LobbyRoomStateMapper() { this(Clock.systemUTC()); }
    // Package-private for deterministic tests.
    LobbyRoomStateMapper(Clock clock) { this.clock = clock; }

    public Map<String, Object> map(LobbySnapshotResponse snapshot) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("roomCode", snapshot.roomCode());
        state.put("serverTime", clock.millis());
        state.put("phase", snapshot.phase());
        List<Map<String, Object>> players = new ArrayList<>();
        for (LobbySnapshotResponse.LobbyPlayer player : snapshot.players()) {
            Map<String, Object> member = new LinkedHashMap<>();
            member.put("id", player.id());
            member.put("nickname", player.nickname());
            member.put("role", player.role());
            member.put("ready", player.ready());
            member.put("stance", null);
            member.put("hasVoted", false);
            member.put("isHost", player.isHost());
            players.add(member);
        }
        state.put("players", players);
        // Frontend RoomSettings currently contains only three editable fields.
        state.put("settings", Map.of(
                "maxItems", snapshot.settings().maxItems(),
                "itemSeconds", snapshot.settings().itemSeconds(),
                "cardsPerItem", snapshot.settings().cardsPerItem()));
        var item = snapshot.currentItem();
        state.put("currentItem", item == null ? null : Map.of(
                    "index", item.index(),
                    "name", item.name(),
                    "description", item.description()
        ));
        state.put("itemEndsAt", null);
        state.put("speech", null);
        state.put("lastSpeakerId", null);
        state.put("pendingRebuttal", null);
        state.put("effect", null);
        state.put("results", List.of());
        return state;
    }
}
