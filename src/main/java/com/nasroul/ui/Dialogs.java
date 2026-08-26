package com.nasroul.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.geometry.Rectangle2D;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.util.Optional;

/**
 * Alertes et dialogues modaux, toujours thémés.
 *
 * Remplace les showError/showWarning/showInfo dupliqués dans les contrôleurs
 * et les {@code new Scene(loader.load())} nus qui affichaient les dialogues
 * sans aucun style.
 */
public final class Dialogs {

    private Dialogs() {
    }

    public static void info(Window owner, String title, String message) {
        show(Alert.AlertType.INFORMATION, owner, title, message);
    }

    public static void warn(Window owner, String title, String message) {
        show(Alert.AlertType.WARNING, owner, title, message);
    }

    public static void error(Window owner, String title, String message) {
        show(Alert.AlertType.ERROR, owner, title, message);
    }

    /** @return true si l'utilisateur a confirmé */
    public static boolean confirm(Window owner, String title, String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        if (owner != null) {
            alert.initOwner(owner);
        }
        ThemeManager.applyTo(alert.getDialogPane());
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private static void show(Alert.AlertType type, Window owner, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        if (owner != null) {
            alert.initOwner(owner);
        }
        ThemeManager.applyTo(alert.getDialogPane());
        alert.showAndWait();
    }

    /** Résultat d'ouverture d'un dialogue FXML : la fenêtre et son contrôleur. */
    public record Modal<C>(Stage stage, C controller) {
    }

    /**
     * Charge un FXML dans un Stage modal thémé (non affiché : appeler
     * {@code stage().showAndWait()} après avoir configuré le contrôleur).
     */
    public static <C> Modal<C> openModal(String fxmlPath, String title, Window owner) throws IOException {
        FXMLLoader loader = new FXMLLoader(Dialogs.class.getResource(fxmlPath));
        Parent root = loader.load();

        // Sur un petit ecran, un dialogue plus haut que la zone utile sortait de
        // l'ecran et ses boutons du bas devenaient inaccessibles. On l'enveloppe
        // alors dans un ScrollPane plutot que de le tronquer.
        Rectangle2D visual = Screen.getPrimary().getVisualBounds();
        double maxWidth = visual.getWidth() - 40;    // marge pour les bords
        double maxHeight = visual.getHeight() - 60;  // marge pour la barre de titre
        double prefWidth = root.prefWidth(-1);
        double prefHeight = root.prefHeight(prefWidth);

        Scene scene;
        if (prefWidth > maxWidth || prefHeight > maxHeight) {
            ScrollPane scroller = new ScrollPane(root);
            scroller.getStyleClass().add("modal-scroll");
            scroller.setFitToWidth(true);
            scroller.setFitToHeight(true);
            scroller.setPrefSize(Math.min(prefWidth, maxWidth), Math.min(prefHeight, maxHeight));
            scene = new Scene(scroller);
        } else {
            scene = new Scene(root);
        }
        ThemeManager.applyTo(scene);

        Stage stage = new Stage();
        stage.setTitle(title);
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setScene(scene);
        stage.setOnShown(e -> stage.centerOnScreen());
        return new Modal<>(stage, loader.getController());
    }
}
