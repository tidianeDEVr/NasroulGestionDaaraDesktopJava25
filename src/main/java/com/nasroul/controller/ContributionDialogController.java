package com.nasroul.controller;

import com.nasroul.model.Contribution;
import com.nasroul.model.Event;
import com.nasroul.model.Member;
import com.nasroul.service.ContributionService;
import com.nasroul.service.EventService;
import com.nasroul.service.MemberService;
import com.nasroul.service.ProjectService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class ContributionDialogController {

    @FXML
    private CheckBox chkContinuousEntry;

    @FXML
    private TextField txtMemberSearch;

    @FXML
    private ComboBox<Member> cbMember;

    @FXML
    private ComboBox<String> cbEntityType;

    @FXML
    private ComboBox<Object> cbEntity;

    @FXML
    private TextField txtAmount;

    @FXML
    private DatePicker dpDate;

    @FXML
    private ComboBox<String> cbStatus;

    @FXML
    private ComboBox<String> cbPaymentMethod;

    private Stage dialogStage;
    private boolean confirmed = false;
    private boolean anySaved = false;
    private Contribution contribution;

    private final ContributionService contributionService;
    private final MemberService memberService;
    private final EventService eventService;
    private final ProjectService projectService;
    private List<Member> allMembers;

    public ContributionDialogController() {
        this.contributionService = new ContributionService();
        this.memberService = new MemberService();
        this.eventService = new EventService();
        this.projectService = new ProjectService();
    }

    @FXML
    private void initialize() {
        setupComboBoxes();
        setupEntityTypeListener();
        setupMemberSearch();
    }

    private void setupComboBoxes() {
        // Types d'entité
        cbEntityType.getItems().addAll("Événement", "Projet");

        // Statuts
        cbStatus.getItems().addAll("Payé", "En attente");

        // Méthodes de paiement
        cbPaymentMethod.getItems().addAll("Espèces", "Mobile Money", "Virement bancaire", "Chèque");

        // Charger les membres
        try {
            allMembers = memberService.getAllMembers();
            cbMember.getItems().addAll(allMembers);
            cbMember.setCellFactory(param -> new ListCell<Member>() {
                @Override
                protected void updateItem(Member item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getFullName());
                }
            });
            cbMember.setButtonCell(new ListCell<Member>() {
                @Override
                protected void updateItem(Member item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getFullName());
                }
            });
        } catch (SQLException e) {
            showError("Erreur", "Impossible de charger les membres: " + e.getMessage());
        }

        // Date par défaut
        dpDate.setValue(LocalDate.now());
    }

    private void setupMemberSearch() {
        // Filtrage en temps réel sur le champ de recherche
        txtMemberSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            if (allMembers == null) return;

            cbMember.getItems().clear();

            if (newValue == null || newValue.trim().isEmpty()) {
                // Si le champ est vide, afficher tous les membres
                cbMember.getItems().addAll(allMembers);
            } else {
                // Filtrer les membres par nom (insensible à la casse)
                String searchTerm = newValue.toLowerCase();
                allMembers.stream()
                    .filter(member -> member.getFullName().toLowerCase().contains(searchTerm))
                    .forEach(member -> cbMember.getItems().add(member));
            }
        });
    }

    private void setupEntityTypeListener() {
        cbEntityType.setOnAction(event -> {
            String selectedType = cbEntityType.getValue();
            cbEntity.getItems().clear();

            if (selectedType == null) {
                return;
            }

            String typeCode = getEntityTypeCode(selectedType);

            try {
                if ("EVENT".equals(typeCode)) {
                    List<Event> events = eventService.getAllEvents();
                    cbEntity.getItems().addAll(events);
                    cbEntity.setCellFactory(param -> new ListCell<Object>() {
                        @Override
                        protected void updateItem(Object item, boolean empty) {
                            super.updateItem(item, empty);
                            setText(empty || item == null ? null : ((Event) item).getName());
                        }
                    });
                    cbEntity.setButtonCell(new ListCell<Object>() {
                        @Override
                        protected void updateItem(Object item, boolean empty) {
                            super.updateItem(item, empty);
                            setText(empty || item == null ? null : ((Event) item).getName());
                        }
                    });
                } else if ("PROJECT".equals(typeCode)) {
                    var projects = projectService.getAllProjects();
                    cbEntity.getItems().addAll(projects);
                    cbEntity.setCellFactory(param -> new ListCell<Object>() {
                        @Override
                        protected void updateItem(Object item, boolean empty) {
                            super.updateItem(item, empty);
                            if (empty || item == null) {
                                setText(null);
                            } else {
                                try {
                                    setText((String) item.getClass().getMethod("getName").invoke(item));
                                } catch (Exception e) {
                                    setText(item.toString());
                                }
                            }
                        }
                    });
                    cbEntity.setButtonCell(new ListCell<Object>() {
                        @Override
                        protected void updateItem(Object item, boolean empty) {
                            super.updateItem(item, empty);
                            if (empty || item == null) {
                                setText(null);
                            } else {
                                try {
                                    setText((String) item.getClass().getMethod("getName").invoke(item));
                                } catch (Exception e) {
                                    setText(item.toString());
                                }
                            }
                        }
                    });
                }
            } catch (SQLException e) {
                showError("Erreur", "Impossible de charger les données: " + e.getMessage());
            }
        });
    }

    public void setContribution(Contribution contribution) {
        this.contribution = contribution;

        if (contribution != null) {
            // Mode édition
            try {
                Member member = memberService.getMemberById(contribution.getMemberId());
                cbMember.setValue(member);

                cbEntityType.setValue(getEntityTypeLabel(contribution.getEntityType()));

                if ("EVENT".equals(contribution.getEntityType())) {
                    Event event = eventService.getEventById(contribution.getEntityId());
                    cbEntity.setValue(event);
                } else if ("PROJECT".equals(contribution.getEntityType())) {
                    var project = projectService.getProjectById(contribution.getEntityId());
                    cbEntity.setValue(project);
                }

                txtAmount.setText(String.valueOf(contribution.getAmount()));
                dpDate.setValue(contribution.getDate());
                cbStatus.setValue(getStatusLabel(contribution.getStatus()));
                cbPaymentMethod.setValue(getPaymentMethodLabel(contribution.getPaymentMethod()));
            } catch (SQLException e) {
                showError("Erreur", "Impossible de charger les données: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleSave() {
        if (!validateInput()) {
            return;
        }

        try {
            Member selectedMember = cbMember.getValue();
            String entityTypeFr = cbEntityType.getValue();
            Object selectedEntity = cbEntity.getValue();
            double amount = Double.parseDouble(txtAmount.getText().trim());
            LocalDate date = dpDate.getValue();
            String statusFr = cbStatus.getValue();
            String paymentMethodFr = cbPaymentMethod.getValue();

            // Convertir les labels français en codes
            String entityType = getEntityTypeCode(entityTypeFr);
            String status = getStatusCode(statusFr);
            String paymentMethod = getPaymentMethodCode(paymentMethodFr);

            int entityId = 0;
            if ("EVENT".equals(entityType)) {
                entityId = ((Event) selectedEntity).getId();
            } else if ("PROJECT".equals(entityType)) {
                entityId = (int) selectedEntity.getClass().getMethod("getId").invoke(selectedEntity);
            }

            if (contribution == null) {
                // Nouvelle cotisation
                Contribution newContribution = new Contribution();
                newContribution.setMemberId(selectedMember.getId());
                newContribution.setEntityType(entityType);
                newContribution.setEntityId(entityId);
                newContribution.setAmount(amount);
                newContribution.setDate(date);
                newContribution.setStatus(status);
                newContribution.setPaymentMethod(paymentMethod);
                contributionService.createContribution(newContribution);
            } else {
                // Modification
                contribution.setMemberId(selectedMember.getId());
                contribution.setEntityType(entityType);
                contribution.setEntityId(entityId);
                contribution.setAmount(amount);
                contribution.setDate(date);
                contribution.setStatus(status);
                contribution.setPaymentMethod(paymentMethod);
                contributionService.updateContribution(contribution);
            }

            confirmed = true;
            anySaved = true;

            // Vérifier si le mode saisie continue est activé
            if (chkContinuousEntry.isSelected()) {
                // Réinitialiser les champs pour la prochaine saisie
                resetFieldsForContinuousEntry();
            } else {
                // Fermer le dialogue
                dialogStage.close();
            }
        } catch (Exception e) {
            showError("Erreur", "Impossible d'enregistrer la cotisation: " + e.getMessage());
        }
    }

    private void resetFieldsForContinuousEntry() {
        // Réinitialiser uniquement les champs qui changent entre les saisies
        // Garder : Type et Événement/Projet
        cbMember.setValue(null);
        txtMemberSearch.clear();
        txtAmount.clear();
        dpDate.setValue(LocalDate.now());
        cbStatus.setValue(null);
        cbPaymentMethod.setValue(null);

        // Remettre le focus sur le champ de recherche
        txtMemberSearch.requestFocus();
    }

    @FXML
    private void handleCancel() {
        // Si on est en mode saisie continue et qu'on a déjà sauvegardé au moins une cotisation
        // on doit marquer confirmed comme true pour que le controller parent recharge la table
        if (anySaved) {
            confirmed = true;
        } else {
            confirmed = false;
        }
        dialogStage.close();
    }

    private boolean validateInput() {
        if (cbMember.getValue() == null) {
            showWarning("Validation", "Veuillez sélectionner un membre.");
            return false;
        }

        if (cbEntityType.getValue() == null) {
            showWarning("Validation", "Veuillez sélectionner un type (Événement ou Projet).");
            return false;
        }

        if (cbEntity.getValue() == null) {
            showWarning("Validation", "Veuillez sélectionner un événement ou projet.");
            return false;
        }

        if (txtAmount.getText() == null || txtAmount.getText().trim().isEmpty()) {
            showWarning("Validation", "Veuillez saisir un montant.");
            return false;
        }

        try {
            double amount = Double.parseDouble(txtAmount.getText().trim());
            if (amount <= 0) {
                showWarning("Validation", "Le montant doit être positif.");
                return false;
            }
        } catch (NumberFormatException e) {
            showWarning("Validation", "Le montant doit être un nombre valide.");
            return false;
        }

        if (dpDate.getValue() == null) {
            showWarning("Validation", "Veuillez sélectionner une date.");
            return false;
        }

        if (cbStatus.getValue() == null) {
            showWarning("Validation", "Veuillez sélectionner un statut.");
            return false;
        }

        return true;
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showWarning(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;

        // Ajouter un listener pour détecter la fermeture de la fenêtre
        dialogStage.setOnCloseRequest(event -> {
            // Si on a enregistré au moins une cotisation, marquer comme confirmé
            // pour que le controller parent recharge la table
            if (anySaved) {
                confirmed = true;
            }
        });
    }

    public Stage getDialogStage() {
        return dialogStage;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    // Méthodes de conversion entre français et codes
    private String getEntityTypeCode(String frenchLabel) {
        switch (frenchLabel) {
            case "Événement": return "EVENT";
            case "Projet": return "PROJECT";
            default: return frenchLabel;
        }
    }

    private String getEntityTypeLabel(String code) {
        switch (code) {
            case "EVENT": return "Événement";
            case "PROJECT": return "Projet";
            default: return code;
        }
    }

    private String getStatusCode(String frenchLabel) {
        switch (frenchLabel) {
            case "Payé": return "PAID";
            case "En attente": return "PENDING";
            default: return frenchLabel;
        }
    }

    private String getStatusLabel(String code) {
        switch (code) {
            case "PAID": return "Payé";
            case "PENDING": return "En attente";
            default: return code;
        }
    }

    private String getPaymentMethodCode(String frenchLabel) {
        switch (frenchLabel) {
            case "Espèces": return "CASH";
            case "Mobile Money": return "MOBILE_MONEY";
            case "Virement bancaire": return "BANK_TRANSFER";
            case "Chèque": return "CHECK";
            default: return frenchLabel;
        }
    }

    private String getPaymentMethodLabel(String code) {
        if (code == null) return null;
        switch (code) {
            case "CASH": return "Espèces";
            case "MOBILE_MONEY": return "Mobile Money";
            case "BANK_TRANSFER": return "Virement bancaire";
            case "CHECK": return "Chèque";
            default: return code;
        }
    }
}
