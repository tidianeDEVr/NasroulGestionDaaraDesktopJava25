package com.nasroul;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class AssociationApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        Scene scene = new Scene(loader.load(), 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

        primaryStage.setTitle("Nasroul - Gestion du Daara");
        primaryStage.setScene(scene);
        primaryStage.show();

        System.out.println("Application started successfully!");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
