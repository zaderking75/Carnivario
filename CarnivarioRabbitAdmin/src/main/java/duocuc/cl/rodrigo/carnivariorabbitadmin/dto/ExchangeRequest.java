package duocuc.cl.rodrigo.carnivariorabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ExchangeRequest(
        @NotBlank @Size(max = 255) @Pattern(regexp = QueueRequest.NAME_PATTERN) String name,
        @NotBlank @Pattern(regexp = "direct|topic|fanout") String type,
        @NotNull Boolean durable) {
}
