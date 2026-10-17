package com.sanguosha.assistant.controller;

import com.sanguosha.assistant.vo.AppException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Keeps the LAN browser on its local origin while reading the approved feed. */
@RestController
@RequestMapping("/api/shared-hall")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SharedHallProxyController {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(8))
            .build();

    @Value("${sgs.shared-hall.browse-url:https://sgs.tizonic.com/api/public/shared-packages}")
    private String browseUrl;

    @Value("${sgs.shared-hall.submit-url:https://sgs.tizonic.com/api/public/shared-packages/submissions}")
    private String submitUrl;

    @GetMapping("/packages")
    public ResponseEntity<String> packages(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        try {
            StringBuilder target = new StringBuilder(browseUrl);
            target.append(browseUrl.contains("?") ? '&' : '?');
            if (keyword != null && !keyword.isBlank()) {
                target.append("keyword=").append(URLEncoder.encode(keyword, StandardCharsets.UTF_8)).append('&');
            }
            if (page != null) target.append("page=").append(page).append('&');
            target.append("pageSize=").append(pageSize == null ? 100 : pageSize);
            HttpRequest request = HttpRequest.newBuilder(URI.create(target.toString()))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("共享大厅返回 HTTP " + response.statusCode());
            }
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AppException("读取共享大厅已中断，请重试");
        } catch (IOException | IllegalArgumentException exception) {
            throw new AppException("无法读取共享大厅，请检查网络后重试");
        }
    }

    @PostMapping("/packages")
    public ResponseEntity<String> submit(@RequestBody String payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(submitUrl))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("共享大厅返回 HTTP " + response.statusCode());
            }
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AppException("提交共享大厅已中断，请重试");
        } catch (IOException | IllegalArgumentException exception) {
            throw new AppException("无法提交共享大厅，请检查网络后重试");
        }
    }
}
