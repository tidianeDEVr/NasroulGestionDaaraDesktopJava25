package com.nasroul.controller;

import com.nasroul.model.PaymentGroup;
import com.nasroul.service.ContributionCalculator;
import com.nasroul.service.ContributionCalculator.MemberRecoveryRow;
import com.nasroul.service.PaymentGroupService;
import com.nasroul.ui.Dialogs;
import com.nasroul.ui.ThemeManager;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Onglet Recouvrement d'une fiche Collecte : qui a payé, qui est en retard,
 * avec « SMS aux retardataires » et « Collecte en masse ».
 * Toutes les valeurs viennent de ContributionCalculator (source unique).
 */
public class RecoveryController {

    @FXML private ComboBox<PaymentGroup> cbGroup;
    @FXML private Label lblObjective;
    @FXML private Button btnBulk;
    @FXML private Button btnSms;
    @FXML private Label lblGroupExpected;
    @FXML private Label lblGroupPaid;
    @FXML private Label lblGroupRemaining;
    @FXML private Label lblGroupRate;
    @FXML private ProgressBar groupProgress;
    @FXML private TableView<MemberRecoveryRow> recoveryTable;
    @FXML private TableColumn<MemberRecoveryRow, String> colMember;
    @FXML private TableColumn<MemberRecoveryRow, String> colTarget;
    @FXML private TableColumn<MemberRecoveryRow, String> colPaid;
    @FXML private TableColumn<MemberRecoveryRow, String> colRemaining;
    @FXML private TableColumn<MemberRecoveryRow, MemberRecoveryRow> colRecoveryStatus;
    @FXML private Label lblPlaceholder;

    private final PaymentGroupService paymentGroupService = new PaymentGroupService();
    private final ContributionCalculator calculator = new ContributionCalculator();
    private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.FRANCE);

    private final ObservableList<MemberRecoveryRow> rows = FXCollections.observableArrayList();

    private String entityType;
    private Integer entityId;
    private String entityName = "";
    private Runnable onDataChanged = () -> { };

    public void setOnDataChanged(Runnable onDataChanged) {
        this.onDataChanged = onDataChanged;
    }

    @FXML
    public void initialize() {
        recoveryTable.setItems(rows);

        cbGroup.setCellFactory(lv -> groupCell());
        cbGroup.setButtonCell(groupCell());
        cbGroup.setOnAction(e -> loadRows());

        colMember.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().fullName()));
        colTarget.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().targetDefined() ? numberFormat.format(d.getValue().targetPerMember()) : "—"));
        colPaid.setCellValueFactory(d -> new SimpleStringProperty(numberFormat.format(d.getValue().paid())));
        colRemaining.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().targetDefined() ? numberFormat.format(d.getValue().remaining()) : "—"));

        colRecoveryStatus.setCellValueFactory(d -> Bindings.createObjectBinding(d::getValue));
        colRecoveryStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(MemberRecoveryRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                Label badge;
                if (!row.targetDefined()) {
                    badge = CollecteController.badge("Sans objectif");
                } else if (row.surplus() > 0) {
                    badge = CollecteController.badge("Surplus +" + numberFormat.format(row.surplus()), "badge-lime");
                } else if (row.remaining() > 0) {
                    badge = CollecteController.badge("En retard", "badge-warning");
                } else {
                    badge = CollecteController.badge("Payé", "badge-success");
                }
                setGraphic(badge);
            }
        });

        btnSms.setDisable(true);
        btnBulk.setDisable(true);
    }

    private ListCell<PaymentGroup> groupCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(PaymentGroup pg, boolean empty) {
                super.updateItem(pg, empty);
                setText(empty || pg == null ? "" : pg.getGroupName());
            }
        };
    }

    public void setEntity(String entityType, int entityId) {
        this.entityType = entityType;
        this.entityId = entityId;
        loadGroups(true);
    }

    /** Recharge groupes + lignes (après une collecte en masse, un retour...). */
    public void reload() {
        loadGroups(false);
    }

    private void loadGroups(boolean selectFirst) {
        Task<List<PaymentGroup>> task = new Task<>() {
            @Override
            protected List<PaymentGroup> call() throws Exception {
                return paymentGroupService.getPaymentGroupsByEntity(entityType, entityId);
            }
        };
        task.setOnSucceeded(e -> {
            List<PaymentGroup> groups = task.getValue();
            Integer previous = cbGroup.getValue() != null ? cbGroup.getValue().getGroupId() : null;
            cbGroup.getItems().setAll(groups);
            if (groups.isEmpty()) {
                lblObjective.setText("Aucun objectif de cotisation défini pour cette collecte");
                lblPlaceholder.setText("Définissez d'abord un objectif par groupe (onglet Objectifs) "
                    + "pour suivre le recouvrement.");
                rows.clear();
                resetStats();
            } else {
                PaymentGroup toSelect = groups.stream()
                    .filter(g -> previous != null && g.getGroupId().equals(previous))
                    .findFirst()
                    .orElse(selectFirst || previous == null ? groups.get(0) : groups.get(0));
                cbGroup.setValue(toSelect);
                loadRows();
            }
        });
        task.setOnFailed(e -> task.getException().printStackTrace());
        Thread thread = new Thread(task, "recovery-groups");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadRows() {
        PaymentGroup selected = cbGroup.getValue();
        if (selected == null) {
            return;
        }
        Task<List<MemberRecoveryRow>> task = new Task<>() {
            double expected;

            @Override
            protected List<MemberRecoveryRow> call() throws Exception {
                expected = calculator.expectedForGroup(entityType, entityId, selected.getGroupId());
                return calculator.recoveryForGroup(entityType, entityId, selected.getGroupId());
            }

            @Override
            protected void succeeded() {
                List<MemberRecoveryRow> list = getValue();
                rows.setAll(list);

                lblObjective.setText("Objectif : " + numberFormat.format(selected.getAmount())
                    + " CFA / membre · " + list.size() + " membre" + (list.size() > 1 ? "s" : ""));

                double paid = list.stream().mapToDouble(MemberRecoveryRow::paid).sum();
                double remaining = list.stream().mapToDouble(MemberRecoveryRow::remaining).sum();
                double credited = Math.max(0, expected - remaining);

                lblGroupExpected.setText(numberFormat.format(expected) + " CFA");
                lblGroupPaid.setText(numberFormat.format(paid) + " CFA");
                lblGroupRemaining.setText(numberFormat.format(remaining) + " CFA");
                double ratio = expected > 0 ? Math.min(1.0, credited / expected) : 0;
                lblGroupRate.setText(Math.round(ratio * 100) + " %");
                groupProgress.setProgress(ratio);

                long late = list.stream().filter(MemberRecoveryRow::isLate).count();
                btnSms.setText(late > 0 ? "SMS aux retardataires (" + late + ")" : "SMS aux retardataires");
                btnSms.setDisable(late == 0);
                btnBulk.setDisable(false);
            }

            @Override
            protected void failed() {
                getException().printStackTrace();
                Dialogs.error(window(), "Erreur", "Impossible de charger le recouvrement.");
            }
        };
        Thread thread = new Thread(task, "recovery-rows");
        thread.setDaemon(true);
        thread.start();
    }

    private void resetStats() {
        lblGroupExpected.setText("—");
        lblGroupPaid.setText("—");
        lblGroupRemaining.setText("—");
        lblGroupRate.setText("—");
        groupProgress.setProgress(0);
        btnSms.setDisable(true);
        btnBulk.setDisable(true);
    }

    // ------------------------------------------------------------- Actions

    @FXML
    private void handleSmsLate() {
        PaymentGroup selected = cbGroup.getValue();
        if (selected == null) {
            return;
        }
        Set<Integer> lateIds = rows.stream()
            .filter(MemberRecoveryRow::isLate)
            .map(MemberRecoveryRow::memberId)
            .collect(Collectors.toSet());
        if (lateIds.isEmpty()) {
            return;
        }
        try {
            Dialogs.Modal<SMSCampaignDialogController> modal = Dialogs.openModal(
                "/fxml/SMSCampaignDialog.fxml",
                "SMS aux retardataires — " + selected.getGroupName(), window());
            modal.controller().setEntity(entityType, entityId, entityName());
            modal.controller().restrictRecipients(lateIds);
            modal.controller().preselectGroup(selected.getGroupId());
            modal.stage().showAndWait();
        } catch (IOException e) {
            Dialogs.error(window(), "Erreur", "Impossible d'ouvrir la campagne SMS : " + e.getMessage());
        }
    }

    @FXML
    private void handleBulkCollection() {
        PaymentGroup selected = cbGroup.getValue();
        if (selected == null) {
            return;
        }
        try {
            Dialogs.Modal<BulkCollectionController> modal = Dialogs.openModal(
                "/fxml/BulkCollectionView.fxml",
                "Collecte en masse — " + selected.getGroupName(), window());
            modal.controller().setContext(entityType, entityId, selected.getGroupId(),
                selected.getGroupName(), List.copyOf(rows));
            modal.stage().setResizable(true);
            modal.stage().showAndWait();

            if (modal.controller().isSaved()) {
                loadRows();
                onDataChanged.run();
            }
        } catch (IOException e) {
            Dialogs.error(window(), "Erreur", "Impossible d'ouvrir la collecte en masse : " + e.getMessage());
        }
    }

    private String entityName() {
        PaymentGroup selected = cbGroup.getValue();
        return selected != null && selected.getEntityName() != null ? selected.getEntityName() : "";
    }

    private javafx.stage.Window window() {
        return recoveryTable.getScene() != null ? recoveryTable.getScene().getWindow() : null;
    }
}
