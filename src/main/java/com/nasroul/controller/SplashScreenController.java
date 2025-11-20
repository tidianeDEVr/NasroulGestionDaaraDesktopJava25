package com.nasroul.controller;

import com.nasroul.dao.DatabaseManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;

public class SplashScreenController {

    @FXML
    private Label statusLabel;

    @FXML
    private Label errorLabel;

    @FXML
    private ProgressBar progressBar;

    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void initialize() {
        // Start database connection test in background
        Task<Boolean> connectionTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                updateMessage("Vérification de la configuration...");
                updateProgress(0.2, 1.0);
                Thread.sleep(500);

                updateMessage("Connexion à la base de données...");
                updateProgress(0.5, 1.0);

                // Initialize database (this will test the connection)
                DatabaseManager dbManager = DatabaseManager.getInstance();
                Thread.sleep(500);

                updateProgress(0.8, 1.0);

                // Check if there was a connection error
                if (dbManager.hasConnectionError()) {
                    updateMessage("Erreur de connexion");
                    throw new Exception(dbManager.getConnectionError());
                }

                updateMessage("Connexion établie avec succès!");
                updateProgress(1.0, 1.0);
                Thread.sleep(500);

                return true;
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                Platform.runLater(() -> {
                    try {
                        loadMainApplication();
                    } catch (Exception e) {
                        showError("Erreur lors du chargement de l'application: " + e.getMessage());
                    }
                });
            }

            @Override
            protected void failed() {
                super.failed();
                Platform.runLater(() -> {
                    Throwable exception = getException();
                    String errorMessage = exception != null ? exception.getMessage() : "Erreur inconnue";
                    showError("Impossible de se connecter à la base de données.\n\n" +
                             "Détails: " + errorMessage + "\n\n" +
                             "Veuillez vérifier votre fichier config.properties et assurez-vous que:\n" +
                             "- Pour MySQL: le serveur est démarré et les identifiants sont corrects\n" +
                             "- Pour SQLite: le fichier de base de données est accessible");
                });
            }
        };

        statusLabel.textProperty().bind(connectionTask.messageProperty());
        progressBar.progressProperty().bind(connectionTask.progressProperty());

        Thread thread = new Thread(connectionTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        progressBar.setVisible(false);
        statusLabel.setVisible(false);
    }

    private void loadMainApplication() throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

        // Create new stage for main application
        Stage mainStage = new Stage();
        mainStage.setScene(scene);
        mainStage.setTitle("Association Manager");
        mainStage.setResizable(true);
        mainStage.centerOnScreen();

        // Close splash screen and show main application
        stage.close();
        mainStage.show();
    }
}
