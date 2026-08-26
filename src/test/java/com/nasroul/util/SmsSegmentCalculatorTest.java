package com.nasroul.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmsSegmentCalculatorTest {

    @Test
    void emptyMessageCostsNothing() {
        assertEquals(0, SmsSegmentCalculator.countSegments(""));
        assertEquals(0, SmsSegmentCalculator.countSegments(null));
    }

    @Test
    void gsm7Boundaries() {
        assertEquals(1, SmsSegmentCalculator.countSegments("a".repeat(160)));
        assertEquals(2, SmsSegmentCalculator.countSegments("a".repeat(161)));
        assertEquals(2, SmsSegmentCalculator.countSegments("a".repeat(306)));
        assertEquals(3, SmsSegmentCalculator.countSegments("a".repeat(307)));
    }

    @Test
    void frenchAccentsInGsm7StayCheap() {
        // é è à ù sont dans l'alphabet GSM 7 bits...
        String msg = "Assalamou aleykoum, il vous reste à payer 5000 CFA. Merci d'avancer réglé.";
        assertTrue(SmsSegmentCalculator.isGsm7(msg));
        assertEquals(1, SmsSegmentCalculator.countSegments(msg));

        // ...mais pas ê (ni ô, î, ï) : un seul suffit à faire basculer en UCS-2
        assertFalse(SmsSegmentCalculator.isGsm7("Merci d'être à jour."));
    }

    @Test
    void extensionCharactersCountDouble() {
        // 80 × '€' = 160 caractères effectifs → 1 segment ; 81 → 2
        assertEquals(1, SmsSegmentCalculator.countSegments("€".repeat(80)));
        assertEquals(2, SmsSegmentCalculator.countSegments("€".repeat(81)));
    }

    @Test
    void nonGsmCharacterSwitchesToUcs2() {
        // 'ô' n'est pas dans l'alphabet GSM : tout le message passe en UCS-2
        String msg = "Cotisation pour l'hôpital: " + "a".repeat(50);
        assertFalse(SmsSegmentCalculator.isGsm7(msg));
        assertEquals(2, SmsSegmentCalculator.countSegments(msg)); // 77 caractères > 70
    }

    @Test
    void ucs2Boundaries() {
        assertEquals(1, SmsSegmentCalculator.countSegments("ô".repeat(70)));
        assertEquals(2, SmsSegmentCalculator.countSegments("ô".repeat(71)));
    }
}
