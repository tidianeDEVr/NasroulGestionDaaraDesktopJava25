package com.nasroul.controller;

import com.nasroul.dao.SmsLogDAO;
import com.nasroul.model.Event;
import com.nasroul.model.Group;
import com.nasroul.model.Member;
import com.nasroul.model.Project;
import com.nasroul.service.*;
import com.nasroul.util.PhoneNumberValidator;
import com.nasroul.util.SmsSegmentCalculator;
import com.nasroul.ui.Forms;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class SMSCampaignDialogController {

    @FXML private Label lblEntityInfo;
    @FXML private ComboBox<Group> cbGroup;
    @FXML private Label lblRecipientCount;
    @FXML private Label lblSMSBalance;
    @FXML private javafx.scene.layout.FlowPane variablesPane;
    @FXML private TextArea txtMessage;
    @FXML private Label lblCharCount;
    @FXML private TextArea txtPreview;
    @FXML private Button btnSend;
    @FXML private VBox progressContainer;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblProgress;

    private final GroupService groupService;
    private final MemberService memberService;
    private final ContributionService contributionService;
    private final ContributionCalculator calculator;
    private final EventService eventService;
    private final ProjectService projectService;
    private final SMSService smsService;
    private final SmsLogDAO smsLogDAO;

    private String entityType;
    private Integer entityId;
    private String entityName;
    private String entityDeadline;

    /** Destinataire retenu : membre + numéro normalisé (+221XXXXXXXXX). */
    private record Recipient(Member member, String phone) {
    }

    /** Message prêt à partir pour un destinataire, avec son coût en segments. */
    private record PreparedMessage(Recipient recipient, String text, int segments) {
    }

    private List<Recipient> recipients;
    private volatile int smsBalance = -1;
    private volatile boolean cancelRequested = false;
    private volatile boolean sending = false;

    /** Campagne à reprendre après un échec partiel (les SENT seront sautés). */
    private String retryCampaignId = null;

    /** Restriction optionnelle aux retardataires (vue Recouvrement). */
    private Set<Integer> restrictToMemberIds = null;

    /**
     * Cache des situations financières du groupe sélectionné, chargé en fond
     * par {@link #refreshStatusCache()} : ni l'aperçu ni la préparation de
     * l'envoi ne refont de requête par membre.
     */
    private java.util.Map<Integer, ContributionCalculator.MemberRecoveryRow> statusByMember = java.util.Map.of();
    private double cachedGlobalRemaining = 0.0;
    private Double cachedTarget = null;
    /** Génération du cache : ignore les résultats obsolètes après un changement de groupe. */
    private int cacheGeneration = 0;
    private boolean statusCacheReady = false;

    public SMSCampaignDialogController() {
        this.groupService = new GroupService();
        this.memberService = new MemberService();
        this.contributionService = new ContributionService();
        this.calculator = new ContributionCalculator();
        this.eventService = new EventService();
        this.projectService = new ProjectService();
        this.smsService = new SMSService();
        this.smsLogDAO = new SmsLogDAO();
    }

    public void initialize() {
        loadGroups();
        checkSMSBalance();
        buildVariableChips();

        cbGroup.setOnAction(e -> updateRecipients());
        txtMessage.textProperty().addListener((obs, old, newVal) -> {
            retryCampaignId = null; // le message a changé : nouvelle campagne
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

    /**
     * Pré-sélectionne le groupe et verrouille le choix (campagne lancée
     * depuis la vue Recouvrement d'une fiche Collecte).
     */
    public void preselectGroup(int groupId) {
        cbGroup.getItems().stream()
            .filter(g -> g.getId() != null && g.getId() == groupId)
            .findFirst()
            .ifPresent(g -> {
                cbGroup.setValue(g);
                cbGroup.setDisable(true);
                updateRecipients();
            });
    }

    /** Restreint les destinataires à une liste de membres (retardataires). */
    public void restrictRecipients(Set<Integer> memberIds) {
        this.restrictToMemberIds = memberIds;
        if (lblEntityInfo != null && memberIds != null) {
            lblEntityInfo.setText(lblEntityInfo.getText()
                + " — retardataires uniquement (" + memberIds.size() + ")");
        }
        if (cbGroup.getValue() != null) {
            updateRecipients();
        }
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

    /**
     * Restant global de l'entité = montant attendu (dérivé des objectifs par
     * groupe × effectifs) − total déjà collecté. Une seule définition de la
     * cible, la même que le dashboard.
     */
    private double calculateGlobalRemaining() {
        try {
            Double totalCollected = contributionService.getTotalByEntity(entityType, entityId);
            double collected = totalCollected != null ? totalCollected : 0.0;
            double target = calculator.expectedForEntity(entityType, entityId);
            return Math.max(0.0, target - collected);
        } catch (SQLException e) {
            System.err.println("Error calculating global remaining: " + e.getMessage());
            return 0.0;
        }
    }

    /** Variables cliquables : un clic insère la variable à la position du curseur. */
    private void buildVariableChips() {
        List<String> variables = List.of(
            "{prenom}", "{nom}", "{montant_restant}", "{montant_paye}", "{montant_total}",
            "{montant_cible_restant}", "{nom_evenement}", "{nom_projet}", "{nom_groupe}", "{date_echeance}");
        for (String variable : variables) {
            Label chip = new Label(variable);
            chip.getStyleClass().addAll("badge", "badge-lime");
            chip.setStyle("-fx-cursor: hand;");
            chip.setTooltip(new Tooltip("Cliquer pour insérer " + variable + " dans le message"));
            chip.setOnMouseClicked(e -> {
                txtMessage.insertText(txtMessage.getCaretPosition(), variable);
                txtMessage.requestFocus();
            });
            variablesPane.getChildren().add(chip);
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
                        (smsBalance > 100 ? "#2E7D32;" : smsBalance > 20 ? "#8A5A00;" : "#B3261E;"));
                } else {
                    lblSMSBalance.setText("Erreur");
                    lblSMSBalance.setStyle("-fx-font-weight: bold; -fx-text-fill: #B3261E; -fx-cursor: hand;");
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
        retryCampaignId = null; // le groupe a changé : nouvelle campagne
        Group selectedGroup = cbGroup.getValue();
        if (selectedGroup == null) {
            recipients = null;
            lblRecipientCount.setText("0");
            lblRecipientCount.setTooltip(null);
            updatePreview();
            return;
        }

        try {
            List<Member> groupMembers = memberService.getActiveMembers().stream()
                .filter(m -> m.getGroupIds() != null && m.getGroupIds().contains(selectedGroup.getId()))
                .filter(m -> restrictToMemberIds == null || restrictToMemberIds.contains(m.getId()))
                .toList();

            List<Recipient> selected = new ArrayList<>();
            List<String> noPhone = new ArrayList<>();
            List<String> invalidPhone = new ArrayList<>();
            List<String> duplicatePhone = new ArrayList<>();
            Set<String> seenPhones = new LinkedHashSet<>();

            for (Member m : groupMembers) {
                if (m.getPhone() == null || m.getPhone().trim().isEmpty()) {
                    noPhone.add(m.getFullName());
                    continue;
                }
                Optional<String> normalized = PhoneNumberValidator.normalize(m.getPhone());
                if (normalized.isEmpty()) {
                    invalidPhone.add(m.getFullName() + " (" + m.getPhone() + ")");
                    continue;
                }
                if (!seenPhones.add(normalized.get())) {
                    duplicatePhone.add(m.getFullName() + " (" + normalized.get() + ")");
                    continue;
                }
                selected.add(new Recipient(m, normalized.get()));
            }

            recipients = selected;

            int excluded = noPhone.size() + invalidPhone.size() + duplicatePhone.size();
            if (excluded == 0) {
                lblRecipientCount.setText(String.valueOf(selected.size()));
                lblRecipientCount.setTooltip(null);
            } else {
                lblRecipientCount.setText(selected.size() + " (" + excluded + " exclu" + (excluded > 1 ? "s" : "") + ")");
                StringBuilder detail = new StringBuilder();
                if (!noPhone.isEmpty()) {
                    detail.append("Sans téléphone : ").append(String.join(", ", noPhone));
                }
                if (!invalidPhone.isEmpty()) {
                    if (detail.length() > 0) detail.append("\n");
                    detail.append("Numéro invalide : ").append(String.join(", ", invalidPhone));
                }
                if (!duplicatePhone.isEmpty()) {
                    if (detail.length() > 0) detail.append("\n");
                    detail.append("Numéro en double (déjà destinataire) : ").append(String.join(", ", duplicatePhone));
                }
                lblRecipientCount.setTooltip(new Tooltip(detail.toString()));
            }
            refreshStatusCache(selectedGroup);
        } catch (SQLException e) {
            System.err.println("Error loading members: " + e.getMessage());
            showError("Erreur lors du chargement des membres: " + e.getMessage());
        }
    }

    /**
     * Recharge en arrière-plan les montants du groupe (une requête agrégée
     * pour tout le groupe + le restant global), puis rafraîchit l'aperçu.
     */
    private void refreshStatusCache(Group group) {
        // Le bouton Envoyer attend que les montants du groupe soient chargés :
        // sans cette garde, un clic rapide après un changement de groupe
        // enverrait des SMS construits sur le cache de l'ancien groupe (ou vide)
        statusCacheReady = false;
        btnSend.setDisable(true);
        final int generation = ++cacheGeneration;
        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            java.util.Map<Integer, ContributionCalculator.MemberRecoveryRow> byMember;
            double globalRemaining;
            Double target;

            @Override
            protected Void call() throws Exception {
                List<ContributionCalculator.MemberRecoveryRow> rows =
                    calculator.recoveryForGroup(entityType, entityId, group.getId());
                byMember = new java.util.HashMap<>();
                target = null;
                for (var row : rows) {
                    byMember.put(row.memberId(), row);
                    if (row.targetDefined()) {
                        target = row.targetPerMember();
                    }
                }
                globalRemaining = calculateGlobalRemaining();
                return null;
            }

            @Override
            protected void succeeded() {
                if (generation != cacheGeneration) {
                    return; // résultat obsolète : un autre groupe a été choisi depuis
                }
                statusByMember = byMember;
                cachedGlobalRemaining = globalRemaining;
                cachedTarget = target;
                statusCacheReady = true;
                if (!sending) {
                    btnSend.setDisable(false);
                }
                updatePreview();
            }

            @Override
            protected void failed() {
                getException().printStackTrace();
                if (generation == cacheGeneration && !sending) {
                    btnSend.setDisable(false);
                }
            }
        };
        Thread thread = new Thread(task, "sms-status-cache");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateCharCount() {
        String text = Forms.raw(txtMessage);
        int charCount = text.length();
        int segments = SmsSegmentCalculator.countSegments(text);
        String encoding = SmsSegmentCalculator.isGsm7(text) ? "" : " — caractères spéciaux (coût x2)";
        lblCharCount.setText(charCount + " caractères ≈ " + segments + " SMS par destinataire" + encoding);
    }

    /**
     * Construit le message final d'un destinataire. Tous les montants viennent
     * de ContributionCalculator : payé = somme réelle des cotisations payées,
     * restant = cible − payé (jamais l'inverse).
     */
    private String buildMessage(String template, Recipient recipient, Group group) {
        ContributionCalculator.MemberRecoveryRow status = statusByMember.get(recipient.member().getId());
        double paid = status != null ? status.paid() : 0.0;
        double remaining = status != null ? status.remaining() : 0.0;
        double target = (status != null && status.targetDefined()) ? status.targetPerMember() : 0.0;

        return smsService.replaceVariables(
            template,
            recipient.member().getFirstName(),
            recipient.member().getLastName(),
            remaining,
            target,
            entityName,
            paid,
            cachedGlobalRemaining,
            group.getName(),
            entityDeadline
        );
    }

    private void updatePreview() {
        if (recipients == null || recipients.isEmpty()) {
            txtPreview.setText("Sélectionnez un groupe pour voir l'aperçu");
            return;
        }

        if (Forms.text(txtMessage).isEmpty()) {
            txtPreview.setText("Saisissez un message pour voir l'aperçu");
            return;
        }

        try {
            String preview = buildMessage(Forms.text(txtMessage), recipients.get(0), cbGroup.getValue());
            txtPreview.setText(preview);
        } catch (Exception e) {
            System.err.println("Error updating preview: " + e.getMessage());
            txtPreview.setText("Erreur lors du calcul du montant restant: " + e.getMessage());
        }
    }

    private boolean messageUsesAmountVariables(String template) {
        return template.contains("{montant_restant}")
            || template.contains("{montant_paye}")
            || template.contains("{montant_total}");
    }

    @FXML
    private void handleSend() {
        if (cbGroup.getValue() == null) {
            showError("Veuillez sélectionner un groupe");
            return;
        }

        final String template = Forms.text(txtMessage);
        if (template.isEmpty()) {
            showError("Veuillez saisir un message");
            return;
        }

        if (recipients == null || recipients.isEmpty()) {
            showError("Aucun destinataire trouvé dans ce groupe avec un numéro de téléphone valide");
            return;
        }

        if (!statusCacheReady) {
            showWarning("Chargement en cours",
                "Les montants du groupe sont en cours de calcul, réessayez dans un instant.");
            return;
        }

        final Group group = cbGroup.getValue();

        // Pas d'objectif de cotisation défini pour ce groupe : bloquer si le
        // message contient des montants (sinon tout le groupe recevrait
        // « payé 0, reste 0 »)
        if (cachedTarget == null && messageUsesAmountVariables(template)) {
            showError(String.format(
                "Aucun objectif de cotisation n'est défini pour le groupe « %s » sur « %s ».\n\n" +
                "Votre message utilise des variables de montant ({montant_restant}, {montant_paye}, {montant_total}) : " +
                "les SMS partiraient avec des montants à 0.\n\n" +
                "Définissez d'abord l'objectif dans l'onglet Objectifs de la collecte, " +
                "ou retirez les variables de montant du message.",
                group.getName(), entityName));
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

        // Construire tous les messages finaux pour connaître le coût réel en
        // segments (un message > 160 caractères ou accentué coûte plusieurs
        // crédits : comparer le solde au nombre de destinataires ne suffit pas)
        // Reprise d'une campagne : exclure d'emblée les destinataires déjà
        // servis, pour que le contrôle de solde et la confirmation ne portent
        // que sur le restant
        Set<String> alreadySentPhones = Set.of();
        if (retryCampaignId != null) {
            try {
                alreadySentPhones = smsLogDAO.findSentPhones(retryCampaignId);
            } catch (SQLException e) {
                showError("Impossible de lire le journal de la campagne à reprendre : " + e.getMessage()
                    + "\n\nReprise annulée pour éviter tout double envoi.");
                return;
            }
        }

        final List<PreparedMessage> prepared = new ArrayList<>();
        int totalSegments = 0;
        for (Recipient recipient : recipients) {
            if (alreadySentPhones.contains(recipient.phone())) {
                continue;
            }
            String text = buildMessage(template, recipient, group);
            int segments = SmsSegmentCalculator.countSegments(text);
            prepared.add(new PreparedMessage(recipient, text, segments));
            totalSegments += segments;
        }

        if (prepared.isEmpty()) {
            showInfo("Rien à envoyer", "Tous les destinataires de cette campagne ont déjà reçu leur SMS.");
            retryCampaignId = null;
            return;
        }

        if (smsBalance < totalSegments) {
            showError(String.format(
                "Solde insuffisant. Cette campagne coûte %d SMS (%d destinataires, messages de %s segment(s)) " +
                "mais votre solde est de %d SMS.",
                totalSegments, prepared.size(),
                prepared.stream().mapToInt(PreparedMessage::segments).max().orElse(1) > 1 ? "1 à " +
                    prepared.stream().mapToInt(PreparedMessage::segments).max().orElse(1) : "1",
                smsBalance
            ));
            return;
        }

        final int finalTotalSegments = totalSegments;
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmer l'envoi");
        confirmation.setHeaderText(String.format("Envoyer %d SMS ?", prepared.size()));
        confirmation.setContentText(String.format(
            "Destinataires : %d\nCoût réel : %d SMS (segments)\nSolde actuel : %d SMS\nSolde après envoi : %d SMS\n\nConfirmer ?",
            prepared.size(), finalTotalSegments, smsBalance, smsBalance - finalTotalSegments
        ));

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                sendCampaign(prepared, group);
            }
        });
    }

    private void sendCampaign(List<PreparedMessage> prepared, Group group) {
        btnSend.setDisable(true);
        progressContainer.setVisible(true);
        progressContainer.setManaged(true);
        progressBar.setProgress(0);
        lblProgress.setText("0/" + prepared.size());
        cancelRequested = false;
        sending = true;

        // Reprise d'une campagne partiellement envoyée : réutiliser son
        // identifiant pour que les destinataires déjà servis soient sautés
        final String campaignId = retryCampaignId != null ? retryCampaignId : UUID.randomUUID().toString();
        final int total = prepared.size();

        new Thread(() -> {
            int successCount = 0;
            int failureCount = 0;
            int skippedCount = 0;
            List<String> failedNames = new ArrayList<>();

            // Le jeu des "déjà servis" est vital pour l'anti-doublon : si sa
            // lecture échoue sur une reprise, on n'envoie RIEN plutôt que de
            // risquer un double envoi
            Set<String> alreadySent;
            try {
                alreadySent = smsLogDAO.findSentPhones(campaignId);
            } catch (SQLException e) {
                e.printStackTrace();
                sending = false;
                Platform.runLater(() -> {
                    btnSend.setDisable(false);
                    progressContainer.setVisible(false);
                    progressContainer.setManaged(false);
                    showError("Impossible de lire le journal des envois : " + e.getMessage()
                        + "\n\nEnvoi annulé pour éviter tout double envoi.");
                });
                return;
            }
            try {
                smsLogDAO.insertPendingBatch(campaignId, entityType, entityId, group.getId(),
                    prepared.stream().map(pm -> new SmsLogDAO.PendingEntry(
                        pm.recipient().member().getId(), pm.recipient().phone(),
                        pm.text(), pm.segments())).toList());
            } catch (SQLException e) {
                // journal incomplet : l'envoi reste possible, la reprise se
                // basera sur les SENT réellement enregistrés
                System.err.println("Error initializing SMS journal: " + e.getMessage());
            }

            for (int i = 0; i < prepared.size(); i++) {
                if (cancelRequested) {
                    break;
                }

                PreparedMessage pm = prepared.get(i);
                Member member = pm.recipient().member();
                final int current = i + 1;

                if (alreadySent.contains(pm.recipient().phone())) {
                    // Déjà servi lors d'une tentative précédente : jamais de double envoi
                    skippedCount++;
                } else {
                    try {
                        SMSService.SendResult result = smsService.sendSMSDetailed(pm.recipient().phone(), pm.text());
                        if (result.success()) {
                            successCount++;
                            smsLogDAO.markSent(campaignId, pm.recipient().phone(), result.providerResponse());
                        } else {
                            failureCount++;
                            failedNames.add(member.getFullName());
                            smsLogDAO.markFailed(campaignId, pm.recipient().phone(), result.errorMessage());
                        }
                    } catch (Exception e) {
                        failureCount++;
                        failedNames.add(member.getFullName());
                        System.err.println("Error sending SMS to " + member.getFullName() + ": " + e.getMessage());
                        try {
                            smsLogDAO.markFailed(campaignId, pm.recipient().phone(), e.getMessage());
                        } catch (SQLException ignored) {
                            // le journal ne doit jamais interrompre la campagne
                        }
                    }

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                Platform.runLater(() -> {
                    progressBar.setProgress((double) current / total);
                    lblProgress.setText(current + "/" + total);
                });
            }

            final int finalSuccess = successCount;
            final int finalFailure = failureCount;
            final int finalSkipped = skippedCount;
            final boolean wasCancelled = cancelRequested;
            final List<String> finalFailedNames = failedNames;

            sending = false;

            Platform.runLater(() -> {
                btnSend.setDisable(false);
                progressContainer.setVisible(false);
                progressContainer.setManaged(false);
                checkSMSBalance();

                if (wasCancelled) {
                    retryCampaignId = campaignId;
                    showWarning("Envoi interrompu", String.format(
                        "Envoi interrompu.\nEnvoyés : %d\nÉchecs : %d\n\n" +
                        "Cliquez à nouveau sur Envoyer pour reprendre : les destinataires déjà servis seront sautés.",
                        finalSuccess, finalFailure));
                } else if (finalFailure == 0) {
                    retryCampaignId = null;
                    String skippedNote = finalSkipped > 0
                        ? String.format("\n(%d déjà envoyés lors de la tentative précédente)", finalSkipped) : "";
                    showInfo("Succès", String.format("Tous les SMS ont été envoyés avec succès ! (%d)%s",
                        finalSuccess + finalSkipped, skippedNote));
                    closeDialog();
                } else {
                    retryCampaignId = campaignId;
                    String names = String.join(", ", finalFailedNames);
                    showWarning("Envoi partiel", String.format(
                        "Envoi terminé.\nSuccès : %d\nÉchecs : %d\n\nEn échec : %s\n\n" +
                        "Cliquez à nouveau sur Envoyer pour réessayer uniquement les échecs " +
                        "(les destinataires déjà servis ne recevront pas de doublon).",
                        finalSuccess, finalFailure, names));
                }
            });
        }).start();
    }

    @FXML
    private void handleCancel() {
        if (sending) {
            // Arrêter proprement la boucle d'envoi ; le journal garde la trace
            // de ce qui est parti et la campagne pourra reprendre sans doublon
            cancelRequested = true;
            return;
        }
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) cbGroup.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        com.nasroul.ui.Dialogs.error(ownerWindow(), "Erreur", message);
    }

    private void showWarning(String title, String message) {
        com.nasroul.ui.Dialogs.warn(ownerWindow(), title, message);
    }

    private void showInfo(String title, String message) {
        com.nasroul.ui.Dialogs.info(ownerWindow(), title, message);
    }

    private javafx.stage.Window ownerWindow() {
        return cbGroup.getScene() != null ? cbGroup.getScene().getWindow() : null;
    }
}
