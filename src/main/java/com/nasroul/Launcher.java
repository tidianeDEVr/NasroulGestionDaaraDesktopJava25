package com.nasroul;

/**
 * Launcher class pour jpackage.
 * Cette classe ne dépend pas de JavaFX directement et peut être lancée par l'exécutable natif.
 * Elle lance ensuite l'application JavaFX de manière robuste.
 *
 * Cette classe séparée est nécessaire car jpackage/exe ne peut pas lancer directement
 * une classe qui étend javafx.application.Application sur Windows.
 */
public class Launcher {
    public static void main(String[] args) {
        try {
            // Lance l'application JavaFX via sa méthode main
            // Cela permet à JavaFX de s'initialiser correctement
            AssociationApp.main(args);
        } catch (Exception e) {
            // En cas d'erreur, afficher dans la console système
            System.err.println("Erreur lors du lancement de l'application:");
            e.printStackTrace();
            // Quitter avec un code d'erreur
            System.exit(1);
        }
    }
}
