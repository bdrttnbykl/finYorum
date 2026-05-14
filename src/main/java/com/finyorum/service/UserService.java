package com.finyorum.service;

import com.finyorum.domain.UserAccount;
import com.finyorum.dto.RegisterRequest;
import com.finyorum.dto.UserResponse;
import com.finyorum.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserAccountRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse register(RegisterRequest request) {
        users.findByEmail(request.email()).ifPresent(user -> {
            throw new IllegalArgumentException("Email already registered");
        });

        UserAccount user = users.save(new UserAccount(
                request.email().toLowerCase(),
                passwordEncoder.encode(request.password()),
                request.riskProfile().toUpperCase()
        ));
        return new UserResponse(user.getId(), user.getEmail(), user.getRiskProfile());
    }
}
