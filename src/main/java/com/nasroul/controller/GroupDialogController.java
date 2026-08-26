package com.nasroul.controller;

import com.nasroul.model.Group;
import com.nasroul.ui.Forms;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class GroupDialogController {

    @FXML private TextField txtName;
    @FXML private TextArea txtDescription;
    @FXML private CheckBox cbActive;

    private Group group;
    private boolean saved = false;

    public void initialize() {
        cbActive.setSelected(true);
    }

    public void setGroup(Group group) {
        this.group = group;

        if (group.getId() != null) {
            // Edit mode - populate fields
            Forms.setText(txtName, group.getName());
            Forms.setText(txtDescription, group.getDescription());
            cbActive.setSelected(group.isActive());
        }
    }

    @FXML
    private void handleSave() {
        // Validate required fields
        if (Forms.text(txtName).isEmpty()) {
            showError("Le nom est obligatoire");
            return;
        }

        // Update group object
        if (group == null) {
            group = new Group();
        }

        group.setName(Forms.text(txtName));

        group.setDescription(Forms.textOrNull(txtDescription));

        group.setActive(cbActive.isSelected());

        saved = true;
        closeDialog();
    }

    @FXML
    private void handleCancel() {
        saved = false;
        closeDialog();
    }

    public boolean isSaved() {
        return saved;
    }

    public Group getGroup() {
        return group;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void closeDialog() {
        getStage().close();
    }

    private Stage getStage() {
        return (Stage) txtName.getScene().getWindow();
    }
}
