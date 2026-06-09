package com.template.OAuth.controller;

import com.template.OAuth.annotation.Auditable;
import com.template.OAuth.dto.RefreshTokenResponse;
import com.template.OAuth.enums.AuditEventType;
import com.template.OAuth.exception.ApiException;
import com.template.OAuth.service.MessageService;
import com.template.OAuth.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/refresh-token")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class RefreshTokenController {

    private final RefreshTokenService refreshTokenService;
    private final MessageService messageService;

    @Autowired
    public RefreshTokenController(RefreshTokenService refreshTokenService, MessageService messageService) {
        this.refreshTokenService = refreshTokenService;
        this.messageService = messageService;
    }

    @PostMapping
    @Auditable(type = AuditEventType.TOKEN_REFRESH, description = "User refreshed token")
    public ResponseEntity<RefreshTokenResponse> refreshAccessToken(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        String refreshToken = null;

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refresh_token".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }

        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new RefreshTokenResponse(null, null, messageService.getMessage("auth.token.invalid")));
        }

        try {
            RefreshTokenResponse refreshResponse = refreshTokenService.refreshToken(refreshToken, response);
            // Update the message to use internationalization
            refreshResponse.setMessage(messageService.getMessage("auth.login.success"));
            return ResponseEntity.ok(refreshResponse);
        } catch (ApiException e) {
            return ResponseEntity.status(e.getStatus())
                    .body(new RefreshTokenResponse(null, null, messageService.getMessage(e.getMessageKey())));
        }
    }
}