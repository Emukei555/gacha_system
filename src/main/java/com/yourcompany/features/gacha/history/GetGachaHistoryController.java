package com.yourcompany.features.gacha.history;

import com.yourcompany.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gachas/history")
@RequiredArgsConstructor
public class GetGachaHistoryController {

    private final GetGachaHistoryUseCase useCase;

    @GetMapping
    public ResponseEntity<Page<GachaHistoryResponse>> getHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20, sort = "executedAt") Pageable pageable
    ) {
        var result = useCase.execute(userDetails.getUser().getId(), pageable);
        return ResponseEntity.ok(result);
    }
}