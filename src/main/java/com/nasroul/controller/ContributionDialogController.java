package com.nasroul.controller;

import com.nasroul.model.Contribution;
import com.nasroul.model.Event;
import com.nasroul.model.Group;
import com.nasroul.model.Member;
import com.nasroul.model.Project;
import com.nasroul.service.ContributionService;
import com.nasroul.service.EventService;
import com.nasroul.service.GroupService;
import com.nasroul.service.MemberService;
import com.nasroul.service.ProjectService;
import com.nasroul.ui.Forms;
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
    private ComboBox<Group> cbGroup;

    @FXML
    private Label lblEntityType;

    @FXML
    private ComboBox<String> cbEntityType;

    @FXML
    private Label lblEntity;

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
    private final GroupService groupService;
    private List<Member> allMembers;
    private List<Group> allGroups;

    public ContributionDialogController() {
        this.contributionService = new ContributionService();
        this.memberService = new MemberService();
        this.eventService = new EventService();
        this.projectService = new ProjectService();
        this.groupService = new GroupService();
    }

    @FXML
    private void initialize() {
        setupComboBoxes();
        setupEntityTypeListener();
        setupMemberSearch();
        setupGroupSelection();
    }

    /**
     * La cotisation est rattachée à un groupe : c'est ce rattachement qui permet
     * de calculer le restant à payer du membre vis-à-vis de l'objectif du groupe.
     * Le combo est peuplé avec les groupes du membre sélectionné, et
     * auto-sélectionné quand le membre n'appartient qu'à un seul groupe.
     */
    private void setupGroupSelection() {
        try {
            allGroups = groupService.getAllGroups();
        } catch (SQLException e) {
            allGroups = List.of();
            showError("Erreur", "Impossible de charger les groupes: " + e.getMessage());
        }

        cbGroup.setCellFactory(param -> new ListCell<Group>() {
            @Override
            protected void updateItem(Group item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        cbGroup.setButtonCell(new ListCell<Group>() {
            @Override
            protected void updateItem(Group item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        cbMember.valueProperty().addListener((obs, oldMember, newMember) -> refreshGroupChoices(newMember));
    }

    private void refreshGroupChoices(Member member) {
        cbGroup.getItems().clear();
        cbGroup.setValue(null);
        if (member == null || member.getGroupIds() == null || allGroups == null) {
            return;
        }
        allGroups.stream()
            .filter(g -> member.getGroupIds().contains(g.getId()))
            .forEach(g -> cbGroup.getItems().add(g));
        if (cbGroup.getItems().size() == 1) {
            cbGroup.setValue(cbGroup.getItems().get(0));
        }
    }

    private void setupComboBoxes() {
        // Types d'entité
        cbEntityType.getItems().addAll("Événement", "Projet");

        // Statuts
        cbStatus.getItems().addAll("Payé", "En attente");

        // Méthodes de paiement
        cbPaymentMethod.getItems().addAll("Espèces", "Wave", "Orange Money", "Virement");

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
            if (selectedType != null) {
                loadEntities(getEntityTypeCode(selectedType));
            } else {
                cbEntity.getItems().clear();
            }
        });

        // Affichage du nom, quel que soit le type (Event ou Project)
        cbEntity.setCellFactory(param -> entityCell());
        cbEntity.setButtonCell(entityCell());
    }

    private ListCell<Object> entityCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : entityName(item));
            }
        };
    }

    private String entityName(Object item) {
        if (item instanceof Event event) {
            return event.getName();
        }
        if (item instanceof Project project) {
            return project.getName();
        }
        return String.valueOf(item);
    }

    private Integer entityId(Object item) {
        if (item instanceof Event event) {
            return event.getId();
        }
        if (item instanceof Project project) {
            return project.getId();
        }
        return null;
    }

    /** Remplit la liste des collectes du type donné (appel synchrone). */
    private void loadEntities(String typeCode) {
        cbEntity.getItems().clear();
        try {
            if ("EVENT".equals(typeCode)) {
                cbEntity.getItems().addAll(eventService.getAllEvents());
            } else if ("PROJECT".equals(typeCode)) {
                cbEntity.getItems().addAll(projectService.getAllProjects());
            }
        } catch (SQLException e) {
            showError("Erreur", "Impossible de charger les collectes: " + e.getMessage());
        }
    }

    /** Sélectionne la collecte correspondante dans la liste déjà chargée. */
    private void selectEntity(int entityId) {
        for (Object item : cbEntity.getItems()) {
            Integer itemId = entityId(item);
            if (itemId != null && itemId == entityId) {
                cbEntity.setValue(item);
                return;
            }
        }
    }

    /**
     * Ouvert depuis la fiche d'une collecte : le type et la collecte sont
     * connus, on les pré-remplit et on masque les deux champs.
     * Tout est synchrone — ne pas dépendre de l'événement onAction du combo
     * Type, qui laissait la collecte non sélectionnée à la validation.
     */
    public void lockEntity(String entityType, int entityId) {
        cbEntityType.setValue(getEntityTypeLabel(entityType));
        loadEntities(entityType);
        selectEntity(entityId);

        for (javafx.scene.Node node : new javafx.scene.Node[]{lblEntityType, cbEntityType, lblEntity, cbEntity}) {
            node.setVisible(false);
            node.setManaged(false);
        }
    }

    public void setContribution(Contribution contribution) {
        this.contribution = contribution;

        if (contribution != null) {
            try {
                Member member = memberService.getMemberById(contribution.getMemberId());
                // Trouver le membre correspondant dans la liste déjà chargée
                // (déclenche le peuplement du combo Groupe via le listener)
                cbMember.getItems().stream()
                    .filter(m -> m.getId().equals(member.getId()))
                    .findFirst()
                    .ifPresent(cbMember::setValue);

                // Sélectionner le groupe déjà rattaché à la cotisation
                if (contribution.getGroupId() != null) {
                    cbGroup.getItems().stream()
                        .filter(g -> g.getId().equals(contribution.getGroupId()))
                        .findFirst()
                        .ifPresent(cbGroup::setValue);
                }

                // Type + collecte : chargement synchrone puis sélection
                String entityType = contribution.getEntityType();
                cbEntityType.setValue(getEntityTypeLabel(entityType));
                loadEntities(entityType);
                selectEntity(contribution.getEntityId());

                Forms.setText(txtAmount, contribution.getAmount());
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
            double amount = Double.parseDouble(Forms.text(txtAmount));
            LocalDate date = dpDate.getValue();
            String statusFr = cbStatus.getValue();
            String paymentMethodFr = cbPaymentMethod.getValue();

            // Convertir les labels français en codes
            String entityType = getEntityTypeCode(entityTypeFr);
            String status = getStatusCode(statusFr);
            String paymentMethod = getPaymentMethodCode(paymentMethodFr);

            Integer resolvedEntityId = entityId(selectedEntity);
            if (resolvedEntityId == null) {
                showError("Erreur", "La collecte sélectionnée est invalide.");
                return;
            }
            int entityId = resolvedEntityId;

            Integer groupId = cbGroup.getValue() != null ? cbGroup.getValue().getId() : null;

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
                newContribution.setGroupId(groupId);
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
                contribution.setGroupId(groupId);
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
        // Garder : Type et Événement/Projet (le combo Groupe se vide via le
        // listener quand le membre est remis à null)
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

        // Le groupe est obligatoire dès que le membre appartient à au moins un
        // groupe (auto-sélectionné s'il n'y en a qu'un). Sans lui, le restant à
        // payer du membre ne peut pas être calculé correctement.
        if (!cbGroup.getItems().isEmpty() && cbGroup.getValue() == null) {
            showWarning("Validation", "Veuillez sélectionner le groupe auquel rattacher cette cotisation.");
            return false;
        }

        if (cbEntityType.getValue() == null) {
            showWarning("Validation", "Veuillez sélectionner un type (Événement ou Projet).");
            return false;
        }

        if (cbEntity.getValue() == null) {
            showWarning("Validation", cbEntity.isVisible()
                ? "Veuillez sélectionner un événement ou projet."
                : "La collecte n'a pas pu être déterminée. Fermez la fiche et rouvrez-la.");
            return false;
        }

        if (txtAmount.getText() == null || Forms.text(txtAmount).isEmpty()) {
            showWarning("Validation", "Veuillez saisir un montant.");
            return false;
        }

        try {
            double amount = Double.parseDouble(Forms.text(txtAmount));
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
        return switch (frenchLabel) {
            case "Événement" -> "EVENT";
            case "Projet" -> "PROJECT";
            default -> frenchLabel;
        };
    }

    private String getEntityTypeLabel(String code) {
        return switch (code) {
            case "EVENT" -> "Événement";
            case "PROJECT" -> "Projet";
            default -> code;
        };
    }

    private String getStatusCode(String frenchLabel) {
        return switch (frenchLabel) {
            case "Payé" -> "PAID";
            case "En attente" -> "PENDING";
            default -> frenchLabel;
        };
    }

    private String getStatusLabel(String code) {
        return switch (code) {
            case "PAID" -> "Payé";
            case "PENDING" -> "En attente";
            default -> code;
        };
    }

    private String getPaymentMethodCode(String frenchLabel) {
        return switch (frenchLabel) {
            case "Espèces" -> "CASH";
            case "Wave" -> "WAVE";
            case "Orange Money" -> "ORANGE_MONEY";
            case "Virement" -> "BANK_TRANSFER";
            default -> frenchLabel;
        };
    }

    private String getPaymentMethodLabel(String code) {
        if (code == null) return null;
        return switch (code) {
            case "CASH" -> "Espèces";
            case "WAVE" -> "Wave";
            case "ORANGE_MONEY" -> "Orange Money";
            case "BANK_TRANSFER" -> "Virement";
            default -> code;
        };
    }
}
