package com.secufusion.iam.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
public class ResponseDto<T> {
    private T data;
    private String message;
    private String responseCode;

    // All-args constructor (data, message, responseCode)
    public ResponseDto(T data, String message, String responseCode) {
        this.data = data;
        this.message = message;
        this.responseCode = responseCode;
    }

    // Two-arg constructor (data, responseCode) - used by existing controllers
    public ResponseDto(T data, String responseCode) {
        this.data = data;
        this.responseCode = responseCode;
    }

    // Legacy getters for backward compatibility
    public T getResults() {
        return data;
    }

    public void setResults(T results) {
        this.data = results;
    }

    public String getCode() {
        return responseCode;
    }

    public void setCode(String code) {
        this.responseCode = code;
    }
}
