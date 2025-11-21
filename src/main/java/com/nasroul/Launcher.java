package com.nasroul;

/**
 * Launcher class pour jpackage.
 * Cette classe ne dépend pas de JavaFX et peut être lancée par l'exécutable natif.
 * Elle lance ensuite l'application JavaFX.
 */
public class Launcher {
    public static void main(String[] args) {
        // Lance l'application JavaFX
        AssociationApp.main(args);
    }
}
