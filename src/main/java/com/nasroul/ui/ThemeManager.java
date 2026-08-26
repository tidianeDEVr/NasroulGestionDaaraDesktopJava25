package com.nasroul.ui;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.text.Font;

import java.io.InputStream;

/**
 * Point d'entrée unique du thème de l'application.
 *
 * Charge les polices embarquées (Plus Jakarta Sans + Phosphor) une seule fois,
 * et applique la feuille de style à chaque scène/dialogue. Toute nouvelle
 * scène (fenêtre, dialogue, alerte) DOIT passer par {@link #applyTo(Scene)}
 * ou {@link #applyTo(DialogPane)} — sinon elle s'affiche en Modena brut.
 */
public final class ThemeManager {

    private static final String THEME_CSS = "/css/theme.css";
    private static String themeUrl;
    private static boolean fontsLoaded = false;

    private ThemeManager() {
    }

    private static synchronized void ensureInitialized() {
        if (fontsLoaded) {
            return;
        }
        fontsLoaded = true;

        // Font.loadFont retourne null silencieusement si le chemin est faux :
        // on logge le nom réellement enregistré pour le voir tout de suite.
        // NB : Medium et SemiBold s'enregistrent comme familles distinctes
        // ("Plus Jakarta Sans Medium"...) — le CSS les référence par ces noms.
        loadFont("/fonts/PlusJakartaSans-Regular.ttf");
        loadFont("/fonts/PlusJakartaSans-Medium.ttf");
        loadFont("/fonts/PlusJakartaSans-SemiBold.ttf");
        loadFont("/fonts/PlusJakartaSans-Bold.ttf");
        loadFont("/fonts/Phosphor.ttf");

        java.net.URL url = ThemeManager.class.getResource(THEME_CSS);
        if (url == null) {
            System.err.println("ThemeManager: " + THEME_CSS + " introuvable dans les ressources !");
        } else {
            themeUrl = url.toExternalForm();
        }
    }

    private static void loadFont(String resourcePath) {
        try (InputStream in = ThemeManager.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                System.err.println("ThemeManager: police introuvable " + resourcePath);
                return;
            }
            Font font = Font.loadFont(in, 13);
            if (font == null) {
                System.err.println("ThemeManager: échec de chargement " + resourcePath);
            } else {
                System.out.println("ThemeManager: police chargée \"" + font.getName() + "\"");
            }
        } catch (Exception e) {
            System.err.println("ThemeManager: erreur police " + resourcePath + ": " + e.getMessage());
        }
    }

    /** Applique le thème à une scène (fenêtre principale, dialogue FXML, splash). */
    public static void applyTo(Scene scene) {
        ensureInitialized();
        if (themeUrl != null && !scene.getStylesheets().contains(themeUrl)) {
            scene.getStylesheets().add(themeUrl);
        }
    }

    /** Applique le thème à une Alert/Dialog (le DialogPane a sa propre scène). */
    public static void applyTo(DialogPane pane) {
        ensureInitialized();
        if (themeUrl != null && !pane.getStylesheets().contains(themeUrl)) {
            pane.getStylesheets().add(themeUrl);
        }
    }
}
