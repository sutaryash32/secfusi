package com.secufusion.iam.dto;

import lombok.Data;

/**
 * @author Satyanarayana
 * @param <T>
 */
@Data
public class ResponseDto<T> {
    private T results;
    private String errorMessage;
    private String errorCode;

    public ResponseDto() {}

    public ResponseDto(T results, String errorMessage, String errorCode) {
        super();
        this.results = results;
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
    }

    public ResponseDto(T results, String errorCode) {
        super();
        this.results = results;
        this.errorCode = errorCode;
    }

    public ResponseDto(String errorMessage, String errorCode) {
        super();
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
    }
}
