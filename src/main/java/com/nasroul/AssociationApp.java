package com.nasroul;

import com.nasroul.controller.SplashScreenController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class AssociationApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Filet de sécurité : une exception non attrapée sur le thread JavaFX
        // ne doit plus faire planter l'application silencieusement
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            throwable.printStackTrace();
            if (javafx.application.Platform.isFxApplicationThread()) {
                com.nasroul.ui.Dialogs.error(null, "Erreur inattendue",
                    "Une erreur inattendue s'est produite :\n" + throwable
                    + "\n\nL'application reste ouverte ; si le problème se répète, redémarrez-la.");
            }
        });

        // Load Splash Screen
        FXMLLoader splashLoader = new FXMLLoader(getClass().getResource("/fxml/SplashScreen.fxml"));
        Scene splashScene = new Scene(splashLoader.load(), 600, 400);
        com.nasroul.ui.ThemeManager.applyTo(splashScene);

        SplashScreenController splashController = splashLoader.getController();
        splashController.setStage(primaryStage);

        primaryStage.setTitle("Nasroul Mouminina - Chargement");
        primaryStage.initStyle(StageStyle.UNDECORATED);
        primaryStage.setScene(splashScene);
        primaryStage.setResizable(false);
        primaryStage.centerOnScreen();
        primaryStage.show();

        System.out.println("Application started - Splash Screen displayed");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
