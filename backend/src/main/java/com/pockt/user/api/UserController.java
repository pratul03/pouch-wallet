package com.pockt.user.api;

import com.pockt.infrastructure.exception.UserNotFoundException;
import com.pockt.infrastructure.security.UserPrincipal;
import com.pockt.infrastructure.web.ApiResponse;
import com.pockt.user.dto.FcmTokenRequest;
import com.pockt.user.dto.UpdateUserRequest;
import com.pockt.user.dto.UserResponse;
import com.pockt.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User profile and search endpoints")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
        UserResponse response = userService.getProfile(principal.id());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update current user profile (full name)")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse response = userService.updateProfile(principal.id(), request.fullName());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @org.springframework.web.bind.annotation.PutMapping("/me/pin")
    @Operation(summary = "Change PIN with current PIN verification")
    public ResponseEntity<ApiResponse<Map<String, String>>> changePin(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody com.pockt.user.dto.ChangePinRequest request) {
        userService.changePin(principal.id(), request.oldPin(), request.newPin());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "PIN changed successfully.")));
    }

    @GetMapping("/search")
    @Operation(summary = "Find registered recipient by phone number")
    public ResponseEntity<ApiResponse<UserResponse>> searchByPhone(@RequestParam("phone") String phone) {
        UserResponse response = userService.findByPhone(phone)
                .orElseThrow(() -> new UserNotFoundException("No user found with phone: " + phone));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/me/fcm-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Register FCM device token for push notifications")
    public ResponseEntity<Void> registerFcmToken(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody FcmTokenRequest request) {
        userService.registerFcmToken(principal.id(), request.fcmToken());
        return ResponseEntity.noContent().build();
    }
}
