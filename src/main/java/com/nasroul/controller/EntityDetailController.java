package com.nasroul.controller;

import com.nasroul.dao.SmsLogDAO;
import com.nasroul.model.Event;
import com.nasroul.model.Project;
import com.nasroul.service.ContributionCalculator;
import com.nasroul.service.ContributionService;
import com.nasroul.service.EventService;
import com.nasroul.service.ProjectService;
import com.nasroul.ui.Dialogs;
import com.nasroul.ui.Phosphor;
import com.nasroul.ui.PhosphorIcon;
import com.nasroul.ui.ThemeManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Fiche d'une collecte (événement ou projet) : hero + onglets
 * Recouvrement / Cotisations / Objectifs / Dépenses / SMS.
 * Les onglets sont chargés paresseusement au premier affichage.
 */
public class EntityDetailController {

    @FXML private Label crumbName;
    @FXML private Label lblName;
    @FXML private Label lblStatus;
    @FXML private Label lblSubtitle;
    @FXML private Label lblCollected;
    @FXML private Label lblExpected;
    @FXML private ProgressBar heroProgress;
    @FXML private MenuButton btnActions;
    @FXML private TabPane tabPane;
    @FXML private Tab tabRecovery;
    @FXML private Tab tabContributions;
    @FXML private Tab tabTargets;
    @FXML private Tab tabExpenses;
    @FXML private Tab tabSms;

    private final EventService eventService = new EventService();
    private final ProjectService projectService = new ProjectService();
    private final ContributionService contributionService = new ContributionService();
    private final ContributionCalculator calculator = new ContributionCalculator();
    private final SmsLogDAO smsLogDAO = new SmsLogDAO();
    private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.FRANCE);

    private String entityType;
    private Integer entityId;
    private String entityName = "";

    private Runnable onBack = () -> { };
    private Runnable onChanged = () -> { };

    // Contrôleurs des onglets (chargés paresseusement)
    private RecoveryController recoveryController;
    private ContributionController contributionController;
    private PaymentGroupController paymentGroupController;
    private ExpenseController expenseController;
    private TableView<SmsLogDAO.CampaignSummary> smsTable;

    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }

    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    @FXML
    public void initialize() {
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, old, tab) -> {
            if (entityId != null) {
                ensureTabLoaded(tab);
            }
        });
    }

    public void setEntity(String entityType, int entityId) {
        this.entityType = entityType;
        this.entityId = entityId;
        refreshHero();

        // Re-cibler les onglets déjà chargés, puis revenir sur Recouvrement
        if (recoveryController != null) {
            recoveryController.setEntity(entityType, entityId);
        }
        if (contributionController != null) {
            contributionController.setEntityFilter(entityType, entityId);
        }
        if (paymentGroupController != null) {
            paymentGroupController.setEntityFilter(entityType, entityId);
        }
        if (expenseController != null) {
            expenseController.setEntityFilter(entityType, entityId);
        }
        loadSmsHistory();

        tabPane.getSelectionModel().select(tabRecovery);
        ensureTabLoaded(tabRecovery);
    }

    /** Rafraîchit la fiche (retour de section, après enregistrement...). */
    public void onShown() {
        if (entityId != null) {
            refreshHero();
            ensureTabLoaded(tabPane.getSelectionModel().getSelectedItem());
        }
    }

    // -------------------------------------------------------------- Hero

    private void refreshHero() {
        Task<Void> task = new Task<>() {
            String name = "";
            String subtitle = "";
            String statusCode = null;
            double collected;
            double expected;

            @Override
            protected Void call() throws Exception {
                DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                if ("EVENT".equals(entityType)) {
                    Event event = eventService.getEventById(entityId);
                    if (event != null) {
                        name = event.getName();
                        statusCode = event.getStatus();
                        StringBuilder sb = new StringBuilder("Événement");
                        if (event.getStartDate() != null) {
                            sb.append(" · ").append(event.getStartDate().format(df));
                            if (event.getEndDate() != null) {
                                sb.append(" → ").append(event.getEndDate().format(df));
                            }
                        }
                        if (event.getLocation() != null && !event.getLocation().isBlank()) {
                            sb.append(" · ").append(event.getLocation());
                        }
                        subtitle = sb.toString();
                    }
                } else {
                    Project project = projectService.getProjectById(entityId);
                    if (project != null) {
                        name = project.getName();
                        statusCode = project.getStatus();
                        StringBuilder sb = new StringBuilder("Projet");
                        if (project.getEndDate() != null) {
                            sb.append(" · Échéance ").append(project.getEndDate().format(df));
                        }
                        if (project.getManagerName() != null && !project.getManagerName().isBlank()) {
                            sb.append(" · Resp. ").append(project.getManagerName());
                        }
                        subtitle = sb.toString();
                    }
                }
                Double total = contributionService.getTotalByEntity(entityType, entityId);
                collected = total != null ? total : 0.0;
                expected = calculator.expectedForEntity(entityType, entityId);
                return null;
            }

            @Override
            protected void succeeded() {
                entityName = name;
                crumbName.setText(name);
                lblName.setText(name);
                lblSubtitle.setText(subtitle);

                Label statusLabel = CollecteController.statusBadge(statusCode);
                lblStatus.setText(statusLabel.getText());
                lblStatus.getStyleClass().setAll(statusLabel.getStyleClass());

                lblCollected.setText(numberFormat.format(collected));
                if (expected > 0) {
                    double ratio = Math.min(1.0, collected / expected);
                    lblExpected.setText("sur " + numberFormat.format(expected) + " · " + Math.round(ratio * 100) + " %");
                    heroProgress.setProgress(ratio);
                } else {
                    lblExpected.setText("aucun objectif défini");
                    heroProgress.setProgress(0);
                }
            }

            @Override
            protected void failed() {
                getException().printStackTrace();
            }
        };
        Thread thread = new Thread(task, "fiche-hero");
        thread.setDaemon(true);
        thread.start();
    }

    // ------------------------------------------------------------ Onglets

    private void ensureTabLoaded(Tab tab) {
        try {
            if (tab == tabRecovery && recoveryController == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/RecoveryView.fxml"));
                Parent view = loader.load();
                recoveryController = loader.getController();
                recoveryController.setOnDataChanged(() -> {
                    refreshHero();
                    onChanged.run();
                });
                tab.setContent(view);
                recoveryController.setEntity(entityType, entityId);
            } else if (tab == tabContributions && contributionController == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ContributionView.fxml"));
                Parent view = loader.load();
                contributionController = loader.getController();
                tab.setContent(view);
                contributionController.setEntityFilter(entityType, entityId);
            } else if (tab == tabTargets && paymentGroupController == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PaymentGroupView.fxml"));
                Parent view = loader.load();
                paymentGroupController = loader.getController();
                tab.setContent(view);
                paymentGroupController.setEntityFilter(entityType, entityId);
            } else if (tab == tabExpenses && expenseController == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ExpenseView.fxml"));
                Parent view = loader.load();
                expenseController = loader.getController();
                tab.setContent(view);
                expenseController.setEntityFilter(entityType, entityId);
            } else if (tab == tabSms && smsTable == null) {
                tab.setContent(buildSmsTab());
                loadSmsHistory();
            } else if (tab != null) {
                // Onglet déjà chargé : rafraîchir ses données
                if (tab == tabRecovery && recoveryController != null) {
                    recoveryController.reload();
                } else if (tab == tabContributions && contributionController != null) {
                    contributionController.onShown();
                } else if (tab == tabTargets && paymentGroupController != null) {
                    paymentGroupController.onShown();
                } else if (tab == tabExpenses && expenseController != null) {
                    expenseController.onShown();
                } else if (tab == tabSms && smsTable != null) {
                    loadSmsHistory();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Dialogs.error(window(), "Erreur", "Impossible de charger l'onglet.");
        }
    }

    private Parent buildSmsTab() {
        smsTable = new TableView<>();
        smsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        smsTable.setPlaceholder(styledLabel("Aucune campagne SMS envoyée pour cette collecte.", "empty-state"));

        TableColumn<SmsLogDAO.CampaignSummary, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().createdAt() != null ? d.getValue().createdAt() : ""));
        colDate.setMinWidth(150);

        TableColumn<SmsLogDAO.CampaignSummary, String> colRecipients = new TableColumn<>("Destinataires");
        colRecipients.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().recipients())));

        TableColumn<SmsLogDAO.CampaignSummary, String> colSent = new TableColumn<>("Envoyés");
        colSent.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().sent())));

        TableColumn<SmsLogDAO.CampaignSummary, String> colFailed = new TableColumn<>("Échecs");
        colFailed.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().failed())));

        smsTable.getColumns().addAll(List.of(colDate, colRecipients, colSent, colFailed));
        VBox.setVgrow(smsTable, javafx.scene.layout.Priority.ALWAYS);

        Button newCampaign = new Button("Nouvelle campagne SMS");
        newCampaign.getStyleClass().add("primary-button");
        newCampaign.setGraphic(new PhosphorIcon(Phosphor.PAPER_PLANE_TILT, 15));
        newCampaign.setOnAction(e -> openSmsCampaign());

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox toolbar = new HBox(12, spacer, newCampaign);
        toolbar.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(14, toolbar, smsTable);
        box.setPadding(new Insets(16, 0, 0, 0));
        return box;
    }

    private void loadSmsHistory() {
        if (smsTable == null || entityId == null) {
            return;
        }
        Task<List<SmsLogDAO.CampaignSummary>> task = new Task<>() {
            @Override
            protected List<SmsLogDAO.CampaignSummary> call() throws Exception {
                return smsLogDAO.findByEntity(entityType, entityId);
            }
        };
        task.setOnSucceeded(e -> smsTable.getItems().setAll(task.getValue()));
        Thread thread = new Thread(task, "sms-history");
        thread.setDaemon(true);
        thread.start();
    }

    private void openSmsCampaign() {
        try {
            Dialogs.Modal<SMSCampaignDialogController> modal = Dialogs.openModal(
                "/fxml/SMSCampaignDialog.fxml", "Campagne SMS — " + entityName, window());
            modal.controller().setEntity(entityType, entityId, entityName);
            modal.stage().showAndWait();
            loadSmsHistory();
        } catch (IOException e) {
            Dialogs.error(window(), "Erreur", "Impossible d'ouvrir la campagne SMS : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ Actions

    @FXML
    private void handleBack() {
        onBack.run();
    }

    @FXML
    private void handleEdit() {
        try {
            if ("EVENT".equals(entityType)) {
                Event event = eventService.getEventById(entityId);
                if (event == null) {
                    entityGone();
                    return;
                }
                Dialogs.Modal<EventDialogController> modal =
                    Dialogs.openModal("/fxml/EventDialog.fxml", "Modifier l'événement", window());
                modal.controller().setEvent(event);
                modal.stage().showAndWait();
                if (modal.controller().isSaved()) {
                    eventService.updateEvent(modal.controller().getEvent());
                    refreshHero();
                    onChanged.run();
                }
            } else {
                Project project = projectService.getProjectById(entityId);
                if (project == null) {
                    entityGone();
                    return;
                }
                Dialogs.Modal<ProjectDialogController> modal =
                    Dialogs.openModal("/fxml/ProjectDialog.fxml", "Modifier le projet", window());
                modal.controller().setProject(project);
                modal.stage().showAndWait();
                if (modal.controller().isSaved()) {
                    projectService.updateProject(modal.controller().getProject());
                    refreshHero();
                    onChanged.run();
                }
            }
        } catch (IOException | SQLException e) {
            Dialogs.error(window(), "Erreur", "Impossible d'enregistrer : " + e.getMessage());
        }
    }

    /** La collecte a été supprimée entre-temps (souvent par la synchronisation). */
    private void entityGone() {
        Dialogs.warn(window(), "Collecte introuvable",
            "Cette collecte a été supprimée (probablement depuis un autre appareil). Retour à la liste.");
        onChanged.run();
        onBack.run();
    }

    @FXML
    private void handleDelete() {
        boolean confirmed = Dialogs.confirm(window(), "Confirmer la suppression",
            "Supprimer « " + entityName + " » ?",
            "La collecte sera masquée de l'application (suppression logique, propagée à la synchronisation).");
        if (!confirmed) {
            return;
        }
        try {
            if ("EVENT".equals(entityType)) {
                eventService.deleteEvent(entityId);
            } else {
                projectService.deleteProject(entityId);
            }
            onChanged.run();
            onBack.run();
        } catch (SQLException e) {
            Dialogs.error(window(), "Erreur", "Impossible de supprimer : " + e.getMessage());
        }
    }

    private Label styledLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private javafx.stage.Window window() {
        return tabPane.getScene() != null ? tabPane.getScene().getWindow() : null;
    }
}
