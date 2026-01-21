package com.yourcompany.features.gacha.draw;

import com.yourcompany.domain.shared.exception.GachaException;
import com.sqlcanvas.sharedkernel.shared.result.Result;
import com.yourcompany.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/gachas")
@RequiredArgsConstructor
public class DrawGachaController {

    private final DrawGachaUseCase useCase;

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