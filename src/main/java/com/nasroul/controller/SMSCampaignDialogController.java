package com.nasroul.controller;

import com.nasroul.model.Event;
import com.nasroul.model.Group;
import com.nasroul.model.Member;
import com.nasroul.model.Project;
import com.nasroul.service.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class SMSCampaignDialogController {

    @FXML private Label lblEntityInfo;
    @FXML private ComboBox<Group> cbGroup;
    @FXML private Label lblRecipientCount;
    @FXML private Label lblSMSBalance;
    @FXML private Label lblVariables;
    @FXML private TextArea txtMessage;
    @FXML private Label lblCharCount;
    @FXML private TextArea txtPreview;
    @FXML private Button btnSend;
    @FXML private VBox progressContainer;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblProgress;

    private final GroupService groupService;
    private final MemberService memberService;
    private final PaymentGroupService paymentGroupService;
    private final ContributionService contributionService;
    private final EventService eventService;
    private final ProjectService projectService;
    private final SMSService smsService;

    private String entityType;
    private Integer entityId;
    private String entityName;
    private String entityDeadline;
    private List<Member> recipients;
    private int smsBalance = -1;

    public SMSCampaignDialogController() {
        this.groupService = new GroupService();
        this.memberService = new MemberService();
        this.paymentGroupService = new PaymentGroupService();
        this.contributionService = new ContributionService();
        this.eventService = new EventService();
        this.projectService = new ProjectService();
        this.smsService = new SMSService();
    }

    public void initialize() {
        loadGroups();
        checkSMSBalance();

        lblVariables.setText(smsService.getAvailableVariables());

        cbGroup.setOnAction(e -> updateRecipients());
        txtMessage.textProperty().addListener((obs, old, newVal) -> {
            updateCharCount();
            updatePreview();
        });

        cbGroup.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Group group, boolean empty) {
                super.updateItem(group, empty);
                if (empty || group == null) {
                    setText("");
                } else {
                    String displayText = group.getName();
                    if (entityName != null && !entityName.isEmpty()) {
                        displayText = group.getName() + " - " + entityName;
                    }
                    setText(displayText);
                }
            }
        });
        cbGroup.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Group group, boolean empty) {
                super.updateItem(group, empty);
                if (empty || group == null) {
                    setText("");
                } else {
                    String displayText = group.getName();
                    if (entityName != null && !entityName.isEmpty()) {
                        displayText = group.getName() + " - " + entityName;
                    }
                    setText(displayText);
                }
            }
        });
    }

    public void setEntity(String entityType, Integer entityId, String entityName) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.entityName = entityName;
        this.entityDeadline = loadEntityDeadline();

        String typeText = "EVENT".equals(entityType) ? "Événement" : "Projet";
        lblEntityInfo.setText(String.format("Pour %s : %s", typeText, entityName));
    }

    private String loadEntityDeadline() {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            if ("EVENT".equals(entityType)) {
                Event event = eventService.getEventById(entityId);
                if (event != null && event.getEndDate() != null) {
                    return event.getEndDate().format(formatter);
                }
            } else {
                Project project = projectService.getProjectById(entityId);
                if (project != null && project.getEndDate() != null) {
                    return project.getEndDate().format(formatter);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading entity deadline: " + e.getMessage());
        }
        return "";
    }

    private double calculateGlobalRemaining() {
        try {
            Double totalCollected = contributionService.getTotalByEntity(entityType, entityId);
            double collected = totalCollected != null ? totalCollected : 0.0;

            double target = 0.0;
            if ("EVENT".equals(entityType)) {
                Event event = eventService.getEventById(entityId);
                if (event != null && event.getContributionTarget() != null) {
                    target = event.getContributionTarget();
                }
            } else {
                Project project = projectService.getProjectById(entityId);
                if (project != null && project.getContributionTarget() != null) {
                    target = project.getContributionTarget();
                }
            }

            double remaining = target - collected;
            return remaining > 0 ? remaining : 0.0;
        } catch (SQLException e) {
            System.err.println("Error calculating global remaining: " + e.getMessage());
            return 0.0;
        }
    }

    private void loadGroups() {
        try {
            List<Group> groups = groupService.getActiveGroups();
            cbGroup.getItems().setAll(groups);
        } catch (SQLException e) {
            showError("Erreur lors du chargement des groupes: " + e.getMessage());
        }
    }

    private void checkSMSBalance() {
        new Thread(() -> {
            smsBalance = smsService.checkSMSBalance();
            final String errorMessage = smsService.getLastErrorMessage();
            Platform.runLater(() -> {
                if (smsBalance >= 0) {
                    lblSMSBalance.setText(String.format("%d SMS", smsBalance));
                    lblSMSBalance.setStyle("-fx-font-weight: bold; -fx-text-fill: " +
                        (smsBalance > 100 ? "#1a7f37;" : smsBalance > 20 ? "#bf8700;" : "#cf222e;"));
                } else {
                    lblSMSBalance.setText("Erreur");
                    lblSMSBalance.setStyle("-fx-font-weight: bold; -fx-text-fill: #cf222e; -fx-cursor: hand;");
                    lblSMSBalance.setOnMouseClicked(e -> {
                        if (errorMessage != null) {
                            showError(errorMessage);
                        }
                    });
                }
            });
        }).start();
    }

    private void updateRecipients() {
        Group selectedGroup = cbGroup.getValue();
        if (selectedGroup == null) {
            recipients = null;
            lblRecipientCount.setText("0");
            updatePreview();
            return;
        }

        try {
            List<Member> allMembers = memberService.getActiveMembers();

            recipients = allMembers.stream()
                .filter(m -> m.getGroupIds() != null && m.getGroupIds().contains(selectedGroup.getId()))
                .filter(m -> m.getPhone() != null && !m.getPhone().trim().isEmpty())
                .collect(Collectors.toList());

            lblRecipientCount.setText(String.valueOf(recipients.size()));
            updatePreview();
        } catch (SQLException e) {
            System.err.println("Error loading members: " + e.getMessage());
            showError("Erreur lors du chargement des membres: " + e.getMessage());
        }
    }

    private void updateCharCount() {
        int charCount = txtMessage.getText().length();
        lblCharCount.setText(charCount + " caractères");
    }

    private void updatePreview() {
        if (recipients == null || recipients.isEmpty()) {
            txtPreview.setText("Sélectionnez un groupe pour voir l'aperçu");
            return;
        }

        if (txtMessage.getText().trim().isEmpty()) {
            txtPreview.setText("Saisissez un message pour voir l'aperçu");
            return;
        }

        Member firstMember = recipients.get(0);
        Group selectedGroup = cbGroup.getValue();
        try {
            double remainingAmount = paymentGroupService.calculateRemainingAmount(
                firstMember.getId(), entityType, entityId, selectedGroup.getId());

            Double totalAmount = paymentGroupService.getTotalAmountByEntityAndGroup(entityType, entityId, selectedGroup.getId());
            double total = totalAmount != null ? totalAmount : 0.0;
            double amountPaid = total - remainingAmount;
            double globalRemaining = calculateGlobalRemaining();

            String preview = smsService.replaceVariables(
                txtMessage.getText(),
                firstMember.getFirstName(),
                firstMember.getLastName(),
                remainingAmount,
                total,
                entityName,
                amountPaid,
                globalRemaining,
                selectedGroup.getName(),
                entityDeadline
            );
            txtPreview.setText(preview);
        } catch (Exception e) {
            System.err.println("Error updating preview: " + e.getMessage());
            txtPreview.setText("Erreur lors du calcul du montant restant: " + e.getMessage());
        }
    }

    @FXML
    private void handleSend() {
        if (cbGroup.getValue() == null) {
            showError("Veuillez sélectionner un groupe");
            return;
        }

        if (txtMessage.getText().trim().isEmpty()) {
            showError("Veuillez saisir un message");
            return;
        }

        if (recipients == null || recipients.isEmpty()) {
            showError("Aucun destinataire trouvé dans ce groupe avec un numéro de téléphone");
            return;
        }

        if (smsBalance < 0) {
            String errorMsg = smsService.getLastErrorMessage();
            if (errorMsg != null) {
                showError(errorMsg);
            } else {
                showError("Impossible de vérifier le solde SMS.\n\nVeuillez vérifier votre connexion Internet et réessayer.");
            }
            return;
        }

        if (smsBalance < recipients.size()) {
            showError(String.format(
                "Solde insuffisant. Vous avez %d SMS disponibles mais %d messages à envoyer.",
                smsBalance, recipients.size()
            ));
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmer l'envoi");
        confirmation.setHeaderText(String.format("Envoyer %d SMS ?", recipients.size()));
        confirmation.setContentText(String.format(
            "Vous êtes sur le point d'envoyer %d SMS.\nSolde actuel : %d SMS\nSolde après envoi : %d SMS\n\nConfirmer ?",
            recipients.size(), smsBalance, smsBalance - recipients.size()
        ));

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                sendCampaign();
            }
        });
    }

    private void sendCampaign() {
        btnSend.setDisable(true);
        progressContainer.setVisible(true);
        progressContainer.setManaged(true);
        progressBar.setProgress(0);
        lblProgress.setText("0/" + recipients.size());

        final int selectedGroupId = cbGroup.getValue().getId();
        final String groupName = cbGroup.getValue().getName();
        final int totalRecipients = recipients.size();

        new Thread(() -> {
            int successCount = 0;
            int failureCount = 0;

            double totalAmount = 0.0;
            try {
                Double total = paymentGroupService.getTotalAmountByEntityAndGroup(entityType, entityId, selectedGroupId);
                totalAmount = total != null ? total : 0.0;
            } catch (Exception e) {
                System.err.println("Error getting total amount: " + e.getMessage());
            }

            double globalRemaining = calculateGlobalRemaining();

            for (int i = 0; i < recipients.size(); i++) {
                Member member = recipients.get(i);
                final int current = i + 1;

                try {
                    double remainingAmount = paymentGroupService.calculateRemainingAmount(
                        member.getId(), entityType, entityId, selectedGroupId);
                    double amountPaid = totalAmount - remainingAmount;

                    String message = smsService.replaceVariables(
                        txtMessage.getText(),
                        member.getFirstName(),
                        member.getLastName(),
                        remainingAmount,
                        totalAmount,
                        entityName,
                        amountPaid,
                        globalRemaining,
                        groupName,
                        entityDeadline
                    );

                    boolean sent = smsService.sendSMS(member.getPhone(), message);
                    if (sent) {
                        successCount++;
                    } else {
                        failureCount++;
                    }

                    Platform.runLater(() -> {
                        progressBar.setProgress((double) current / totalRecipients);
                        lblProgress.setText(current + "/" + totalRecipients);
                    });

                    Thread.sleep(500);
                } catch (Exception e) {
                    failureCount++;
                    System.err.println("Error sending SMS to " + member.getFullName() + ": " + e.getMessage());
                }
            }

            final int finalSuccess = successCount;
            final int finalFailure = failureCount;

            Platform.runLater(() -> {
                btnSend.setDisable(false);
                progressContainer.setVisible(false);
                progressContainer.setManaged(false);

                if (finalFailure == 0) {
                    showInfo("Succès", String.format("Tous les %d SMS ont été envoyés avec succès !", finalSuccess));
                    closeDialog();
                } else {
                    showWarning("Envoi partiel",
                        String.format("Envoi terminé.\nSuccès : %d\nÉchecs : %d", finalSuccess, finalFailure));
                }
            });
        }).start();
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) cbGroup.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
