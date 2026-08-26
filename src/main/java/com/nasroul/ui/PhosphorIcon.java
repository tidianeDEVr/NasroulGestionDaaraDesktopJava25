package com.nasroul.ui;

import javafx.scene.control.Label;

import java.lang.reflect.Field;

/**
 * Icône Phosphor utilisable en Java ({@code new PhosphorIcon(Phosphor.PLUS, 16)})
 * et en FXML :
 *
 * <pre>{@code <PhosphorIcon icon="USERS_THREE" size="18"/>}</pre>
 *
 * La police "Phosphor" est appliquée par la classe CSS {@code phosphor-icon}
 * (définie dans theme.css) — jamais en dur ici, pour que la cascade reste
 * maîtrisée. La couleur suit {@code -fx-text-fill} du contexte.
 */
public class PhosphorIcon extends Label {

    private String icon;

    public PhosphorIcon() {
        getStyleClass().add("phosphor-icon");
    }

    public PhosphorIcon(String glyph, double size) {
        this();
        setText(glyph);
        setSize(size);
    }

    /** Nom de constante de {@link Phosphor}, pour l'attribut FXML icon="...". */
    public void setIcon(String iconName) {
        this.icon = iconName;
        try {
            Field field = Phosphor.class.getField(iconName);
            setText((String) field.get(null));
        } catch (Exception e) {
            System.err.println("PhosphorIcon: icône inconnue \"" + iconName + "\"");
            setText("?");
        }
    }

    public String getIcon() {
        return icon;
    }

    /** Taille du glyphe en px, pour l'attribut FXML size="...". */
    public void setSize(double size) {
        setStyle("-fx-font-size: " + size + "px;");
    }

    public double getSize() {
        return getFont().getSize();
    }
}
