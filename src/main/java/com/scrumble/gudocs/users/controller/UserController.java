package com.scrumble.gudocs.users.controller;

import com.scrumble.gudocs.global.response.ApiResponse;
import com.scrumble.gudocs.global.security.CurrentUserId;
import com.scrumble.gudocs.users.dto.UserInfoResponse;
import com.scrumble.gudocs.users.dto.UserNameUpdateRequest;
import com.scrumble.gudocs.users.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController implements UserApi {

    private final UserService userService;

    @Override
    @PutMapping("/me/name")
    public ResponseEntity<ApiResponse<UserInfoResponse>> updateName(
            @CurrentUserId Long userId,
            @Valid @RequestBody UserNameUpdateRequest request) {
        UserInfoResponse response = userService.updateName(userId, request);
        return ResponseEntity.ok(ApiResponse.success("이름이 수정되었습니다.", response));
    }

    @Override
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @CurrentUserId Long userId,
            HttpServletRequest httpRequest) {
        userService.deleteAccount(userId);
        var session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(ApiResponse.success("회원 탈퇴가 완료되었습니다."));
    }
}
