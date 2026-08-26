package com.nasroul.util;

/**
 * Computes how many SMS segments (credits) a message really costs.
 *
 * A message whose characters all belong to the GSM 03.38 7-bit alphabet fits
 * 160 characters in one segment (153 per segment when concatenated); extension
 * characters (€, [, ], {, }, ~, ^, \, |) count double. A single character
 * outside that alphabet (ô, î, ï, emoji…) switches the whole message to UCS-2:
 * 70 characters per segment (67 when concatenated).
 */
public final class SmsSegmentCalculator {

    private static final String GSM_BASIC =
            "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?"
            + "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";

    private static final String GSM_EXTENSION = "^{}\\[~]|€";

    private SmsSegmentCalculator() {
    }

    /** true if the message can be encoded in the GSM 7-bit alphabet. */
    public static boolean isGsm7(String message) {
        if (message == null) {
            return true;
        }
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (GSM_BASIC.indexOf(c) < 0 && GSM_EXTENSION.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Effective length of the message in its encoding: GSM extension
     * characters count double, UCS-2 characters count once each.
     */
    public static int effectiveLength(String message) {
        if (message == null || message.isEmpty()) {
            return 0;
        }
        if (!isGsm7(message)) {
            return message.length();
        }
        int length = 0;
        for (int i = 0; i < message.length(); i++) {
            length += GSM_EXTENSION.indexOf(message.charAt(i)) >= 0 ? 2 : 1;
        }
        return length;
    }

    /** Number of segments (credits) this message costs. */
    public static int countSegments(String message) {
        int length = effectiveLength(message);
        if (length == 0) {
            return 0;
        }
        if (isGsm7(message)) {
            return length <= 160 ? 1 : (int) Math.ceil(length / 153.0);
        }
        return length <= 70 ? 1 : (int) Math.ceil(length / 67.0);
    }
}
