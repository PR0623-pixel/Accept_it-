package com.debate.game.controller;

import com.debate.game.exception.GlobalExceptionHandler;
import com.debate.game.exception.InvalidRoomCodeException;
import com.debate.game.exception.AlreadyStartedException;
import com.debate.game.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoomControllerLeaveTest {
    MockMvc mvc;
    RoomService service;

    @BeforeEach
    void setup() {
        service = mock(RoomService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RoomController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void hostLeaveResponds204() throws Exception {
        mvc.perform(post("/api/rooms/ABC123/leave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"1\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).leaveRoom("ABC123", 1L);
    }

    @Test
    void closedRoomIsNotFoundOnSubsequentLeave() throws Exception {
        doThrow(new InvalidRoomCodeException("방을 찾을 수 없습니다."))
                .when(service).leaveRoom("ABC123", 1L);
        mvc.perform(post("/api/rooms/ABC123/leave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void cannotCloseStartedRoom() throws Exception {
        doThrow(new AlreadyStartedException()).when(service).leaveRoom("ABC123", 1L);
        mvc.perform(post("/api/rooms/ABC123/leave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_STARTED"));
    }
}
