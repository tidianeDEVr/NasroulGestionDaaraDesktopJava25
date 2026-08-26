package com.nasroul.util;

import java.util.Optional;

/**
 * Normalizes and validates Senegalese mobile numbers.
 *
 * Accepted inputs (spaces, dashes, dots and parentheses are ignored):
 * "77 123 45 67", "+221771234567", "00221771234567", "221771234567".
 * A valid number is a 9-digit mobile starting with 70/71/75/76/77/78.
 * Anything else is rejected explicitly instead of being sent to the provider
 * with a blindly prepended +221.
 */
public final class PhoneNumberValidator {

    private PhoneNumberValidator() {
    }

    /**
     * @return the number in international format (+221771234567),
     *         or empty if the input is not a valid Senegalese mobile number
     */
    public static Optional<String> normalize(String phoneNumber) {
        if (phoneNumber == null) {
            return Optional.empty();
        }

        String cleaned = phoneNumber.replaceAll("[\\s\\-().]", "");
        if (cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.startsWith("00221")) {
            cleaned = cleaned.substring(5);
        } else if (cleaned.startsWith("221")) {
            cleaned = cleaned.substring(3);
        }

        // Préfixes mobiles sénégalais : 70, 71, 75, 76, 77, 78
        if (!cleaned.matches("7[015678]\\d{7}")) {
            return Optional.empty();
        }
        return Optional.of("+221" + cleaned);
    }

    public static boolean isValid(String phoneNumber) {
        return normalize(phoneNumber).isPresent();
    }
}
