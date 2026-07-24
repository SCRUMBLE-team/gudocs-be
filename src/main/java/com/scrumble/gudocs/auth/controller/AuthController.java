package com.scrumble.gudocs.auth.controller;

import com.scrumble.gudocs.auth.dto.UserResponse;
import com.scrumble.gudocs.auth.service.AuthService;
import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.global.security.CurrentUserId;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;

    @Override
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest) {
        SecurityContextHolder.clearContext();
        var session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(ApiResponse.success("로그아웃 성공"));
    }

    @Override
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@CurrentUserId Long userId) {
        UserResponse response = authService.getCurrentUser(userId);
        return ResponseEntity.ok(ApiResponse.success("조회 성공", response));
    }
}
