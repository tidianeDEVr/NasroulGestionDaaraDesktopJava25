package com.nasroul.controller;

import com.nasroul.model.Event;
import com.nasroul.model.Project;
import com.nasroul.service.ContributionCalculator;
import com.nasroul.service.ContributionService;
import com.nasroul.service.EventService;
import com.nasroul.service.ProjectService;
import com.nasroul.ui.Dialogs;
import com.nasroul.ui.Refreshable;
import com.nasroul.util.ExcelUtil;
import com.nasroul.ui.Forms;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Section « Collectes » : liste unifiée des événements et projets, chaque
 * ligne s'ouvrant sur la fiche à onglets (EntityDetailView).
 */
public class CollecteController implements Refreshable {

    /** Ligne unifiée événement/projet. */
    public record CollecteRow(String entityType, int id, String name, String dates,
                              String statusCode, double collected, double expected) {
        public boolean isEvent() {
            return "EVENT".equals(entityType);
        }
    }

    @FXML private VBox listPane;
    @FXML private VBox detailPane;
    @FXML private EntityDetailController entityDetailController;
    @FXML private ToggleButton tabAll;
    @FXML private ToggleButton tabEvents;
    @FXML private ToggleButton tabProjects;
    @FXML private TextField searchField;
    @FXML private TableView<CollecteRow> collecteTable;
    @FXML private TableColumn<CollecteRow, String> colName;
    @FXML private TableColumn<CollecteRow, CollecteRow> colType;
    @FXML private TableColumn<CollecteRow, CollecteRow> colStatus;
    @FXML private TableColumn<CollecteRow, String> colDates;
    @FXML private TableColumn<CollecteRow, CollecteRow> colProgress;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;

    private final EventService eventService = new EventService();
    private final ProjectService projectService = new ProjectService();
    private final ContributionService contributionService = new ContributionService();
    private final ContributionCalculator calculator = new ContributionCalculator();
    private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.FRANCE);

    private final ObservableList<CollecteRow> allRows = FXCollections.observableArrayList();
    private FilteredList<CollecteRow> filteredRows;
    private final ToggleGroup filterGroup = new ToggleGroup();

    @FXML
    public void initialize() {
        setupFilterTabs();
        setupTable();
        entityDetailController.setOnBack(this::showList);
        entityDetailController.setOnChanged(this::loadData);
        loadData();
    }

    private void setupFilterTabs() {
        tabAll.setToggleGroup(filterGroup);
        tabEvents.setToggleGroup(filterGroup);
        tabProjects.setToggleGroup(filterGroup);
        filterGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            } else {
                applyFilter();
            }
        });
        searchField.textProperty().addListener((obs, old, val) -> applyFilter());
    }

    private void setupTable() {
        filteredRows = new FilteredList<>(allRows);
        collecteTable.setItems(filteredRows);

        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        colDates.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().dates()));

        colType.setCellValueFactory(data -> Bindings.createObjectBinding(data::getValue));
        colType.setCellFactory(col -> badgeCell(row ->
            badge(row.isEvent() ? "Événement" : "Projet", "badge")));

        colStatus.setCellValueFactory(data -> Bindings.createObjectBinding(data::getValue));
        colStatus.setCellFactory(col -> badgeCell(row -> statusBadge(row.statusCode())));

        colProgress.setCellValueFactory(data -> Bindings.createObjectBinding(data::getValue));
        colProgress.setCellFactory(col -> new TableCell<>() {
            private final ProgressBar bar = new ProgressBar(0);
            private final Label label = new Label();
            private final HBox box = new HBox(10, bar, label);

            {
                bar.setPrefWidth(120);
                box.setAlignment(Pos.CENTER_LEFT);
                label.getStyleClass().add("text-muted");
            }

            @Override
            protected void updateItem(CollecteRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                double ratio = row.expected() > 0 ? Math.min(1.0, row.collected() / row.expected()) : 0;
                bar.setProgress(ratio);
                if (row.expected() > 0) {
                    label.setText(numberFormat.format(row.collected()) + " / "
                        + numberFormat.format(row.expected()) + " CFA · " + Math.round(ratio * 100) + " %");
                } else {
                    label.setText(numberFormat.format(row.collected()) + " CFA · aucun objectif défini");
                }
                setGraphic(box);
            }
        });

        // Boutons actifs seulement quand une ligne est sélectionnée
        var noSelection = collecteTable.getSelectionModel().selectedItemProperty().isNull();
        btnEdit.disableProperty().bind(noSelection);
        btnDelete.disableProperty().bind(noSelection);

        // Double-clic = ouvrir la fiche
        collecteTable.setRowFactory(tv -> {
            var row = new javafx.scene.control.TableRow<CollecteRow>();
            row.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()) {
                    openDetail(row.getItem());
                }
            });
            return row;
        });
    }

    private void applyFilter() {
        String search = searchField.getText() == null ? "" : Forms.text(searchField).toLowerCase();
        boolean eventsOnly = tabEvents.isSelected();
        boolean projectsOnly = tabProjects.isSelected();
        filteredRows.setPredicate(row -> {
            if (eventsOnly && !row.isEvent()) return false;
            if (projectsOnly && row.isEvent()) return false;
            return search.isEmpty() || row.name().toLowerCase().contains(search);
        });
    }

    @Override
    public void onShown() {
        if (detailPane.isVisible()) {
            entityDetailController.onShown();
        } else {
            loadData();
        }
    }

    private void loadData() {
        Task<List<CollecteRow>> task = new Task<>() {
            @Override
            protected List<CollecteRow> call() throws Exception {
                DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                // Deux requêtes agrégées pour toute la liste (pas de N+1)
                var paidByEntity = contributionService.getPaidTotalsByEntity();
                var expectedByEntity = calculator.expectedTotalsByEntity();

                List<CollecteRow> rows = new ArrayList<>();
                for (Event event : eventService.getAllEvents()) {
                    String dates = event.getStartDate() != null ? event.getStartDate().format(df) : "";
                    if (event.getEndDate() != null) {
                        dates += " → " + event.getEndDate().format(df);
                    }
                    String key = "EVENT:" + event.getId();
                    rows.add(new CollecteRow("EVENT", event.getId(), event.getName(), dates,
                        event.getStatus(), paidByEntity.getOrDefault(key, 0.0),
                        expectedByEntity.getOrDefault(key, 0.0)));
                }
                for (Project project : projectService.getAllProjects()) {
                    String dates = project.getEndDate() != null
                        ? "Échéance " + project.getEndDate().format(df) : "";
                    String key = "PROJECT:" + project.getId();
                    rows.add(new CollecteRow("PROJECT", project.getId(), project.getName(), dates,
                        project.getStatus(), paidByEntity.getOrDefault(key, 0.0),
                        expectedByEntity.getOrDefault(key, 0.0)));
                }
                return rows;
            }
        };
        task.setOnSucceeded(e -> {
            allRows.setAll(task.getValue());
            applyFilter();
        });
        task.setOnFailed(e -> {
            task.getException().printStackTrace();
            Dialogs.error(window(), "Erreur", "Impossible de charger les collectes.");
        });
        Thread thread = new Thread(task, "collectes-load");
        thread.setDaemon(true);
        thread.start();
    }

    // ------------------------------------------------------------- Fiche

    private void showList() {
        detailPane.setVisible(false);
        detailPane.setManaged(false);
        listPane.setVisible(true);
        listPane.setManaged(true);
        loadData();
    }

    private void openDetail(CollecteRow row) {
        if (row == null) {
            return;
        }
        entityDetailController.setEntity(row.entityType(), row.id());
        listPane.setVisible(false);
        listPane.setManaged(false);
        detailPane.setVisible(true);
        detailPane.setManaged(true);
    }

    // ------------------------------------------------------------- CRUD

    @FXML
    private void handleAdd() {
        Alert choice = new Alert(Alert.AlertType.CONFIRMATION);
        choice.setTitle("Nouvelle collecte");
        choice.setHeaderText("Que voulez-vous créer ?");
        choice.setContentText("Un événement a des dates (Gamou, Magal…) ; un projet a une échéance et un budget (construction, fonds…).");
        ButtonType eventBtn = new ButtonType("Événement");
        ButtonType projectBtn = new ButtonType("Projet");
        choice.getButtonTypes().setAll(eventBtn, projectBtn, ButtonType.CANCEL);
        choice.initOwner(window());
        com.nasroul.ui.ThemeManager.applyTo(choice.getDialogPane());

        choice.showAndWait().ifPresent(bt -> {
            if (bt == eventBtn) {
                openEventDialog(null);
            } else if (bt == projectBtn) {
                openProjectDialog(null);
            }
        });
    }

    @FXML
    private void handleEdit() {
        CollecteRow row = collecteTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            return;
        }
        try {
            if (row.isEvent()) {
                openEventDialog(eventService.getEventById(row.id()));
            } else {
                openProjectDialog(projectService.getProjectById(row.id()));
            }
        } catch (SQLException e) {
            Dialogs.error(window(), "Erreur", "Impossible de charger la collecte : " + e.getMessage());
        }
    }

    @FXML
    private void handleDelete() {
        CollecteRow row = collecteTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            return;
        }
        boolean confirmed = Dialogs.confirm(window(), "Confirmer la suppression",
            "Supprimer « " + row.name() + " » ?",
            "La collecte sera masquée de l'application (suppression logique, propagée à la synchronisation).");
        if (!confirmed) {
            return;
        }
        try {
            if (row.isEvent()) {
                eventService.deleteEvent(row.id());
            } else {
                projectService.deleteProject(row.id());
            }
            loadData();
        } catch (SQLException e) {
            Dialogs.error(window(), "Erreur", "Impossible de supprimer la collecte : " + e.getMessage());
        }
    }

    void openEventDialog(Event event) {
        try {
            Dialogs.Modal<EventDialogController> modal =
                Dialogs.openModal("/fxml/EventDialog.fxml",
                    event != null ? "Modifier l'événement" : "Nouvel événement", window());
            modal.controller().setEvent(event != null ? event : new Event());
            modal.stage().showAndWait();

            if (modal.controller().isSaved()) {
                Event saved = modal.controller().getEvent();
                if (saved.getId() == null) {
                    eventService.createEvent(saved);
                } else {
                    eventService.updateEvent(saved);
                }
                loadData();
            }
        } catch (IOException | SQLException e) {
            Dialogs.error(window(), "Erreur", "Impossible d'enregistrer l'événement : " + e.getMessage());
        }
    }

    void openProjectDialog(Project project) {
        try {
            Dialogs.Modal<ProjectDialogController> modal =
                Dialogs.openModal("/fxml/ProjectDialog.fxml",
                    project != null ? "Modifier le projet" : "Nouveau projet", window());
            modal.controller().setProject(project != null ? project : new Project());
            modal.stage().showAndWait();

            if (modal.controller().isSaved()) {
                Project saved = modal.controller().getProject();
                if (saved.getId() == null) {
                    projectService.createProject(saved);
                } else {
                    projectService.updateProject(saved);
                }
                loadData();
            }
        } catch (IOException | SQLException e) {
            Dialogs.error(window(), "Erreur", "Impossible d'enregistrer le projet : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------- Excel

    @FXML
    private void handleImportEvents() {
        File file = chooseOpen("Importer des événements depuis Excel");
        if (file != null) {
            try {
                List<Event> imported = ExcelUtil.importEvents(file);
                eventService.bulkCreate(imported);
                loadData();
                Dialogs.info(window(), "Import terminé", imported.size() + " événement(s) importé(s).");
            } catch (Exception e) {
                Dialogs.error(window(), "Erreur d'import", "Impossible d'importer les événements : " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleImportProjects() {
        File file = chooseOpen("Importer des projets depuis Excel");
        if (file != null) {
            try {
                List<Project> imported = ExcelUtil.importProjects(file);
                projectService.bulkCreate(imported);
                loadData();
                Dialogs.info(window(), "Import terminé", imported.size() + " projet(s) importé(s).");
            } catch (Exception e) {
                Dialogs.error(window(), "Erreur d'import", "Impossible d'importer les projets : " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleEventTemplate() {
        File file = chooseSave("modele_evenements.xlsx");
        if (file != null) {
            try {
                ExcelUtil.generateEventTemplate(file);
                Dialogs.info(window(), "Modèle enregistré", "Le modèle d'import des événements a été enregistré.");
            } catch (Exception e) {
                Dialogs.error(window(), "Erreur", "Impossible d'enregistrer le modèle : " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleProjectTemplate() {
        File file = chooseSave("modele_projets.xlsx");
        if (file != null) {
            try {
                ExcelUtil.generateProjectTemplate(file);
                Dialogs.info(window(), "Modèle enregistré", "Le modèle d'import des projets a été enregistré.");
            } catch (Exception e) {
                Dialogs.error(window(), "Erreur", "Impossible d'enregistrer le modèle : " + e.getMessage());
            }
        }
    }

    private File chooseOpen(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichiers Excel", "*.xlsx"));
        return chooser.showOpenDialog(window());
    }

    private File chooseSave(String fileName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Enregistrer le modèle");
        chooser.setInitialFileName(fileName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichiers Excel", "*.xlsx"));
        return chooser.showSaveDialog(window());
    }

    // ------------------------------------------------------------- Divers

    private TableCell<CollecteRow, CollecteRow> badgeCell(java.util.function.Function<CollecteRow, Label> factory) {
        return new TableCell<>() {
            @Override
            protected void updateItem(CollecteRow row, boolean empty) {
                super.updateItem(row, empty);
                setGraphic(empty || row == null ? null : factory.apply(row));
            }
        };
    }

    static Label badge(String text, String... styleClasses) {
        Label label = new Label(text);
        label.getStyleClass().add("badge");
        for (String sc : styleClasses) {
            if (!"badge".equals(sc)) {
                label.getStyleClass().add(sc);
            }
        }
        return label;
    }

    static Label statusBadge(String statusCode) {
        if (statusCode == null) {
            return badge("—");
        }
        return switch (statusCode) {
            case "PLANNED", "PLANNING" -> badge("Planifié", "badge-warning");
            case "ONGOING" -> badge("En cours", "badge-lime");
            case "COMPLETED" -> badge("Terminé", "badge-success");
            case "CANCELLED" -> badge("Annulé", "badge-danger");
            case "ON_HOLD" -> badge("En attente", "badge-warning");
            default -> badge(statusCode);
        };
    }

    private javafx.stage.Window window() {
        return collecteTable.getScene() != null ? collecteTable.getScene().getWindow() : null;
    }
}
