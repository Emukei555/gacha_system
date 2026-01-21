package com.yourcompany.features.auth;

import com.sqlcanvas.sharedkernel.shared.result.Result;
import com.yourcompany.domain.model.user.User;
import com.yourcompany.domain.model.user.UserRepository;
import com.yourcompany.domain.model.wallet.Wallet;
import com.yourcompany.schoolasset.infrastructure.persistence.repository.WalletRepository;
import com.yourcompany.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthUseCase {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository; // 登録時にウォレットも作る
    private final PasswordEncoder passwordEncoder;

    // ログイン処理
    public Result<AuthResponse> login(AuthRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
            String jwt = tokenProvider.generateToken(authentication);
            return Result.success(new AuthResponse(jwt));
        } catch (AuthenticationException e) {
            // 認証失敗は401へ (本来はErrorCode定義推奨だが簡易的に)
            return Result.failure(new com.sqlcanvas.sharedkernel.shared.error.ErrorCode() {
                @Override public String getCode() { return "AUTH-001"; }
                @Override public String getDefaultMessage() { return "Invalid email or password"; }
                @Override public org.springframework.http.HttpStatus getStatus() { return org.springframework.http.HttpStatus.UNAUTHORIZED; }
            });
        }
    }

    // 登録処理 (本来はRegisterUseCaseとして分けるのも可)
    @Transactional
    public Result<AuthResponse> register(AuthRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            return Result.failure(new com.sqlcanvas.sharedkernel.shared.error.ErrorCode() {
                @Override public String getCode() { return "AUTH-002"; }
                @Override public String getDefaultMessage() { return "Email already exists"; }
                @Override public org.springframework.http.HttpStatus getStatus() { return org.springframework.http.HttpStatus.CONFLICT; }
            });
        }

        // 1. User作成
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(User.Role.USER);
        userRepository.save(user);

        // 2. Wallet作成 (初期残高0)
        Wallet wallet = Wallet.create(user.getId());
        walletRepository.save(wallet);

        // 3. ログインしてトークン発行
        return login(request);
    }
}