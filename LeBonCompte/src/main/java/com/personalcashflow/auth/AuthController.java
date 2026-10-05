package com.personalcashflow.auth;

import com.personalcashflow.auth.dto.CurrentUserResponse;
import com.personalcashflow.auth.dto.PasswordChangeRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login and logout themselves are handled by Spring Security (see {@link SecurityConfig}). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserService userService;

    public AuthController(AppUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Principal principal) {
        return CurrentUserResponse.from(userService.findByUsername(principal.getName()));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(Principal principal, @Valid @RequestBody PasswordChangeRequest request) {
        userService.changeOwnPassword(principal.getName(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
