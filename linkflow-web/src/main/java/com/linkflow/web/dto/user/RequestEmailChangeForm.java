package com.linkflow.web.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestEmailChangeForm {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New email address is required")
    @Email(message = "New email must be a valid email address")
    private String newEmail;
}
