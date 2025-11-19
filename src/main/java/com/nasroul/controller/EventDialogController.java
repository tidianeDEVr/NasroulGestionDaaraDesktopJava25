package com.nasroul.controller;

import com.nasroul.model.Event;
import com.nasroul.model.Member;
import com.nasroul.service.MemberService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class EventDialogController {

    @FXML private TextField txtName;
    @FXML private TextArea txtDescription;
    @FXML private DatePicker dpStartDate;
    @FXML private DatePicker dpEndDate;
    @FXML private TextField txtLocation;
    @FXML private ComboBox<Member> cbOrganizer;
    @FXML private TextField txtContributionTarget;
    @FXML private ComboBox<String> cbStatus;
    @FXML private CheckBox cbActive;

    private Event event;
    private boolean saved = false;
    private final MemberService memberService;

    public EventDialogController() {
        this.memberService = new MemberService();
    }

    public void initialize() {
        // Set default values
        cbActive.setSelected(true);
        dpStartDate.setValue(LocalDate.now());

        // Populate status options
        cbStatus.getItems().addAll("Planifié", "En cours", "Terminé", "Annulé");
        cbStatus.setValue("Planifié");

        // Load members for organizer dropdown
        loadMembers();

        // Set custom cell factory to display member names
        cbOrganizer.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Member member, boolean empty) {
                super.updateItem(member, empty);
                setText(empty || member == null ? "" : member.getFullName());
            }
        });
        cbOrganizer.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Member member, boolean empty) {
                super.updateItem(member, empty);
                setText(empty || member == null ? "" : member.getFullName());
            }
        });
    }

    private void loadMembers() {
        try {
            List<Member> members = memberService.getAllMembers();
            cbOrganizer.getItems().setAll(members);
        } catch (SQLException e) {
            showError("Erreur lors du chargement des membres: " + e.getMessage());
        }
    }

    public void setEvent(Event event) {
        this.event = event;

        if (event.getId() != null) {
            // Edit mode - populate fields
            txtName.setText(event.getName());
            txtDescription.setText(event.getDescription());

            if (event.getStartDate() != null) {
                dpStartDate.setValue(event.getStartDate().toLocalDate());
            }

            if (event.getEndDate() != null) {
                dpEndDate.setValue(event.getEndDate().toLocalDate());
            }

            txtLocation.setText(event.getLocation());
            cbStatus.setValue(translateStatusToFrench(event.getStatus()));
            cbActive.setSelected(event.isActive());

            if (event.getContributionTarget() != null) {
                txtContributionTarget.setText(event.getContributionTarget().toString());
            }

            // Select organizer
            if (event.getOrganizerId() != null) {
                cbOrganizer.getItems().stream()
                    .filter(m -> m.getId().equals(event.getOrganizerId()))
                    .findFirst()
                    .ifPresent(cbOrganizer::setValue);
            }
        }
    }

    @FXML
    private void handleSave() {
        // Validate required fields
        if (txtName.getText().trim().isEmpty()) {
            showError("Le nom est obligatoire");
            return;
        }

        if (dpStartDate.getValue() == null) {
            showError("La date de début est obligatoire");
            return;
        }

        // Validate contribution target if provided
        Double contributionTarget = 0.0;
        String contributionTargetText = txtContributionTarget.getText();
        if (contributionTargetText != null && !contributionTargetText.trim().isEmpty()) {
            try {
                contributionTarget = Double.parseDouble(contributionTargetText.trim());
                if (contributionTarget < 0) {
                    showError("Le budget de cotisation ne peut pas être négatif");
                    return;
                }
            } catch (NumberFormatException e) {
                showError("Format de budget invalide");
                return;
            }
        }

        // Validate dates
        if (dpEndDate.getValue() != null && dpEndDate.getValue().isBefore(dpStartDate.getValue())) {
            showError("La date de fin ne peut pas être avant la date de début");
            return;
        }

        // Update event object
        if (event == null) {
            event = new Event();
        }

        event.setName(txtName.getText().trim());

        String description = txtDescription.getText();
        event.setDescription(description != null && !description.trim().isEmpty() ? description.trim() : null);

        event.setStartDate(LocalDateTime.of(dpStartDate.getValue(), LocalTime.of(0, 0)));
        event.setEndDate(dpEndDate.getValue() != null ? LocalDateTime.of(dpEndDate.getValue(), LocalTime.of(23, 59)) : null);

        String location = txtLocation.getText();
        event.setLocation(location != null && !location.trim().isEmpty() ? location.trim() : null);

        event.setStatus(translateStatusToEnglish(cbStatus.getValue()));
        event.setContributionTarget(contributionTarget);
        event.setActive(cbActive.isSelected());

        if (cbOrganizer.getValue() != null) {
            event.setOrganizerId(cbOrganizer.getValue().getId());
            event.setOrganizerName(cbOrganizer.getValue().getFullName());
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

    public Event getEvent() {
        return event;
    }

    private String translateStatusToFrench(String status) {
        if (status == null) return "Planifié";
        switch (status) {
            case "PLANNED": return "Planifié";
            case "ONGOING": return "En cours";
            case "COMPLETED": return "Terminé";
            case "CANCELLED": return "Annulé";
            default: return status;
        }
    }

    private String translateStatusToEnglish(String status) {
        if (status == null) return "PLANNED";
        switch (status) {
            case "Planifié": return "PLANNED";
            case "En cours": return "ONGOING";
            case "Terminé": return "COMPLETED";
            case "Annulé": return "CANCELLED";
            default: return status;
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
        return (Stage) txtName.getScene().getWindow();
    }
}
