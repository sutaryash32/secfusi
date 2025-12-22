package com.secufusion.iam.dto;

import lombok.Data;

@Data
public class ResponseDto<T> {
    private T results;
    private String message;
    private String code;

    public ResponseDto() {}


    public ResponseDto(T results, String message, String code) {
        super();
        this.results = results;
        this.message = message;
        this.code = code;
    }

    public ResponseDto(T results, String code) {
        super();
        this.results = results;
        this.code = code;
    }

    public ResponseDto(String message, String code) {
        super();
        this.message = message;
        this.code = code;
    }

}
