package com.debate.game.controller;

import com.debate.game.exception.*;
import com.debate.game.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoomControllerStartCheckTest {
    RoomService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(RoomService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RoomController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String playerId) {
        return post("/api/rooms/ABC123/start/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerId\":\"" + playerId + "\"}");
    }

    @Test
    void validHostRequestReturns204Only() throws Exception {
        mvc.perform(request("53"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).validateStartConditions("ABC123", 53L);
        verifyNoMoreInteractions(service);
    }

    @Test
    void notHostReturns403() throws Exception {
        doThrow(new NotHostException()).when(service).validateStartConditions("ABC123", 53L);
        mvc.perform(request("53"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_HOST"));
    }

    @Test
    void lessThanThreeReturns409() throws Exception {
        doThrow(new NotEnoughPlayersException()).when(service).validateStartConditions("ABC123", 53L);
        mvc.perform(request("53"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_PLAYERS"));
    }

    @Test
    void wrongRolesReturn409() throws Exception {
        doThrow(new InvalidStartConditionsException("INVALID_ROLE_COMPOSITION", "wrong role"))
                .when(service).validateStartConditions("ABC123", 53L);
        mvc.perform(request("53"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ROLE_COMPOSITION"));
    }

    @Test
    void notAllReadyReturns409() throws Exception {
        doThrow(new InvalidStartConditionsException("NOT_ALL_READY", "not ready"))
                .when(service).validateStartConditions("ABC123", 53L);
        mvc.perform(request("53"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ALL_READY"));
    }

    @Test
    void otherRoomPlayerReturns404() throws Exception {
        doThrow(new PlayerNotInRoomException()).when(service).validateStartConditions("ABC123", 53L);
        mvc.perform(request("53"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_IN_ROOM"));
    }

    @Test
    void badIdReturns404WithoutServiceCall() throws Exception {
        mvc.perform(request("no-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_IN_ROOM"));
        verifyNoInteractions(service);
    }

    @Test
    void alreadyStartedReturns409() throws Exception {
        doThrow(new AlreadyStartedException()).when(service).validateStartConditions("ABC123", 53L);
        mvc.perform(request("53"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_STARTED"));
    }
}
