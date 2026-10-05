package com.personalcashflow.auth;

import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the first admin when nobody can log in yet, so a fresh install isn't locked out. */
@Component
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    AdminBootstrap(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
                   @Value("${app.admin.username}") String username,
                   @Value("${app.admin.password}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        String initialPassword = password.isBlank() ? randomPassword() : password;
        userRepository.save(new AppUser(username, passwordEncoder.encode(initialPassword), UserRole.ADMIN));
        if (password.isBlank()) {
            log.warn("Admin créé : identifiant '{}', mot de passe '{}' (à changer après la première connexion)",
                    username, initialPassword);
        } else {
            log.info("Admin créé : identifiant '{}'", username);
        }
    }

    private static String randomPassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
