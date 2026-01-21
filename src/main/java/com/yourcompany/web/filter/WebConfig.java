package com.yourcompany.web.filter;

import com.sqlcanvas.sharedkernel.shared.filter.SharedRequestLoggingFilter;
import com.yourcompany.security.CustomUserDetails;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<SharedRequestLoggingFilter> loggingFilter() {
        // ライブラリのフィルターをインスタンス化
        // コンストラクタに「AuthenticationからどうやってユーザーIDを取り出すか」のロジックを渡す
        SharedRequestLoggingFilter filter = new SharedRequestLoggingFilter(auth -> {
            if (auth.getPrincipal() instanceof CustomUserDetails userDetails) {
                // アプリ固有のクラスにキャストしてIDを取得
                return userDetails.getUser().getId().toString();
            }
            // フォールバック（未認証時などはユーザー名やanonymous）
            return auth.getName();
        });

        FilterRegistrationBean<SharedRequestLoggingFilter> bean = new FilterRegistrationBean<>(filter);

        // フィルターの実行順序を最優先にする（ログは最初に出したい）
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return bean;
    }
}