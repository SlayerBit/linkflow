package com.linkflow.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EmailChangeVerifyDto {

    @NotBlank(message = "Token is required")
    private String token;
}
