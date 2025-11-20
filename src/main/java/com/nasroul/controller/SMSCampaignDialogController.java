package com.nasroul.controller;

import com.nasroul.model.Group;
import com.nasroul.model.Member;
import com.nasroul.service.GroupService;
import com.nasroul.service.MemberService;
import com.nasroul.service.PaymentGroupService;
import com.nasroul.service.SMSService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.SQLException;
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

    private final GroupService groupService;
    private final MemberService memberService;
    private final PaymentGroupService paymentGroupService;
    private final SMSService smsService;

    private String entityType;
    private Integer entityId;
    private String entityName;
    private List<Member> recipients;
    private int smsBalance = -1;

    public SMSCampaignDialogController() {
        this.groupService = new GroupService();
        this.memberService = new MemberService();
        this.paymentGroupService = new PaymentGroupService();
        this.smsService = new SMSService();
    }

    public void initialize() {
        loadGroups();
        checkSMSBalance();

        // Set variables label
        lblVariables.setText(smsService.getAvailableVariables());

        // Add listeners
        cbGroup.setOnAction(e -> updateRecipients());
        txtMessage.textProperty().addListener((obs, old, newVal) -> {
            updateCharCount();
            updatePreview();
        });

        // Set custom cell factory for group combo
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

        // Update entity info label
        String typeText = "EVENT".equals(entityType) ? "Événement" : "Projet";
        lblEntityInfo.setText(String.format("Pour %s : %s", typeText, entityName));
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
        // Check balance in background thread
        new Thread(() -> {
            smsBalance = smsService.checkSMSBalance();
            Platform.runLater(() -> {
                if (smsBalance >= 0) {
                    lblSMSBalance.setText(String.format("%d SMS", smsBalance));
                    lblSMSBalance.setStyle("-fx-font-weight: bold; -fx-text-fill: " +
                        (smsBalance > 100 ? "#1a7f37;" : smsBalance > 20 ? "#bf8700;" : "#cf222e;"));
                } else {
                    lblSMSBalance.setText("Erreur de vérification");
                    lblSMSBalance.setStyle("-fx-font-weight: bold; -fx-text-fill: #cf222e;");
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
            // Get all members of the selected group who have a phone number
            List<Member> allMembers = memberService.getActiveMembers();
            System.out.println("Total active members: " + allMembers.size());
            System.out.println("Selected group ID: " + selectedGroup.getId());

            recipients = allMembers.stream()
                .filter(m -> {
                    boolean hasGroups = m.getGroupIds() != null && m.getGroupIds().contains(selectedGroup.getId());
                    if (hasGroups) {
                        System.out.println("Member " + m.getFullName() + " is in group " + selectedGroup.getId());
                    }
                    return hasGroups;
                })
                .filter(m -> {
                    boolean hasPhone = m.getPhone() != null && !m.getPhone().trim().isEmpty();
                    if (!hasPhone) {
                        System.out.println("Member " + m.getFullName() + " has no phone");
                    }
                    return hasPhone;
                })
                .collect(Collectors.toList());

            System.out.println("Total recipients: " + recipients.size());
            lblRecipientCount.setText(String.valueOf(recipients.size()));
            updatePreview();
        } catch (SQLException e) {
            System.err.println("Error loading members: " + e.getMessage());
            e.printStackTrace();
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

        // Show preview with first recipient
        Member firstMember = recipients.get(0);
        try {
            double remainingAmount = paymentGroupService.calculateRemainingAmount(
                firstMember.getId(), entityType, entityId);

            // Get total amount from payment group
            Double totalAmount = paymentGroupService.getTotalAmountByEntity(entityType, entityId);
            double total = totalAmount != null ? totalAmount : 0.0;

            String preview = smsService.replaceVariables(
                txtMessage.getText(),
                firstMember.getFirstName(),
                firstMember.getLastName(),
                remainingAmount,
                total,
                entityName
            );
            txtPreview.setText(preview);
        } catch (Exception e) {
            System.err.println("Error updating preview: " + e.getMessage());
            e.printStackTrace();
            txtPreview.setText("Erreur lors du calcul du montant restant: " + e.getMessage());
        }
    }

    @FXML
    private void handleSend() {
        // Validate
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

        // Check SMS balance
        if (smsBalance < 0) {
            showError("Impossible de vérifier le solde SMS. Veuillez réessayer.");
            return;
        }

        if (smsBalance < recipients.size()) {
            showError(String.format(
                "Solde insuffisant. Vous avez %d SMS disponibles mais %d messages à envoyer.",
                smsBalance, recipients.size()
            ));
            return;
        }

        // Confirm send
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
        // Disable send button
        btnSend.setDisable(true);

        // Send in background thread
        new Thread(() -> {
            int successCount = 0;
            int failureCount = 0;

            // Get total amount once for all messages
            double totalAmount = 0.0;
            try {
                Double total = paymentGroupService.getTotalAmountByEntity(entityType, entityId);
                totalAmount = total != null ? total : 0.0;
            } catch (Exception e) {
                System.err.println("Error getting total amount: " + e.getMessage());
            }

            for (Member member : recipients) {
                try {
                    double remainingAmount = paymentGroupService.calculateRemainingAmount(
                        member.getId(), entityType, entityId);

                    String message = smsService.replaceVariables(
                        txtMessage.getText(),
                        member.getFirstName(),
                        member.getLastName(),
                        remainingAmount,
                        totalAmount,
                        entityName
                    );

                    boolean sent = smsService.sendSMS(member.getPhone(), message);
                    if (sent) {
                        successCount++;
                    } else {
                        failureCount++;
                    }

                    // Small delay between messages to avoid overloading the API
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

    private Stage getStage() {
        return (Stage) cbGroup.getScene().getWindow();
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
