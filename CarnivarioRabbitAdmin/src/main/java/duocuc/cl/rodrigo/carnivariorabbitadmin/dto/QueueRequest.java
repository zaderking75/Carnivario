package duocuc.cl.rodrigo.carnivariorabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record QueueRequest(
        @NotBlank @Size(max = 255) @Pattern(regexp = NAME_PATTERN) String name,
        @NotNull Boolean durable) {

    public static final String NAME_PATTERN = "[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*";
}
