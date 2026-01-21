package com.yourcompany.web.filter;

import com.yourcompany.domain.shared.value.RequestId;
import com.yourcompany.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String KEY_REQUEST_ID = "requestId";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_URI = "uri";
    private static final String KEY_METHOD = "method";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // 1. リクエストIDの発行 (HTTPヘッダーにあればそれを使い、なければ生成)
            // ここでは簡易的に毎回生成するか、クライアントからのX-Request-IDを優先するロジックを入れる
            RequestId requestId = RequestId.generate();
            MDC.put(KEY_REQUEST_ID, requestId.toString());

            // 2. URIとメソッド
            MDC.put(KEY_URI, request.getRequestURI());
            MDC.put(KEY_METHOD, request.getMethod());

            // 3. ユーザー情報の取得 (SecurityContextから)
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
                MDC.put(KEY_USER_ID, userDetails.getUser().getId().toString());
            } else {
                MDC.put(KEY_USER_ID, "anonymous");
            }

            // 実行
            filterChain.doFilter(request, response);

        } finally {
            // 重要: スレッドプールで再利用されるため、必ずクリアする
            MDC.clear();
        }
    }
}