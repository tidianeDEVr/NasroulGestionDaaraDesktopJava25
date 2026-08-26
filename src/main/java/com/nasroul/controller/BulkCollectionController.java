package com.nasroul.controller;

import com.nasroul.dao.ContributionDAO;
import com.nasroul.model.Contribution;
import com.nasroul.service.ContributionCalculator.MemberRecoveryRow;
import com.nasroul.ui.Dialogs;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pointage par lot des cotisations d'un groupe pour une collecte :
 * cases à cocher, montant pré-rempli avec le restant dû, méthode par ligne,
 * enregistrement en une transaction (ContributionDAO.createBatch).
 */
public class BulkCollectionController {

    private static final List<String> METHODS = List.of("Espèces", "Wave", "Orange Money", "Virement");

    /** Ligne éditable du pointage. */
    public static final class BulkRow {
        final MemberRecoveryRow source;
        final BooleanProperty selected = new SimpleBooleanProperty(false);
        final SimpleStringProperty amount = new SimpleStringProperty("");
        final SimpleStringProperty method = new SimpleStringProperty(METHODS.get(0));

        BulkRow(MemberRecoveryRow source) {
            this.source = source;
        }

        boolean alreadyPaid() {
            return source.targetDefined() && source.remaining() <= 0;
        }
    }

    @FXML private Label lblContext;
    @FXML private DatePicker dpDate;
    @FXML private ComboBox<String> cbDefaultMethod;
    @FXML private Hyperlink linkCheckLate;
    @FXML private TableView<BulkRow> bulkTable;
    @FXML private TableColumn<BulkRow, BulkRow> colCheck;
    @FXML private TableColumn<BulkRow, String> colMember;
    @FXML private TableColumn<BulkRow, String> colRemaining;
    @FXML private TableColumn<BulkRow, BulkRow> colAmount;
    @FXML private TableColumn<BulkRow, BulkRow> colMethod;
    @FXML private Label lblSummary;
    @FXML private Button btnSave;

    private final ContributionDAO contributionDAO = new ContributionDAO();
    private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.FRANCE);
    private final ObservableList<BulkRow> rows = FXCollections.observableArrayList();

    private String entityType;
    private Integer entityId;
    private Integer groupId;
    private boolean saved = false;
    private boolean saving = false;

    public boolean isSaved() {
        return saved;
    }

    @FXML
    public void initialize() {
        dpDate.setValue(LocalDate.now());
        cbDefaultMethod.getItems().setAll(METHODS);
        cbDefaultMethod.setValue(METHODS.get(0));
        // Changer la méthode par défaut s'applique à TOUTES les lignes non
        // encore payées (cochées comprises : c'est elles qu'on enregistre)
        cbDefaultMethod.setOnAction(e -> rows.forEach(r -> {
            if (!r.alreadyPaid()) {
                r.method.set(cbDefaultMethod.getValue());
            }
        }));

        bulkTable.setItems(rows);

        colCheck.setCellValueFactory(d -> Bindings.createObjectBinding(d::getValue));
        colCheck.setCellFactory(col -> new TableCell<>() {
            private final CheckBox check = new CheckBox();
            private BulkRow bound;

            @Override
            protected void updateItem(BulkRow row, boolean empty) {
                super.updateItem(row, empty);
                if (bound != null) {
                    check.selectedProperty().unbindBidirectional(bound.selected);
                    bound = null;
                }
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                bound = row;
                check.selectedProperty().bindBidirectional(row.selected);
                check.setDisable(row.alreadyPaid());
                setGraphic(check);
            }
        });

        colMember.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().source.fullName()));
        colRemaining.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().source.targetDefined()
                ? numberFormat.format(d.getValue().source.remaining()) + " CFA"
                : "—"));

        colAmount.setCellValueFactory(d -> Bindings.createObjectBinding(d::getValue));
        colAmount.setCellFactory(col -> new TableCell<>() {
            private final TextField field = new TextField();
            private BulkRow bound;

            {
                field.setPrefWidth(100);
            }

            @Override
            protected void updateItem(BulkRow row, boolean empty) {
                super.updateItem(row, empty);
                if (bound != null) {
                    field.textProperty().unbindBidirectional(bound.amount);
                    bound = null;
                }
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                if (row.alreadyPaid()) {
                    setGraphic(CollecteController.badge("Déjà payé", "badge-success"));
                    return;
                }
                bound = row;
                field.textProperty().bindBidirectional(row.amount);
                field.disableProperty().bind(row.selected.not());
                setGraphic(field);
            }
        });

        colMethod.setCellValueFactory(d -> Bindings.createObjectBinding(d::getValue));
        colMethod.setCellFactory(col -> new TableCell<>() {
            private final ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(METHODS));
            private BulkRow bound;

            {
                combo.setPrefWidth(140);
            }

            @Override
            protected void updateItem(BulkRow row, boolean empty) {
                super.updateItem(row, empty);
                if (bound != null) {
                    combo.valueProperty().unbindBidirectional(bound.method);
                    bound = null;
                }
                if (empty || row == null || row.alreadyPaid()) {
                    setGraphic(null);
                    return;
                }
                bound = row;
                combo.valueProperty().bindBidirectional(row.method);
                combo.disableProperty().bind(row.selected.not());
                setGraphic(combo);
            }
        });
    }

    public void setContext(String entityType, int entityId, int groupId,
                           String groupName, List<MemberRecoveryRow> recoveryRows) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.groupId = groupId;
        lblContext.setText(groupName);

        rows.clear();
        for (MemberRecoveryRow source : recoveryRows) {
            BulkRow row = new BulkRow(source);
            if (source.targetDefined() && source.remaining() > 0) {
                row.amount.set(String.format(Locale.ROOT, "%.0f", source.remaining()));
            }
            row.method.set(cbDefaultMethod.getValue());
            row.selected.addListener((obs, old, val) -> updateSummary());
            row.amount.addListener((obs, old, val) -> updateSummary());
            rows.add(row);
        }
        updateSummary();
    }

    @FXML
    private void handleCheckAllLate() {
        rows.forEach(r -> {
            if (!r.alreadyPaid() && r.source.targetDefined() && r.source.remaining() > 0) {
                r.selected.set(true);
            }
        });
    }

    private List<BulkRow> selectedRows() {
        return rows.stream().filter(r -> r.selected.get()).toList();
    }

    private void updateSummary() {
        List<BulkRow> selected = selectedRows();
        double total = 0;
        for (BulkRow row : selected) {
            try {
                total += Double.parseDouble(row.amount.get().trim().replace(" ", ""));
            } catch (Exception ignored) {
                // montant invalide : signalé à l'enregistrement
            }
        }
        lblSummary.setText(selected.size() + " paiement" + (selected.size() > 1 ? "s" : "")
            + " sélectionné" + (selected.size() > 1 ? "s" : "")
            + " · total " + numberFormat.format(total) + " CFA");
        btnSave.setDisable(selected.isEmpty() || saving);
        btnSave.setText(selected.isEmpty() ? "Enregistrer"
            : "Enregistrer " + selected.size() + " paiement" + (selected.size() > 1 ? "s" : ""));
    }

    @FXML
    private void handleSave() {
        List<BulkRow> selected = selectedRows();
        if (selected.isEmpty()) {
            return;
        }

        List<Contribution> batch = new ArrayList<>();
        double total = 0;
        for (BulkRow row : selected) {
            double amount;
            try {
                amount = Double.parseDouble(row.amount.get().trim().replace(" ", ""));
            } catch (Exception e) {
                Dialogs.warn(window(), "Montant invalide",
                    "Le montant de " + row.source.fullName() + " n'est pas un nombre valide.");
                return;
            }
            if (amount <= 0) {
                Dialogs.warn(window(), "Montant invalide",
                    "Le montant de " + row.source.fullName() + " doit être supérieur à 0.");
                return;
            }
            total += amount;

            Contribution c = new Contribution();
            c.setMemberId(row.source.memberId());
            c.setEntityType(entityType);
            c.setEntityId(entityId);
            c.setGroupId(groupId);
            c.setAmount(amount);
            c.setDate(dpDate.getValue() != null ? dpDate.getValue() : LocalDate.now());
            c.setStatus("PAID");
            c.setPaymentMethod(toMethodCode(row.method.get()));
            batch.add(c);
        }

        boolean confirmed = Dialogs.confirm(window(), "Confirmer la collecte",
            "Enregistrer " + batch.size() + " paiement" + (batch.size() > 1 ? "s" : "")
                + " pour un total de " + numberFormat.format(total) + " CFA ?",
            "Chaque paiement sera enregistré comme cotisation « Payé » du "
                + (dpDate.getValue() != null ? dpDate.getValue() : LocalDate.now()) + ".");
        if (!confirmed) {
            return;
        }

        saving = true;
        btnSave.setDisable(true);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                contributionDAO.createBatch(batch);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            saved = true;
            close();
        });
        task.setOnFailed(e -> {
            saving = false;
            btnSave.setDisable(false);
            task.getException().printStackTrace();
            Dialogs.error(window(), "Erreur",
                "Aucun paiement n'a été enregistré (transaction annulée) : "
                    + task.getException().getMessage());
        });
        Thread thread = new Thread(task, "bulk-save");
        thread.setDaemon(true);
        thread.start();
    }

    private String toMethodCode(String label) {
        return switch (label) {
            case "Wave" -> "WAVE";
            case "Orange Money" -> "ORANGE_MONEY";
            case "Virement" -> "BANK_TRANSFER";
            default -> "CASH";
        };
    }

    @FXML
    private void handleCancel() {
        close();
    }

    private void close() {
        ((Stage) bulkTable.getScene().getWindow()).close();
    }

    private javafx.stage.Window window() {
        return bulkTable.getScene() != null ? bulkTable.getScene().getWindow() : null;
    }
}
