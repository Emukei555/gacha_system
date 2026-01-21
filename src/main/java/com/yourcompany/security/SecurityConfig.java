package com.yourcompany.security;

import com.sqlcanvas.sharedkernel.shared.filter.SharedRequestLoggingFilter; // ★ライブラリのインポート
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // ★ここでライブラリのログフィルターを生成
        // アプリ固有の「ユーザーIDの取り出し方」を定義して渡す
        SharedRequestLoggingFilter sharedLoggingFilter = new SharedRequestLoggingFilter(auth -> {
            if (auth.getPrincipal() instanceof CustomUserDetails userDetails) {
                return userDetails.getUser().getId().toString();
            }
            return auth.getName(); // フォールバック
        });

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // 認証不要なエンドポイント
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // その他は全て認証必須
                        .anyRequest().authenticated()
                )
                // 1. JWT認証フィルターを、標準の認証フィルターの前に置く
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

                // 2. ★修正: ログフィルターを「JWT認証の後」に追加する
                // これにより、SecurityContextにユーザー情報が入った状態でログが出力される
                .addFilterAfter(sharedLoggingFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}