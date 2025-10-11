package com.aisip.OnO.backend.common.exception;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.util.webhook.DiscordWebhookPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.List;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${discord.webhook-url}")
    private String discordWebhookUrl;

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<CommonResponse> handleApplicationException(ApplicationException e, WebRequest request) {
        CommonResponse commonResponse = CommonResponse.error(e.getErrorCase());

        HttpStatus status = HttpStatus.valueOf(e.getErrorCase().getHttpStatusCode());
        sendToDiscord(e, request, status);

        return ResponseEntity
                .status(e.getErrorCase().getHttpStatusCode())
                .body(commonResponse);
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse> handleValidException(BindingResult bindingResult,
                                                               MethodArgumentNotValidException ex,
                                                               WebRequest request) {
        String message = bindingResult.getAllErrors().get(0).getDefaultMessage();
        CommonResponse commonResponse = CommonResponse.error(400, message);

        sendToDiscord(ex, request, HttpStatus.BAD_REQUEST);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(commonResponse);
    }

    private void sendToDiscord(Exception ex, WebRequest request, HttpStatus status) {
        String path      = ((ServletWebRequest) request).getRequest().getRequestURI();
        String timestamp = Instant.now().toString();

        // Embed 필드 구성
        DiscordWebhookPayload.Embed embed = new DiscordWebhookPayload.Embed(
                "🚨 서버 에러 발생",
                "```" + ex.getMessage() + "```",
                timestamp,
                List.of(
                        new DiscordWebhookPayload.Embed.Field("URL", path, false),
                        new DiscordWebhookPayload.Embed.Field("Status", status.toString(), true),
                        new DiscordWebhookPayload.Embed.Field("Time", timestamp, true),
                        new DiscordWebhookPayload.Embed.Field("Exception", ex.getClass().getSimpleName(), true)
                )
        );
        DiscordWebhookPayload payload = new DiscordWebhookPayload(null, List.of(embed));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try{
            restTemplate.postForEntity(
                    discordWebhookUrl,
                    new HttpEntity<>(payload, headers),
                    String.class
            );
            log.info("send to Discord webhook: {} complete", payload);
        } catch (Exception e){
            log.error(e.getMessage());
        }

    }
}

