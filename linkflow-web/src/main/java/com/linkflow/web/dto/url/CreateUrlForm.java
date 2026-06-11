package com.linkflow.web.dto.url;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
public class CreateUrlForm {

    @NotBlank(message = "Original URL is required")
    @Size(max = 2048, message = "Original URL must not exceed 2048 characters")
    private String originalUrl;

    @Size(max = 100, message = "Custom alias must not exceed 100 characters")
    @Pattern(
            regexp = "^$|^[a-zA-Z0-9_-]+$",
            message = "Custom alias may only contain alphanumeric characters, hyphens, and underscores"
    )
    private String customAlias;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime expiresAt;
}
