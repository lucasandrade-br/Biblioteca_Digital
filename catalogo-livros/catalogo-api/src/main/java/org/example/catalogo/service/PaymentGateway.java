package org.example.catalogo.service;

import org.example.catalogo.service.dto.LoanContracts.PaymentRequest;
import org.example.catalogo.service.dto.LoanContracts.PaymentResult;

public interface PaymentGateway {

    PaymentResult start(PaymentRequest request);

    String providerName();
}
