package com.yourcompany.features.gacha.draw;

import com.yourcompany.domain.shared.exception.GachaException;
import com.sqlcanvas.sharedkernel.shared.result.Result;
import com.yourcompany.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Gacha", description = "ガチャ関連API")
@RestController
@RequestMapping("/api/v1/gachas")
@RequiredArgsConstructor
public class DrawGachaController {

    private final DrawGachaUseCase useCase;
    @Operation(summary = "ガチャを引く", description = "指定されたプールIDと回数でガチャを実行します。")
    @PostMapping("/draw")
    public ResponseEntity<DrawGachaResponse> handle(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Validated DrawGachaRequest request
    ) {
        Result<DrawGachaResponse> result = useCase.execute(
                userDetails.getUser().getId(),
                request
        );

        if (result instanceof Result.Success<DrawGachaResponse> success) {
            return ResponseEntity.ok(success.value());
        }

        if (result instanceof Result.Failure<DrawGachaResponse> failure) {
            throw new GachaException(failure.errorCode());
        }

        throw new IllegalStateException("Unknown result type");
    }
}