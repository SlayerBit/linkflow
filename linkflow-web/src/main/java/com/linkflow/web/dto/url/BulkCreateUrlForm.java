package com.linkflow.web.dto.url;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BulkCreateUrlForm {

    @NotBlank(message = "Please enter at least one URL")
    private String urlsText;
}
