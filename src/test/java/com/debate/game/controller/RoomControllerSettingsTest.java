package com.debate.game.controller;

import com.debate.game.dto.RoomSettingsUpdateRequest;
import com.debate.game.exception.GlobalExceptionHandler;
import com.debate.game.exception.NotHostException;
import com.debate.game.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoomControllerSettingsTest {
    RoomService service;
    MockMvc mvc;
    final String json = "{\"playerId\":\"11\",\"cardsPerItem\":4,\"speechSeconds\":150,"
            + "\"rebuttalSeconds\":75,\"itemSeconds\":480,\"maxItems\":5}";

    @BeforeEach
    void setup() {
        service = mock(RoomService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RoomController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void acceptsFiveSettings() throws Exception {
        mvc.perform(put("/api/rooms/ABC123/settings")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNoContent());
        verify(service).updateSettings(eq("ABC123"), eq(11L),
                argThat(s -> s.maxItems() == 5 && s.cardsPerItem() == 4
                        && s.itemSeconds() == 480 && s.speechSeconds() == 150
                        && s.rebuttalSeconds() == 75));
    }

    @Test
    void invalidPlayerIdIsRejected() throws Exception {
        mvc.perform(put("/api/rooms/ABC123/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.replace("\"11\"", "\"oops\"")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_IN_ROOM"));
        verifyNoInteractions(service);
    }

    @Test
    void nonHostReturns403() throws Exception {
        doThrow(new NotHostException()).when(service)
                .updateSettings(eq("ABC123"), eq(11L), any(RoomSettingsUpdateRequest.class));
        mvc.perform(put("/api/rooms/ABC123/settings")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_HOST"));
    }
}
