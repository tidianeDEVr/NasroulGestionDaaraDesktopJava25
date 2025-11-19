package com.nasroul.controller;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MainController {

    @FXML
    private Label dateLabel;

    @FXML
    private StackPane contentPane;

    @FXML
    private Label statusLabel;

    @FXML
    private Button btnDashboard;

    @FXML
    private Button btnContributions;

    @FXML
    private Button btnMembers;

    @FXML
    private Button btnEvents;

    @FXML
    private Button btnProjects;

    @FXML
    private Button btnExpenses;

    @FXML
    private Button btnGroups;

    private Timeline clockTimeline;

    @FXML
    public void initialize() {
        startClock();
        showDashboard();
    }

    private void startClock() {
        // Mettre à jour immédiatement
        updateDateTime();

        // Créer un Timeline qui s'exécute chaque seconde
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateDateTime()));
        clockTimeline.setCycleCount(Animation.INDEFINITE);
        clockTimeline.play();
    }

    private void updateDateTime() {
        if (dateLabel != null) {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE dd MMMM yyyy - HH:mm:ss", java.util.Locale.FRENCH);
            String formattedDate = now.format(formatter);
            String capitalizedDate = formattedDate.substring(0, 1).toUpperCase() + formattedDate.substring(1);
            dateLabel.setText(capitalizedDate);
        }
    }

    private void setActiveButton(Button activeButton) {
        btnDashboard.getStyleClass().remove("menu-button-active");
        btnContributions.getStyleClass().remove("menu-button-active");
        btnMembers.getStyleClass().remove("menu-button-active");
        btnEvents.getStyleClass().remove("menu-button-active");
        btnProjects.getStyleClass().remove("menu-button-active");
        btnExpenses.getStyleClass().remove("menu-button-active");
        btnGroups.getStyleClass().remove("menu-button-active");

        if (!activeButton.getStyleClass().contains("menu-button-active")) {
            activeButton.getStyleClass().add("menu-button-active");
        }
    }

    @FXML
    private void showDashboard() {
        setActiveButton(btnDashboard);
        loadView("/fxml/DashboardView.fxml", "Tableau de bord");
    }

    @FXML
    private void showMembers() {
        setActiveButton(btnMembers);
        loadView("/fxml/MemberView.fxml", "Membres");
    }

    @FXML
    private void showEvents() {
        setActiveButton(btnEvents);
        loadView("/fxml/EventView.fxml", "Événements");
    }

    @FXML
    private void showProjects() {
        setActiveButton(btnProjects);
        loadView("/fxml/ProjectView.fxml", "Projets");
    }

    @FXML
    private void showExpenses() {
        setActiveButton(btnExpenses);
        loadView("/fxml/ExpenseView.fxml", "Dépenses");
    }

    @FXML
    private void showGroups() {
        setActiveButton(btnGroups);
        loadView("/fxml/GroupView.fxml", "Groupes");
    }

    @FXML
    private void showContributions() {
        setActiveButton(btnContributions);
        loadView("/fxml/ContributionView.fxml", "Cotisations");
    }

    @FXML
    private void handleExit() {
        // Arrêter le timer avant de quitter
        if (clockTimeline != null) {
            clockTimeline.stop();
        }
        Platform.exit();
    }

    private void loadView(String fxmlPath, String viewName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();
            contentPane.getChildren().clear();
            contentPane.getChildren().add(view);
            statusLabel.setText("Vue active : " + viewName);
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Error loading view: " + viewName);
        }
    }
}
