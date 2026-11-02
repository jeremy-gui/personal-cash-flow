package com.personalcashflow.auth;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The user store is mocked so these checks never write to the database. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AppUserService userService;

    @Test
    void api_shouldAnswer401WhenNotLoggedIn() throws Exception {
        mockMvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    void login_shouldAnswer200WithTheRightPassword() throws Exception {
        givenUser("alice", "bon-mot-de-passe");

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", "alice").param("password", "bon-mot-de-passe"))
                .andExpect(status().isOk());
    }

    @Test
    void login_shouldAnswer401WithAWrongPassword() throws Exception {
        givenUser("alice", "bon-mot-de-passe");

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", "alice").param("password", "mauvais"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void writes_shouldBeRejectedWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void users_shouldBeForbiddenForNonAdmins() throws Exception {
        mockMvc.perform(get("/api/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void users_shouldBeAllowedForAdmins() throws Exception {
        when(userService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/users")).andExpect(status().isOk());
    }

    private void givenUser(String username, String password) {
        when(userService.loadUserByUsername(username)).thenReturn(User.withUsername(username)
                .password(passwordEncoder.encode(password)).roles("USER").build());
    }
}
