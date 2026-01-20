package com.yourcompany.web.controller;

import com.sqlcanvas.sharedkernel.shared.result.Result;
import com.yourcompany.domain.shared.exception.GachaException;
import com.yourcompany.schoolasset.application.service.GachaService;
import com.yourcompany.security.CustomUserDetails;
import com.yourcompany.web.dto.GachaDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/gachas")
@RequiredArgsConstructor
public class GachaController {

    private final GachaService gachaService;

    /**
     * ガチャを引く
     * POST /api/v1/gachas/draw
     */
    @PostMapping("/draw")
    public ResponseEntity<GachaDto.DrawResponse> draw(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Validated GachaDto.DrawRequest request
    ) {
        // Service呼び出し
        Result<GachaDto.DrawResponse> result = gachaService.drawGacha(
                userDetails.getUser().getId(), // UserDetailsからID取得
                request
        );

        // Resultハンドリング:
        // 成功なら 200 OK
        if (result instanceof Result.Success<GachaDto.DrawResponse> success) {
            return ResponseEntity.ok(success.value());
        }

        // 失敗なら例外をスロー -> GlobalExceptionHandlerでハンドリング
        // (Service内でロールバック済みだが、HTTPステータス変換のために投げる)
        if (result instanceof Result.Failure<GachaDto.DrawResponse> failure) {
            throw new GachaException(failure.errorCode());
        }

        throw new IllegalStateException("Unknown result type");
    }
}