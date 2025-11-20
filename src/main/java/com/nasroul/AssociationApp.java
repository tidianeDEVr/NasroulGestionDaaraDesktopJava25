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
        // Load Splash Screen
        FXMLLoader splashLoader = new FXMLLoader(getClass().getResource("/fxml/SplashScreen.fxml"));
        Scene splashScene = new Scene(splashLoader.load(), 600, 400);

        SplashScreenController splashController = splashLoader.getController();
        splashController.setStage(primaryStage);

        primaryStage.setTitle("Association Manager - Chargement");
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
