//package com.yourcompany.security;
//
//import com.yourcompany.model.user.Role;
//import com.yourcompany.model.user.User;
//import io.jsonwebtoken.Claims;
//import jakarta.servlet.FilterChain;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j; // ログ用に追加
//import org.springframework.lang.NonNull;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.context.SecurityContextHolder;
//import org.springframework.stereotype.Component;
//import org.springframework.util.StringUtils;
//import org.springframework.web.filter.OncePerRequestFilter;
//
//import java.io.IOException;
//
//@Slf4j // ログ用
//@Component
//@RequiredArgsConstructor
//public class JwtAuthenticationFilter extends OncePerRequestFilter {
//
//    private final JwtTokenProvider tokenProvider;
//    // private final UserDetailsService userDetailsService; // 不要になるので削除またはコメントアウト
//
//    @Override
//    protected void doFilterInternal(
//            @NonNull HttpServletRequest request,
//            @NonNull HttpServletResponse response,
//            @NonNull FilterChain filterChain
//    ) throws ServletException, IOException {
//
//        try {
//            String jwt = getJwtFromRequest(request);
//
//            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
//                // 1. トークンから情報を抽出（DBアクセスなし！）
//                Claims claims = tokenProvider.getClaims(jwt);
//                String email = claims.getSubject();
//                Long userId = claims.get("userId", Long.class);
//                String roleName = claims.get("role", String.class);
//
//                // 2. Userオブジェクトを復元
//                User user = new User();
//                user.setId(userId);
//                user.setEmail(email);
//
//                Role role = Role.valueOf(roleName);
//                user.setRole(role);
//                user.setPasswordHash("");
//
//                CustomUserDetails userDetails = new CustomUserDetails(user);
//
//                UsernamePasswordAuthenticationToken authentication =
//                        new UsernamePasswordAuthenticationToken(
//                                userDetails,
//                                null,
//                                userDetails.getAuthorities() // ここで ROLE_CLERK 等が返っているか確認
//                        );
//
//                SecurityContextHolder.getContext().setAuthentication(authentication);
//            }
//        } catch (Exception ex) {
//            log.error("Could not set user authentication in security context", ex);
//        }
//
//        filterChain.doFilter(request, response);
//    }
//
//    private String getJwtFromRequest(HttpServletRequest request) {
//        String bearerToken = request.getHeader("Authorization");
//        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
//            return bearerToken.substring(7);
//        }
//        return null;
//    }
//}