package com.nasroul.ui;

/**
 * Contrat des contrôleurs de section mis en cache par MainController :
 * la vue n'est chargée qu'une fois, {@link #onShown()} est appelé à chaque
 * retour sur la section pour rafraîchir les données.
 */
public interface Refreshable {
    void onShown();
}
