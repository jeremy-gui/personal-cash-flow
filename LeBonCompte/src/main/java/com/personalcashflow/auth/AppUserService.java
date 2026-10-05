package com.personalcashflow.auth;

import com.personalcashflow.auth.dto.UserRequest;
import java.util.List;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AppUserService implements UserDetailsService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AppUserService(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        AppUser user = findByUsername(username);
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .build();
    }

    @Transactional(readOnly = true)
    public List<AppUser> findAll() {
        return userRepository.findAllByOrderByUsernameAsc();
    }

    @Transactional(readOnly = true)
    public AppUser findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    public AppUser create(UserRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyUsedException(username);
        }
        return userRepository.save(new AppUser(username, passwordEncoder.encode(request.password()), request.role()));
    }

    public AppUser rename(Long id, String newUsername) {
        AppUser user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        String username = newUsername.trim();
        if (!username.equals(user.getUsername()) && userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyUsedException(username);
        }
        user.rename(username);
        return user;
    }

    public void resetPassword(Long id, String newPassword) {
        AppUser user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        user.changePasswordHash(passwordEncoder.encode(newPassword));
    }

    public void changeOwnPassword(String username, String currentPassword, String newPassword) {
        AppUser user = findByUsername(username);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new UserChangeNotAllowedException("Mot de passe actuel incorrect");
        }
        user.changePasswordHash(passwordEncoder.encode(newPassword));
    }

    public void delete(Long id, String currentUsername) {
        AppUser user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        if (user.getUsername().equals(currentUsername)) {
            throw new UserChangeNotAllowedException("Vous ne pouvez pas supprimer votre propre compte");
        }
        if (user.getRole() == UserRole.ADMIN && userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new UserChangeNotAllowedException("Impossible de supprimer le dernier administrateur");
        }
        userRepository.delete(user);
    }
}
