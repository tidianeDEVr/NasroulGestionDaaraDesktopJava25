package com.nasroul.controller;

import com.nasroul.model.Contribution;
import com.nasroul.model.Event;
import com.nasroul.model.Expense;
import com.nasroul.model.Member;
import com.nasroul.model.Project;
import com.nasroul.service.ContributionCalculator;
import com.nasroul.service.ContributionService;
import com.nasroul.service.EventService;
import com.nasroul.service.ExpenseService;
import com.nasroul.service.MemberService;
import com.nasroul.service.ProjectService;
import com.nasroul.ui.Dialogs;
import com.nasroul.ui.Refreshable;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class DashboardController implements Refreshable {

    @FXML private Label totalExpensesLabel;
    @FXML private Label cashOnHandLabel;
    @FXML private Label lblTotalPaid;
    @FXML private Label lblTotalPending;
    @FXML private Label lblTotalExpected;
    @FXML private Label lblCollectionRate;
    @FXML private BarChart<String, Number> contributionBarChart;
    @FXML private VBox eventsBreakdown;
    @FXML private VBox projectsBreakdown;
    @FXML private VBox paymentMethodsBreakdown;
    @FXML private ProgressBar recoveryProgress;

    private final MemberService memberService = new MemberService();
    private final EventService eventService = new EventService();
    private final ProjectService projectService = new ProjectService();
    private final ExpenseService expenseService = new ExpenseService();
    private final ContributionService contributionService = new ContributionService();
    private final ContributionCalculator contributionCalculator = new ContributionCalculator();
    private final NumberFormat formatter = NumberFormat.getInstance(Locale.FRANCE);

    @FXML
    public void initialize() {
        loadDashboardData();
    }

    @Override
    public void onShown() {
        loadDashboardData();
    }

    /**
     * Toutes les lectures en tâche de fond, une seule fois par rafraîchissement
     * (une lecture des cotisations, une requête agrégée pour les attendus) —
     * plus aucun accès base sur le thread JavaFX.
     */
    private void loadDashboardData() {
        Task<Void> task = new Task<>() {
            List<Event> events;
            List<Project> projects;
            List<Contribution> contributions;
            double totalExpenses;
            double totalExpected;
            Map<String, Double> expectedByEntity;

            @Override
            protected Void call() throws Exception {
                events = eventService.getAllEvents();
                projects = projectService.getAllProjects();
                contributions = contributionService.getAllContributions();
                List<Expense> expenses = expenseService.getAllExpenses();
                totalExpenses = expenses.stream().mapToDouble(Expense::getAmount).sum();
                totalExpected = contributionCalculator.totalExpectedAll();
                expectedByEntity = contributionCalculator.expectedTotalsByEntity();
                return null;
            }

            @Override
            protected void succeeded() {
                renderDashboard(events, projects, contributions, totalExpenses, totalExpected, expectedByEntity);
            }

            @Override
            protected void failed() {
                getException().printStackTrace();
                Dialogs.error(window(), "Erreur",
                    "Impossible de charger le tableau de bord : " + getException().getMessage());
            }
        };
        Thread thread = new Thread(task, "dashboard-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderDashboard(List<Event> events, List<Project> projects, List<Contribution> contributions,
                                 double totalExpenses, double totalExpected, Map<String, Double> expectedByEntity) {
        double totalPaid = contributions.stream()
            .filter(c -> "PAID".equals(c.getStatus()))
            .mapToDouble(Contribution::getAmount)
            .sum();
        double totalPending = contributions.stream()
            .filter(c -> "PENDING".equals(c.getStatus()))
            .mapToDouble(Contribution::getAmount)
            .sum();

        totalExpensesLabel.setText(formatter.format(totalExpenses) + " CFA");
        cashOnHandLabel.setText(formatter.format(totalPaid - totalExpenses) + " CFA");
        lblTotalPaid.setText(formatter.format(totalPaid) + " CFA");
        lblTotalPending.setText(formatter.format(totalPending) + " CFA");
        lblTotalExpected.setText(formatter.format(totalExpected) + " CFA");

        double collectionRate = totalExpected > 0 ? (totalPaid / totalExpected * 100.0) : 0;
        lblCollectionRate.setText(String.format("%.1f %%", collectionRate));
        if (recoveryProgress != null) {
            recoveryProgress.setProgress(totalExpected > 0 ? Math.min(1.0, totalPaid / totalExpected) : 0);
        }

        renderContributionChart(events, contributions, expectedByEntity);
        renderBreakdown(eventsBreakdown,
            countBy(events.stream().map(e -> translateStatus(e.getStatus()))),
            this::getColorForStatus);
        renderBreakdown(projectsBreakdown,
            countBy(projects.stream().map(p -> translateStatus(p.getStatus()))),
            this::getColorForStatus);
        renderBreakdown(paymentMethodsBreakdown,
            countBy(contributions.stream()
                .filter(c -> "PAID".equals(c.getStatus()))
                .map(c -> translatePaymentMethod(c.getPaymentMethod()))),
            this::getColorForPaymentMethod);
    }

    private Map<String, Long> countBy(java.util.stream.Stream<String> labels) {
        return labels.collect(Collectors.groupingBy(l -> l, LinkedHashMap::new, Collectors.counting()));
    }

    private void renderContributionChart(List<Event> events, List<Contribution> contributions,
                                         Map<String, Double> expectedByEntity) {
        XYChart.Series<String, Number> actualSeries = new XYChart.Series<>();
        actualSeries.setName("Cotisations reçues");
        XYChart.Series<String, Number> targetSeries = new XYChart.Series<>();
        targetSeries.setName("Montant attendu");

        Map<Integer, Double> paidByEvent = contributions.stream()
            .filter(c -> "EVENT".equals(c.getEntityType()) && "PAID".equals(c.getStatus()))
            .collect(Collectors.groupingBy(Contribution::getEntityId,
                Collectors.summingDouble(Contribution::getAmount)));

        for (Event event : events) {
            double target = expectedByEntity.getOrDefault("EVENT:" + event.getId(), 0.0);
            double actual = paidByEvent.getOrDefault(event.getId(), 0.0);
            actualSeries.getData().add(new XYChart.Data<>(event.getName(), actual));
            targetSeries.getData().add(new XYChart.Data<>(event.getName(), target));
        }

        contributionBarChart.getData().clear();
        contributionBarChart.getData().addAll(actualSeries, targetSeries);
    }

    /**
     * Répartition moderne : une ligne par catégorie avec pastille de couleur,
     * effectif et barre de proportion (remplace les camemberts).
     */
    private void renderBreakdown(VBox box, Map<String, Long> counts,
                                 java.util.function.Function<String, String> colorFor) {
        box.getChildren().clear();
        if (counts.isEmpty()) {
            Label empty = new Label("Aucune donnée");
            empty.getStyleClass().add("empty-state");
            box.getChildren().add(empty);
            return;
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> {
                String color = colorFor.apply(entry.getKey());
                double ratio = total > 0 ? (double) entry.getValue() / total : 0;

                Region dot = new Region();
                dot.setMinSize(10, 10);
                dot.setMaxSize(10, 10);
                dot.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3;");

                Label name = new Label(entry.getKey());
                name.setStyle("-fx-font-size: 13px;");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Label count = new Label(entry.getValue() + " · " + Math.round(ratio * 100) + " %");
                count.getStyleClass().add("text-muted");
                count.setStyle("-fx-font-size: 12px;");
                HBox top = new HBox(8, dot, name, spacer, count);
                top.setAlignment(Pos.CENTER_LEFT);

                Region track = new Region();
                track.setMinHeight(6);
                track.setMaxHeight(6);
                track.setMaxWidth(Double.MAX_VALUE);
                track.setStyle("-fx-background-color: #EDEFE6; -fx-background-radius: 999;");
                Region fill = new Region();
                fill.setMinHeight(6);
                fill.setMaxHeight(6);
                fill.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 999;");
                fill.prefWidthProperty().bind(track.widthProperty().multiply(ratio));
                fill.setMaxWidth(Region.USE_PREF_SIZE);
                StackPane bar = new StackPane(track, fill);
                bar.setAlignment(Pos.CENTER_LEFT);

                VBox row = new VBox(5, top, bar);
                box.getChildren().add(row);
            });
    }

    private String getColorForStatus(String statusLabel) {
        // Palette de marque (theme.css)
        if (statusLabel.startsWith("Planifié") || statusLabel.startsWith("Planification")) {
            return "#E3B35C"; // ambre doux
        } else if (statusLabel.startsWith("En cours")) {
            return "#7CB342"; // lime
        } else if (statusLabel.startsWith("Terminé")) {
            return "#0E3B12"; // vert forêt
        } else if (statusLabel.startsWith("Annulé")) {
            return "#B3261E"; // rouge
        } else {
            return "#8B977F"; // gris-vert
        }
    }

    private String translateStatus(String status) {
        if (status == null) return "Inconnu";
        return switch (status) {
            case "PLANNED" -> "Planifié";
            case "PLANNING" -> "Planification";
            case "ONGOING" -> "En cours";
            case "COMPLETED" -> "Terminé";
            case "CANCELLED" -> "Annulé";
            case "ON_HOLD" -> "En attente";
            default -> status;
        };
    }

    private String translatePaymentMethod(String method) {
        if (method == null) return "Non spécifié";
        return switch (method) {
            case "CASH" -> "Espèces";
            case "WAVE" -> "Wave";
            case "ORANGE_MONEY" -> "Orange Money";
            case "BANK_TRANSFER" -> "Virement";
            default -> method;
        };
    }

    private String getColorForPaymentMethod(String methodLabel) {
        if (methodLabel.startsWith("Espèces")) {
            return "#0E3B12"; // vert forêt
        } else if (methodLabel.startsWith("Wave")) {
            return "#7CB342"; // lime
        } else if (methodLabel.startsWith("Orange Money")) {
            return "#E3B35C"; // ambre
        } else if (methodLabel.startsWith("Virement")) {
            return "#C9E3AC"; // lime clair
        } else {
            return "#8B977F"; // gris-vert
        }
    }

    // ------------------------------------------------------------- Actions

    @FXML
    private void handleNewMember() {
        try {
            Dialogs.Modal<MemberDialogController> modal =
                Dialogs.openModal("/fxml/MemberDialog.fxml", "Nouveau membre", window());
            modal.controller().setMember(new Member());
            modal.stage().setResizable(false);
            modal.stage().showAndWait();

            if (modal.controller().isSaved()) {
                memberService.createMember(modal.controller().getMember());
                Dialogs.info(window(), "Succès", "Membre créé avec succès.");
                loadDashboardData();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Dialogs.error(window(), "Erreur", "Impossible d'enregistrer le membre : " + e.getMessage());
        }
    }

    @FXML
    private void handleNewEvent() {
        try {
            Dialogs.Modal<EventDialogController> modal =
                Dialogs.openModal("/fxml/EventDialog.fxml", "Nouvel événement", window());
            modal.controller().setEvent(new Event());
            modal.stage().setResizable(false);
            modal.stage().showAndWait();

            if (modal.controller().isSaved()) {
                eventService.createEvent(modal.controller().getEvent());
                Dialogs.info(window(), "Succès", "Événement créé avec succès.");
                loadDashboardData();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Dialogs.error(window(), "Erreur", "Impossible d'enregistrer l'événement : " + e.getMessage());
        }
    }

    @FXML
    private void handleNewProject() {
        try {
            Dialogs.Modal<ProjectDialogController> modal =
                Dialogs.openModal("/fxml/ProjectDialog.fxml", "Nouveau projet", window());
            modal.controller().setProject(new Project());
            modal.stage().setResizable(false);
            modal.stage().showAndWait();

            if (modal.controller().isSaved()) {
                projectService.createProject(modal.controller().getProject());
                Dialogs.info(window(), "Succès", "Projet créé avec succès.");
                loadDashboardData();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Dialogs.error(window(), "Erreur", "Impossible d'enregistrer le projet : " + e.getMessage());
        }
    }

    @FXML
    private void handleNewContribution() {
        try {
            Dialogs.Modal<ContributionDialogController> modal =
                Dialogs.openModal("/fxml/ContributionDialog.fxml", "Nouvelle cotisation", window());
            modal.controller().setDialogStage(modal.stage());
            modal.stage().showAndWait();

            if (modal.controller().isConfirmed()) {
                loadDashboardData();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Dialogs.error(window(), "Erreur", "Impossible d'ouvrir le dialogue : " + e.getMessage());
        }
    }

    private javafx.stage.Window window() {
        return totalExpensesLabel.getScene() != null ? totalExpensesLabel.getScene().getWindow() : null;
    }
}
