package com.debate.game.controller;

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

class RoomControllerNicknameTest {
    private MockMvc mvc;
    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = mock(RoomService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RoomController(roomService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void acceptsFrontendJsonNickname() throws Exception {
        when(roomService.joinRoom("ABC123", "홍길동")).thenReturn(42L);
        mvc.perform(post("/api/rooms/ABC123/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"홍길동\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("42"));
        verify(roomService).joinRoom("ABC123", "홍길동");
    }

    @Test
    void supportsPreviousQueryParameterForExistingClients() throws Exception {
        when(roomService.joinRoom("ABC123", "민수")).thenReturn(5L);
        mvc.perform(post("/api/rooms/ABC123/join").param("playerName", "민수"))
                .andExpect(status().isOk())
                .andExpect(content().string("5"));
    }

    @Test
    void returnsNicknameErrorAsCommonJson() throws Exception {
        when(roomService.joinRoom("ABC123", "   "))
                .thenThrow(new InvalidNicknameException("닉네임을 입력하세요."));
        mvc.perform(post("/api/rooms/ABC123/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_NICKNAME"))
                .andExpect(jsonPath("$.errorMessage").value("닉네임을 입력하세요."));
    }
}
