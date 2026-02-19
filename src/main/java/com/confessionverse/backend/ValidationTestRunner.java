package com.confessionverse.backend;


import com.confessionverse.backend.dto.requestDTO.RegisterRequest;
import jakarta.annotation.PostConstruct;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ValidationTestRunner {

    @PostConstruct
    public void testValidation() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(""); // invalid
        request.setEmail("invalid-email"); // invalid
        request.setPassword("123"); // invalid (sub 8 caractere)

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        if (violations.isEmpty()) {
            System.out.println("✅ No validation errors (should NOT happen here)");
        } else {
            System.out.println("❌ Validation errors:");
            for (ConstraintViolation<RegisterRequest> v : violations) {
                System.out.println(" - " + v.getPropertyPath() + ": " + v.getMessage());
            }
        }
    }
}
