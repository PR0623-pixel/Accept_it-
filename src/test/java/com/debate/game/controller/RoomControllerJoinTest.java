package com.debate.game.controller;

import com.debate.game.dto.RoomEnterResponse;
import com.debate.game.exception.AlreadyStartedException;
import com.debate.game.exception.GlobalExceptionHandler;
import com.debate.game.exception.InvalidRoomCodeException;
import com.debate.game.exception.RoomFullException;
import com.debate.game.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoomControllerJoinTest {
    MockMvc mvc;
    RoomService service;

    @BeforeEach
    void setUp() {
        service = mock(RoomService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RoomController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void frontendReceivesRoomCodeAndStringPlayerId() throws Exception {
        when(service.joinRoom("ABC123", "홍길동"))
                .thenReturn(new RoomEnterResponse("ABC123", "101"));
        mvc.perform(post("/api/rooms/ABC123/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"홍길동\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").value("ABC123"))
                .andExpect(jsonPath("$.playerId").value("101"));
    }

    @Test
    void roomNotFoundMatchesFrontendError() throws Exception {
        when(service.joinRoom("ZZZZZZ", "홍길동"))
                .thenThrow(new InvalidRoomCodeException("방을 찾을 수 없습니다."));
        mvc.perform(post("/api/rooms/ZZZZZZ/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"홍길동\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("방을 찾을 수 없습니다."));
    }

    @Test
    void roomFullMatchesFrontendError() throws Exception {
        when(service.joinRoom("ABC123", "넷째"))
                .thenThrow(new RoomFullException("방이 가득 찼습니다."));
        mvc.perform(post("/api/rooms/ABC123/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"넷째\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_FULL"))
                .andExpect(jsonPath("$.message").value("방이 가득 찼습니다."));
    }

    @Test
    void alreadyStartedMatchesFrontendError() throws Exception {
        when(service.joinRoom("ABC123", "둘째"))
                .thenThrow(new AlreadyStartedException());
        mvc.perform(post("/api/rooms/ABC123/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"둘째\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_STARTED"))
                .andExpect(jsonPath("$.message").value("이미 시작된 방입니다."));
    }
}
