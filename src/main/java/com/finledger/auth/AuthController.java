package com.finledger.auth;

import com.finledger.common.BadRequestException;
import com.finledger.common.ConflictException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AppUserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(AppUserRepository userRepo,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6) String password,
            String role
    ) {}

    record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> register(@Valid @RequestBody RegisterRequest req) {
        if (userRepo.existsByEmail(req.email())) {
            throw new ConflictException("Email already registered");
        }

        AppRole role = AppRole.USER;
        if (req.role() != null && req.role().equalsIgnoreCase("ADMIN")) {
            role = AppRole.ADMIN;
        }

        String hash = passwordEncoder.encode(req.password());
        AppUser user = new AppUser(req.email(), hash, role);
        userRepo.save(user);

        String token = jwtService.generateToken(user.getEmail(), user.getRole());
        return Map.of(
                "token", token,
                "email", user.getEmail(),
                "role", user.getRole().name()
        );
    }

    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody LoginRequest req) {
        AppUser user = userRepo.findByEmail(req.email())
                .orElseThrow(() -> new BadRequestException("Invalid email or password"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadRequestException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getEmail(), user.getRole());
        return Map.of(
                "token", token,
                "email", user.getEmail(),
                "role", user.getRole().name()
        );
    }
}
