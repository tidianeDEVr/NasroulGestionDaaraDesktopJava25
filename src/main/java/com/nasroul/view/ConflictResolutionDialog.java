package com.nasroul.view;

import com.nasroul.model.SyncableEntity;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;

/**
 * Dialog for manual conflict resolution
 * Shows local and remote versions side by side and lets user choose which to keep
 */
public class ConflictResolutionDialog extends Stage {

    private final SyncableEntity localEntity;
    private final SyncableEntity remoteEntity;
    private final String tableName;
    private final int recordId;

    private ConflictResolution resolution;

    public ConflictResolutionDialog(String tableName, int recordId,
                                     SyncableEntity localEntity, SyncableEntity remoteEntity) {
        this.tableName = tableName;
        this.recordId = recordId;
        this.localEntity = localEntity;
        this.remoteEntity = remoteEntity;
        this.resolution = ConflictResolution.SKIP;

        initUI();
    }

    private void initUI() {
        setTitle("Résolution de Conflit - " + tableName + " #" + recordId);
        initModality(Modality.APPLICATION_MODAL);
        setResizable(true);
        setWidth(800);
        setHeight(600);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #f5f5f5;");

        // Header
        Label titleLabel = new Label("⚠️ Conflit de Synchronisation Détecté");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #d32f2f;");

        Label descLabel = new Label(
            "Les versions locale et distante de cet enregistrement sont différentes.\n" +
            "Veuillez choisir quelle version vous souhaitez conserver."
        );
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-font-size: 13px;");

        // Info panel
        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(10);
        infoGrid.setVgap(5);
        infoGrid.setPadding(new Insets(10));
        infoGrid.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-border-radius: 5; -fx-background-radius: 5;");

        addInfoRow(infoGrid, 0, "Table:", tableName);
        addInfoRow(infoGrid, 1, "ID Record:", String.valueOf(recordId));

        // Comparison panel
        HBox comparisonBox = new HBox(10);
        comparisonBox.setAlignment(Pos.TOP_CENTER);

        // Local version panel
        VBox localPanel = createVersionPanel("Version Locale", localEntity, true);
        localPanel.setStyle("-fx-background-color: #e3f2fd; -fx-border-color: #2196f3; -fx-border-width: 2; -fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 15;");

        // Remote version panel
        VBox remotePanel = createVersionPanel("Version Distante (MySQL)", remoteEntity, false);
        remotePanel.setStyle("-fx-background-color: #fff3e0; -fx-border-color: #ff9800; -fx-border-width: 2; -fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 15;");

        HBox.setHgrow(localPanel, Priority.ALWAYS);
        HBox.setHgrow(remotePanel, Priority.ALWAYS);

        comparisonBox.getChildren().addAll(localPanel, remotePanel);

        // Action buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        Button keepLocalBtn = new Button("✓ Garder Version Locale");
        keepLocalBtn.setStyle("-fx-background-color: #2196f3; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20;");
        keepLocalBtn.setOnAction(e -> {
            resolution = ConflictResolution.KEEP_LOCAL;
            close();
        });

        Button keepRemoteBtn = new Button("✓ Garder Version Distante");
        keepRemoteBtn.setStyle("-fx-background-color: #ff9800; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20;");
        keepRemoteBtn.setOnAction(e -> {
            resolution = ConflictResolution.KEEP_REMOTE;
            close();
        });

        Button skipBtn = new Button("⏭ Ignorer");
        skipBtn.setStyle("-fx-background-color: #9e9e9e; -fx-text-fill: white; -fx-padding: 10 20;");
        skipBtn.setOnAction(e -> {
            resolution = ConflictResolution.SKIP;
            close();
        });

        buttonBox.getChildren().addAll(keepLocalBtn, keepRemoteBtn, skipBtn);

        // Add all to root
        root.getChildren().addAll(
            titleLabel,
            descLabel,
            infoGrid,
            new Separator(),
            comparisonBox,
            buttonBox
        );

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #f5f5f5;");

        Scene scene = new Scene(scrollPane);
        setScene(scene);
    }

    private VBox createVersionPanel(String title, SyncableEntity entity, boolean isLocal) {
        VBox panel = new VBox(10);

        // Title
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        if (entity == null) {
            Label notExistLabel = new Label("❌ N'existe pas");
            notExistLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");
            panel.getChildren().addAll(titleLabel, notExistLabel);
            return panel;
        }

        // Check if deleted
        if (entity.isDeleted()) {
            Label deletedLabel = new Label("🗑️ Supprimé");
            deletedLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #d32f2f; -fx-font-weight: bold;");
            panel.getChildren().addAll(titleLabel, deletedLabel);
        }

        // Metadata
        GridPane metaGrid = new GridPane();
        metaGrid.setHgap(10);
        metaGrid.setVgap(5);
        metaGrid.setStyle("-fx-padding: 5;");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        int row = 0;
        if (entity.getCreatedAt() != null) {
            addMetaRow(metaGrid, row++, "Créé:", entity.getCreatedAt().format(formatter));
        }
        if (entity.getUpdatedAt() != null) {
            addMetaRow(metaGrid, row++, "Modifié:", entity.getUpdatedAt().format(formatter));
        }
        if (entity.getLastModifiedBy() != null) {
            addMetaRow(metaGrid, row++, "Par:", entity.getLastModifiedBy());
        }
        if (entity.getSyncVersion() != null) {
            addMetaRow(metaGrid, row++, "Version:", String.valueOf(entity.getSyncVersion()));
        }
        addMetaRow(metaGrid, row++, "Hash:", entity.calculateHash().substring(0, 16) + "...");

        // Data fields
        TextArea dataArea = new TextArea(formatEntityData(entity));
        dataArea.setEditable(false);
        dataArea.setWrapText(true);
        dataArea.setPrefRowCount(10);
        dataArea.setStyle("-fx-control-inner-background: white; -fx-font-size: 12px;");

        panel.getChildren().addAll(titleLabel, metaGrid, new Separator(), dataArea);

        return panel;
    }

    private String formatEntityData(SyncableEntity entity) {
        if (entity == null) {
            return "Aucune donnée";
        }

        StringBuilder sb = new StringBuilder();
        entity.getFieldValuesForHash().forEach((key, value) -> {
            sb.append(key).append(": ").append(value != null ? value : "null").append("\n");
        });

        return sb.toString();
    }

    private void addInfoRow(GridPane grid, int row, String label, String value) {
        Label labelNode = new Label(label);
        labelNode.setStyle("-fx-font-weight: bold;");

        Label valueNode = new Label(value);

        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }

    private void addMetaRow(GridPane grid, int row, String label, String value) {
        Label labelNode = new Label(label);
        labelNode.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        Label valueNode = new Label(value);
        valueNode.setStyle("-fx-font-size: 11px;");

        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }

    /**
     * Show dialog and wait for user decision
     */
    public ConflictResolution showAndWait() {
        super.showAndWait();
        return resolution;
    }

    /**
     * Conflict resolution options
     */
    public enum ConflictResolution {
        KEEP_LOCAL,
        KEEP_REMOTE,
        SKIP
    }
}
