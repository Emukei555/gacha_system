package com.yourcompany.domain.model.wallet;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CoinType {
    // 優先順位: PAID(1) > FREE(2) の順で消費されるわけではないが、管理上区別する
    PAID("有償石"),
    FREE("無償石");

    private final String label;
}
