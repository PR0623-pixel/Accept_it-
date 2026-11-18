
package com.debate.game.controller;

import com.debate.game.exception.GlobalExceptionHandler;
import com.debate.game.exception.RoleFullException;
import com.debate.game.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoomControllerRoleTest {
    MockMvc mvc;
    RoomService roomService;

    @BeforeEach
    void setup() {
        roomService = mock(RoomService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new RoomController(roomService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void guestRoleCanBeSelected() throws Exception {
        mvc.perform(post("/api/rooms/ABC123/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":12,\"role\":\"GUEST\"}"))
                .andExpect(status().isNoContent());

        verify(roomService).selectRole(12L, "GUEST");
    }

    @Test
    void roleCapacityConflictUsesFrontendErrorContract()
            throws Exception {

        doThrow(new RoleFullException())
                .when(roomService)
                .selectRole(12L, "MODERATOR");

        mvc.perform(post("/api/rooms/ABC123/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":12,\"role\":\"MODERATOR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_FULL"))
                .andExpect(jsonPath("$.message")
                        .value("해당 역할 자리가 가득 찼습니다."));
    }

    @Test
    void oldFixedProConRoleCannotBeSelected()
            throws Exception {

        doThrow(new IllegalArgumentException(
                "PRO is not a lobby role"))
                .when(roomService)
                .selectRole(12L, "PRO");

        mvc.perform(post("/api/rooms/ABC123/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":12,\"role\":\"PRO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
