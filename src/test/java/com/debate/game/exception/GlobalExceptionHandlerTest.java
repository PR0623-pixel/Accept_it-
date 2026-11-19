package com.debate.game.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void domainErrorsHaveSafeCompatibleBodies() throws Exception {
        check("room", 404, "ROOM_NOT_FOUND");
        check("started", 409, "ALREADY_STARTED");
        check("full", 409, "ROOM_FULL");
        check("role", 409, "ROLE_FULL");
        check("role-required", 409, "ROLE_REQUIRED");
        check("players-required", 409, "NOT_ENOUGH_PLAYERS");
        check("config", 503, "AI_NOT_CONFIGURED");
        check("ai", 502, "AI_SERVICE_ERROR");
        check("argument", 400, "BAD_REQUEST");
        check("state", 409, "CONFLICT");
        check("unexpected", 500, "INTERNAL_SERVER_ERROR");
    }

    private void check(String type, int status, String code) throws Exception {
        mvc.perform(get("/errors/" + type)).andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive-detail"))));
    }

    @Test
    void frameworkErrorsUseSameContract() throws Exception {
        mvc.perform(get("/missing")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(post("/errors/room")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post("/body").contentType(MediaType.TEXT_PLAIN).content("text"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(get("/number").param("id", "bad"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/number"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @RestController
    static class TestController {
        @GetMapping("/errors/{type}")
        String error(@PathVariable String type) {
            throw switch (type) {
                case "room" -> new InvalidRoomCodeException("sensitive-detail");
                case "full" -> new RoomFullException("sensitive-detail");
                case "role" -> new RoleFullException();
                case "role-required" -> new RoleRequiredException();
                case "players-required" -> new NotEnoughPlayersException();
                case "started" -> new AlreadyStartedException();
                case "config" -> new AiConfigurationException();
                case "ai" -> new AiServiceException("sensitive-detail");
                case "argument" -> new IllegalArgumentException("sensitive-detail");
                case "state" -> new IllegalStateException("sensitive-detail");
                default -> new RuntimeException("sensitive-detail SQL password");
            };
        }
        @PostMapping(value = "/body", consumes = "application/json")
        void body(@RequestBody java.util.Map<String, Object> body) {}
        @GetMapping("/number")
        void number(@RequestParam Long id) {}
    }
}
