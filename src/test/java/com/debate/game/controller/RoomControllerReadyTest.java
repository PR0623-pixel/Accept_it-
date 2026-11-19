package com.debate.game.controller;

import com.debate.game.exception.AlreadyStartedException;
import com.debate.game.exception.GlobalExceptionHandler;
import com.debate.game.exception.PlayerNotInRoomException;
import com.debate.game.exception.RoleRequiredException;
import com.debate.game.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoomControllerReadyTest {
    MockMvc mvc;
    RoomService roomService;

    @BeforeEach
    void setup() {
        roomService = mock(RoomService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RoomController(roomService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String body) {
        return post("/api/rooms/AB12CD/ready")
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void readyTrueReturns204AndCallsService() throws Exception {
        mvc.perform(request("{\"playerId\":\"12\",\"ready\":true}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(roomService).setReady("AB12CD", 12L, true);
    }

    @Test
    void readyFalseReturns204AndCallsService() throws Exception {
        mvc.perform(request("{\"playerId\":\"12\",\"ready\":false}"))
                .andExpect(status().isNoContent());
        verify(roomService).setReady("AB12CD", 12L, false);
    }

    @Test
    void omittedReadyIsNotInterpretedAsFalse() throws Exception {
        mvc.perform(request("{\"playerId\":\"12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verifyNoInteractions(roomService);
    }

    @Test
    void missingOrInvalidPlayerIs404() throws Exception {
        mvc.perform(request("{\"playerId\":\"abc\",\"ready\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_IN_ROOM"));
        verifyNoInteractions(roomService);
    }

    @Test
    void roleRequiredMatchesFrontendErrorContract() throws Exception {
        doThrow(new RoleRequiredException()).when(roomService).setReady("AB12CD", 12L, true);
        mvc.perform(request("{\"playerId\":\"12\",\"ready\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_REQUIRED"));
    }

    @Test
    void alreadyStartedIsRejected() throws Exception {
        doThrow(new AlreadyStartedException()).when(roomService).setReady("AB12CD", 12L, false);
        mvc.perform(request("{\"playerId\":\"12\",\"ready\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_STARTED"));
    }
}
