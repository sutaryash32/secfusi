package com.secufusion.iam.controller;

import com.secufusion.iam.dto.AuthDetailsDto;
import com.secufusion.iam.dto.LoginResponseDto;
import com.secufusion.iam.dto.ResponseDto;
import com.secufusion.iam.openFeatureService.service.FeatureFlagService;
import com.secufusion.iam.service.AuthConfigService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping
@Tag(name = "Auth", description = "Authentication configuration endpoints")
public class AuthController {

    @Autowired
    private AuthConfigService authConfigService;

    @Autowired
    private FeatureFlagService featureFlagService;

    @Operation(summary = "User Login", description = "Authenticate user and return access token along with user details.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LoginResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid token",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Bad Request - Missing or invalid parameters",
                    content = @Content)
    })
    @PostMapping("/login")
    public ResponseEntity<ResponseDto<LoginResponseDto>> login(HttpServletRequest request, @RequestParam String token){

        // Mask token info: don't log the token itself, only its length and presence
        String remoteAddr = request.getRemoteAddr();
        int tokenLength = token == null ? 0 : token.length();
        log.info("Login attempt from remoteAddr={} with tokenPresent={} tokenLength={}",
                remoteAddr, token != null && !token.isBlank(), tokenLength > 0 ? tokenLength : 0);

        // Delegate authentication to service
        LoginResponseDto response = authConfigService.login(request, token);

        log.debug("Login processed for remoteAddr={}, resultStatus={}",
                remoteAddr, response != null ? "non-null" : "null");

        return ResponseEntity.ok(new ResponseDto<>(response,String.valueOf(HttpStatus.OK.value())));
    }

}