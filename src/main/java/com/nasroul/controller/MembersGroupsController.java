package com.nasroul.controller;

import com.nasroul.ui.Refreshable;
import javafx.fxml.FXML;
import javafx.scene.control.TabPane;

/**
 * Section « Membres & Groupes » : deux onglets qui réutilisent les vues
 * MemberView et GroupView existantes (fx:include).
 */
public class MembersGroupsController implements Refreshable {

    @FXML private TabPane tabPane;
    @FXML private MemberController memberViewController;
    @FXML private GroupController groupViewController;

    @FXML
    public void initialize() {
        // Rafraîchir l'onglet quand on y arrive (les données peuvent avoir
        // changé depuis : sync, cotisations, fiche collecte...)
        tabPane.getSelectionModel().selectedIndexProperty().addListener((obs, old, idx) -> refreshTab(idx.intValue()));
    }

    @Override
    public void onShown() {
        refreshTab(tabPane.getSelectionModel().getSelectedIndex());
    }

    private void refreshTab(int index) {
        if (index == 0 && memberViewController != null) {
            memberViewController.onShown();
        } else if (index == 1 && groupViewController != null) {
            groupViewController.onShown();
        }
    }
}
