package com.debate.game.controller;

import com.debate.game.exception.GlobalExceptionHandler;
import com.debate.game.service.GameStartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GameControllerLegacyStartTest {
    MockMvc mvc;
    GameStartService service;

    @BeforeEach
    void setup() {
        service = mock(GameStartService.class);
        mvc = MockMvcBuilders.standaloneSetup(new GameController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void legacyUnverifiedStartCannotBypassR16() throws Exception {
        mvc.perform(post("/api/game/10/start"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GAME_START_NOT_IMPLEMENTED"));
        verifyNoInteractions(service);
    }
}
