package com.example.userservice.controller;

import com.example.userservice.dto.AuthResponse;
import com.example.userservice.dto.LoginRequest;
import com.example.userservice.dto.RegisterRequest;
import com.example.userservice.exception.BusinessException;
import com.example.userservice.model.User;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/users")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // POST /users/register
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Un compte existe déjà avec cet email");
        }

        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("MEMBER");
        user.setActive(true);

        User savedUser = userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
    }

    // POST /users/login
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        Optional<User> user = userRepository.findByEmail(loginRequest.getEmail().trim().toLowerCase());

        if (user.isPresent()) {
            User existingUser = user.get();

            if (!existingUser.isActive()) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "Compte désactivé");
            }

            if (passwordMatches(loginRequest.getPassword(), existingUser)) {
                String token = jwtService.generateToken(existingUser);
                return ResponseEntity.ok(new AuthResponse(token, existingUser));
            }
        }

        throw new BusinessException(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect");
    }

    // GET /users/profile
    @GetMapping("/profile")
    public ResponseEntity<?> profile(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Token manquant");
        }

        try {
            String token = authorizationHeader.substring(7);
            Long userId = jwtService.extractUserId(token);

            return userRepository.findById(userId)
                    .filter(User::isActive)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body("Utilisateur introuvable"));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Token invalide ou expiré");
        }
    }

    // GET /users/{id}
    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        Optional<User> user = userRepository.findById(id);

        if (user.isPresent() && user.get().isActive()) {
            return ResponseEntity.ok(user.get());
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Utilisateur introuvable");
    }

    private boolean passwordMatches(String rawPassword, User user) {
        String storedPassword = user.getPassword();

        if (storedPassword != null && storedPassword.startsWith("$2")) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }

        boolean legacyPasswordMatches = storedPassword != null && storedPassword.equals(rawPassword);
        if (legacyPasswordMatches) {
            user.setPassword(passwordEncoder.encode(rawPassword));
            userRepository.save(user);
        }

        return legacyPasswordMatches;
    }
}
