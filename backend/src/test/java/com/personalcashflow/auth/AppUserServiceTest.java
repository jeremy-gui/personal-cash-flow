package com.personalcashflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    @Mock
    private AppUserRepository userRepository;

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private AppUserService userService;

    @BeforeEach
    void setUp() {
        userService = new AppUserService(userRepository, passwordEncoder);
    }

    @Test
    void delete_shouldRefuseToDeleteYourself() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(new AppUser("bob", "x", UserRole.USER)));

        assertThatThrownBy(() -> userService.delete(1L, "bob")).isInstanceOf(UserChangeNotAllowedException.class);
    }

    @Test
    void delete_shouldRefuseToDeleteTheLastAdmin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(new AppUser("admin", "x", UserRole.ADMIN)));
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.delete(1L, "bob")).isInstanceOf(UserChangeNotAllowedException.class);
    }

    @Test
    void delete_shouldDeleteAnAdminWhenAnotherOneRemains() {
        AppUser admin = new AppUser("admin2", "x", UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(2L);

        userService.delete(1L, "admin");

        verify(userRepository).delete(admin);
    }

    @Test
    void rename_shouldRefuseAUsernameAlreadyTaken() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(new AppUser("bob", "x", UserRole.USER)));
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> userService.rename(1L, " alice ")).isInstanceOf(UsernameAlreadyUsedException.class);
    }

    @Test
    void rename_shouldTrimAndApplyTheNewUsername() {
        AppUser user = new AppUser("bob", "x", UserRole.USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.rename(1L, " robert ");

        assertThat(user.getUsername()).isEqualTo("robert");
    }

    @Test
    void changeOwnPassword_shouldRequireTheCurrentPassword() {
        AppUser user = new AppUser("bob", passwordEncoder.encode("ancien-mdp"), UserRole.USER);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.changeOwnPassword("bob", "faux", "nouveau-mdp"))
                .isInstanceOf(UserChangeNotAllowedException.class);

        userService.changeOwnPassword("bob", "ancien-mdp", "nouveau-mdp");
        assertThat(passwordEncoder.matches("nouveau-mdp", user.getPasswordHash())).isTrue();
    }
}
