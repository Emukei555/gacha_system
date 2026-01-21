package com.yourcompany.features.auth;

import com.sqlcanvas.sharedkernel.shared.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthUseCase authUseCase;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        Result<AuthResponse> result = authUseCase.login(request);
        return handleResult(result);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
        Result<AuthResponse> result = authUseCase.register(request);
        return handleResult(result);
    }

    // 簡易ハンドリングヘルパー
    private ResponseEntity<?> handleResult(Result<AuthResponse> result) {
        if (result instanceof Result.Success<AuthResponse> s) {
            return ResponseEntity.ok(s.value());
        } else if (result instanceof Result.Failure<AuthResponse> f) {
            return ResponseEntity.status(f.errorCode().getStatus()).body(f.message());
        }
        return ResponseEntity.internalServerError().build();
    }
}