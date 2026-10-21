package duocuc.cl.rodrigo.carnivariorabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BindingRequest(
        @NotBlank @Size(max = 255) @Pattern(regexp = QueueRequest.NAME_PATTERN) String exchange,
        @NotBlank @Size(max = 255) @Pattern(regexp = QueueRequest.NAME_PATTERN) String queue,
        @NotBlank @Size(max = 255)
        @Pattern(regexp = "(?:[A-Za-z0-9_-]+|\\*|#)(?:\\.(?:[A-Za-z0-9_-]+|\\*|#))*") String routingKey) {
}
