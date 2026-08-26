package com.nasroul.ui;

import javafx.scene.control.TextInputControl;

/**
 * Lecture et écriture sûres des champs de saisie.
 *
 * Piège JavaFX à l'origine de plantages : {@code setText(null)} est accepté,
 * mais {@code getText()} renvoie alors {@code null} (et non une chaîne vide) —
 * tout {@code getText().trim()} qui suit lève une NullPointerException. Les
 * entités ayant des champs facultatifs nuls (description, email, lieu…),
 * il faut passer par ces helpers des deux côtés.
 */
public final class Forms {

    private Forms() {
    }

    /** Contenu du champ, jamais null, sans espaces superflus. */
    public static String text(TextInputControl control) {
        if (control == null || control.getText() == null) {
            return "";
        }
        return control.getText().trim();
    }

    /** Contenu du champ, ou null si vide (pour les colonnes facultatives). */
    public static String textOrNull(TextInputControl control) {
        String value = text(control);
        return value.isEmpty() ? null : value;
    }

    /** Contenu brut du champ, jamais null (espaces conservés : compteurs, aperçus). */
    public static String raw(TextInputControl control) {
        if (control == null || control.getText() == null) {
            return "";
        }
        return control.getText();
    }

    /** Renseigne un champ sans jamais y écrire null. */
    public static void setText(TextInputControl control, String value) {
        if (control != null) {
            control.setText(value != null ? value : "");
        }
    }

    /** Renseigne un champ à partir d'une valeur quelconque (nombre, date…). */
    public static void setText(TextInputControl control, Object value) {
        setText(control, value != null ? String.valueOf(value) : "");
    }
}
