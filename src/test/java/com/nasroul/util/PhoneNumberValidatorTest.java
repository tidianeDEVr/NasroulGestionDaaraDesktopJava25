package com.nasroul.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumberValidatorTest {

    @Test
    void acceptsLocalFormats() {
        assertEquals("+221771234567", PhoneNumberValidator.normalize("771234567").orElse(null));
        assertEquals("+221771234567", PhoneNumberValidator.normalize("77 123 45 67").orElse(null));
        assertEquals("+221701234567", PhoneNumberValidator.normalize("70-123-45-67").orElse(null));
    }

    @Test
    void acceptsInternationalFormats() {
        assertEquals("+221771234567", PhoneNumberValidator.normalize("+221771234567").orElse(null));
        assertEquals("+221771234567", PhoneNumberValidator.normalize("00221771234567").orElse(null));
        assertEquals("+221771234567", PhoneNumberValidator.normalize("221771234567").orElse(null));
    }

    @Test
    void acceptsAllSenegaleseMobilePrefixes() {
        for (String prefix : new String[]{"70", "71", "75", "76", "77", "78"}) {
            assertTrue(PhoneNumberValidator.isValid(prefix + "1234567"), prefix);
        }
    }

    @Test
    void rejectsInvalidNumbers() {
        // fixe sénégalais
        assertFalse(PhoneNumberValidator.isValid("338891234"));
        // préfixe mobile inexistant
        assertFalse(PhoneNumberValidator.isValid("791234567"));
        // trop court / trop long — l'ancien code aurait envoyé "+2210770123456"
        assertFalse(PhoneNumberValidator.isValid("0770123456"));
        assertFalse(PhoneNumberValidator.isValid("7712345"));
        // numéro français
        assertFalse(PhoneNumberValidator.isValid("0612345678"));
        assertFalse(PhoneNumberValidator.isValid(""));
        assertFalse(PhoneNumberValidator.isValid(null));
    }
}
