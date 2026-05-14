package com.finyorum.controller;

import com.finyorum.dto.RegisterRequest;
import com.finyorum.dto.UserResponse;
import com.finyorum.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }
}
