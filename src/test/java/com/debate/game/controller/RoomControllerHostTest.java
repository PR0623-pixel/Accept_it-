package com.debate.game.controller;

import com.debate.game.dto.RoomEnterResponse;
import com.debate.game.exception.GlobalExceptionHandler;
import com.debate.game.exception.InvalidNicknameException;
import com.debate.game.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoomControllerHostTest {
    private MockMvc mvc;
    private RoomService service;

    @BeforeEach
    void setUp() {
        service = mock(RoomService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RoomController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void frontendNicknameOnlyRequestReturnsRoomCodeAndStringPlayerId() throws Exception {
        when(service.createRoom("홍길동", null, null))
                .thenReturn(new RoomEnterResponse("ABC123", "15"));

        mvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"홍길동\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").value("ABC123"))
                .andExpect(jsonPath("$.playerId").value("15"));
        verify(service).createRoom("홍길동", null, null);
    }

    @Test
    void badHostNicknameReturnsR01Error() throws Exception {
        when(service.createRoom("  ", null, null))
                .thenThrow(new InvalidNicknameException("닉네임을 입력하세요."));

        mvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_NICKNAME"));
    }
}
