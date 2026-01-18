package com.yourcompany.domain.shared.exception;

import lombok.Getter;

@Getter
public class GachaException extends RuntimeException {
    private final GachaErrorCode errorCode;

    public GachaException(GachaErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }
}