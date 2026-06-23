package com.linkflow.user.api.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRolesRequest {

    @NotEmpty(message = "Roles set cannot be empty")
    private Set<String> roles;
}
