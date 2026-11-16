package org.example.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PickupRequest(
    @NotBlank(message = "Informe o código de retirada")
    @Size(max = 12, message = "Código de retirada inválido")
    String pickupCode
) {
}
