package com.nasroul.controller;

import com.nasroul.model.Group;
import com.nasroul.model.Member;
import com.nasroul.service.GroupService;
import com.nasroul.util.ImageUtil;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MemberDialogController {

    @FXML private TextField txtFirstName;
    @FXML private TextField txtLastName;
    @FXML private TextField txtEmail;
    @FXML private TextField txtPhone;
    @FXML private DatePicker dpBirthDate;
    @FXML private TextArea txtAddress;
    @FXML private DatePicker dpJoinDate;
    @FXML private TextField txtRole;
    @FXML private ComboBox<Group> cbGroup;
    @FXML private ImageView avatarView;
    @FXML private CheckBox cbActive;

    private Member member;
    private boolean saved = false;
    private byte[] avatarData;
    private final GroupService groupService;

    public MemberDialogController() {
        this.groupService = new GroupService();
    }

    public void initialize() {
        // Set default values
        cbActive.setSelected(true);
        dpJoinDate.setValue(LocalDate.now());

        // Load groups for ComboBox
        loadGroups();

        // Set custom cell factory to display group names
        cbGroup.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Group group, boolean empty) {
                super.updateItem(group, empty);
                setText(empty || group == null ? "" : group.getName());
            }
        });
        cbGroup.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Group group, boolean empty) {
                super.updateItem(group, empty);
                setText(empty || group == null ? "" : group.getName());
            }
        });
    }

    private void loadGroups() {
        try {
            List<Group> groups = groupService.getActiveGroups();
            cbGroup.getItems().setAll(groups);
        } catch (SQLException e) {
            showError("Erreur lors du chargement des groupes: " + e.getMessage());
        }
    }

    public void setMember(Member member) {
        this.member = member;

        if (member.getId() != null) {
            // Edit mode - populate fields
            txtFirstName.setText(member.getFirstName());
            txtLastName.setText(member.getLastName());
            txtEmail.setText(member.getEmail() != null ? member.getEmail() : "");
            txtPhone.setText(member.getPhone() != null ? member.getPhone() : "");
            dpBirthDate.setValue(member.getBirthDate());
            txtAddress.setText(member.getAddress() != null ? member.getAddress() : "");
            dpJoinDate.setValue(member.getJoinDate());
            txtRole.setText(member.getRole() != null ? member.getRole() : "");
            cbActive.setSelected(member.isActive());

            // Select group if exists (use first group for backward compatibility)
            if (member.getGroupId() != null) {
                for (Group group : cbGroup.getItems()) {
                    if (group.getId().equals(member.getGroupId())) {
                        cbGroup.getSelectionModel().select(group);
                        break;
                    }
                }
            }

            // Load avatar if exists
            if (member.getAvatar() != null && member.getAvatar().length > 0) {
                avatarData = member.getAvatar();
                displayAvatar(avatarData);
            }
        }
    }

    @FXML
    private void handleChooseAvatar() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choisir un avatar");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
        );

        File selectedFile = fileChooser.showOpenDialog(getStage());
        if (selectedFile != null) {
            try {
                // Resize image to 300x300 and convert to byte array
                avatarData = ImageUtil.resizeImage(selectedFile);
                displayAvatar(avatarData);
            } catch (Exception e) {
                showError("Erreur lors du chargement de l'image: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleRemoveAvatar() {
        avatarData = null;
        avatarView.setImage(null);
    }

    @FXML
    private void handleSave() {
        // Validate required fields
        if (txtFirstName.getText().trim().isEmpty()) {
            showError("Le prénom est obligatoire");
            return;
        }

        if (txtLastName.getText().trim().isEmpty()) {
            showError("Le nom est obligatoire");
            return;
        }

        if (dpJoinDate.getValue() == null) {
            showError("La date d'adhésion est obligatoire");
            return;
        }

        // Validate email format if provided
        String email = txtEmail.getText().trim();
        if (!email.isEmpty() && !email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            showError("Format d'email invalide");
            return;
        }

        // Update member object
        if (member == null) {
            member = new Member();
        }

        member.setFirstName(txtFirstName.getText().trim());
        member.setLastName(txtLastName.getText().trim());
        member.setEmail(email.isEmpty() ? null : email);
        member.setPhone(txtPhone.getText().trim().isEmpty() ? null : txtPhone.getText().trim());
        member.setBirthDate(dpBirthDate.getValue());
        member.setAddress(txtAddress.getText().trim().isEmpty() ? null : txtAddress.getText().trim());
        member.setJoinDate(dpJoinDate.getValue());
        member.setRole(txtRole.getText().trim().isEmpty() ? null : txtRole.getText().trim());
        member.setActive(cbActive.isSelected());
        member.setAvatar(avatarData);

        // Set group
        Group selectedGroup = cbGroup.getSelectionModel().getSelectedItem();
        if (selectedGroup != null) {
            member.setGroupId(selectedGroup.getId());
            member.setGroupName(selectedGroup.getName());

            // Also set for multi-group support (single group in list)
            List<Integer> groupIds = new ArrayList<>();
            groupIds.add(selectedGroup.getId());
            List<String> groupNames = new ArrayList<>();
            groupNames.add(selectedGroup.getName());

            member.setGroupIds(groupIds);
            member.setGroupNames(groupNames);
        } else {
            member.setGroupId(null);
            member.setGroupName(null);
            member.setGroupIds(new ArrayList<>());
            member.setGroupNames(new ArrayList<>());
        }

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

    public Member getMember() {
        return member;
    }

    private void displayAvatar(byte[] imageData) {
        if (imageData != null && imageData.length > 0) {
            try {
                Image image = new Image(new ByteArrayInputStream(imageData));
                avatarView.setImage(image);
            } catch (Exception e) {
                showError("Erreur lors de l'affichage de l'image: " + e.getMessage());
            }
        }
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
        return (Stage) txtFirstName.getScene().getWindow();
    }
}
