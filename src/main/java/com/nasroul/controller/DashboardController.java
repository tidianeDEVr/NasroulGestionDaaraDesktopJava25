package com.nasroul.controller;

import com.nasroul.model.Contribution;
import com.nasroul.model.Event;
import com.nasroul.model.Expense;
import com.nasroul.model.Member;
import com.nasroul.model.Project;
import com.nasroul.service.EventService;
import com.nasroul.service.ExpenseService;
import com.nasroul.service.MemberService;
import com.nasroul.service.ProjectService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class DashboardController {

    @FXML
    private Label totalExpensesLabel;

    @FXML
    private Label cashOnHandLabel;

    @FXML
    private Label lblTotalPaid;

    @FXML
    private Label lblTotalPending;

    @FXML
    private Label lblTotalExpected;

    @FXML
    private Label lblCollectionRate;

    @FXML
    private BarChart<String, Number> contributionBarChart;

    @FXML
    private PieChart eventsPieChart;

    @FXML
    private PieChart projectsPieChart;

    @FXML
    private PieChart paymentMethodsPieChart;

    private final MemberService memberService;
    private final EventService eventService;
    private final ProjectService projectService;
    private final ExpenseService expenseService;
    private final com.nasroul.service.ContributionService contributionService;
    private final com.nasroul.service.PaymentGroupService paymentGroupService;

    public DashboardController() {
        this.memberService = new MemberService();
        this.eventService = new EventService();
        this.projectService = new ProjectService();
        this.expenseService = new ExpenseService();
        this.contributionService = new com.nasroul.service.ContributionService();
        this.paymentGroupService = new com.nasroul.service.PaymentGroupService();
    }

    @FXML
    public void initialize() {
        loadDashboardData();
    }

    private void loadDashboardData() {
        try {
            List<Event> events = eventService.getAllEvents();
            List<Project> projects = projectService.getAllProjects();

            List<Expense> expenses = expenseService.getAllExpenses();
            double totalExpenses = expenses.stream().mapToDouble(Expense::getAmount).sum();
            NumberFormat formatter = NumberFormat.getInstance(Locale.FRANCE);
            totalExpensesLabel.setText(formatter.format(totalExpenses) + " CFA");

            // Calculate cash on hand (total contributions - total expenses)
            double totalContributions = contributionService.getTotalContributions();
            double cashOnHand = totalContributions - totalExpenses;
            cashOnHandLabel.setText(formatter.format(cashOnHand) + " CFA");

            loadEventsPieChart(events);
            loadProjectsPieChart(projects);
            loadPaymentMethodsPieChart();
            loadContributionStatistics();
            loadContributionChart();

        } catch (SQLException e) {
            System.err.println("Error loading dashboard data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadContributionStatistics() throws SQLException {
        List<Contribution> contributions = contributionService.getAllContributions();
        NumberFormat formatter = NumberFormat.getInstance(Locale.FRANCE);

        double totalPaid = contributions.stream()
            .filter(c -> "PAID".equals(c.getStatus()))
            .mapToDouble(Contribution::getAmount)
            .sum();
        lblTotalPaid.setText(formatter.format(totalPaid) + " CFA");

        double totalPending = contributions.stream()
            .filter(c -> "PENDING".equals(c.getStatus()))
            .mapToDouble(Contribution::getAmount)
            .sum();
        lblTotalPending.setText(formatter.format(totalPending) + " CFA");

        // Montant total attendu (objectifs de cotisation)
        double totalExpected = paymentGroupService.getTotalExpectedAmount();
        lblTotalExpected.setText(formatter.format(totalExpected) + " CFA");

        // Taux de recouvrement = (Cotisations payées / Montant attendu) × 100
        double collectionRate = totalExpected > 0 ? (totalPaid / totalExpected * 100.0) : 0;
        lblCollectionRate.setText(String.format("%.1f%%", collectionRate));
    }

    private void loadContributionChart() throws SQLException {
        List<Event> events = eventService.getAllEvents();
        List<Contribution> contributions = contributionService.getAllContributions();

        XYChart.Series<String, Number> targetSeries = new XYChart.Series<>();
        targetSeries.setName("Budget cible");

        XYChart.Series<String, Number> actualSeries = new XYChart.Series<>();
        actualSeries.setName("Cotisations reçues");

        for (Event event : events) {
            String eventName = event.getName();
            double target = event.getContributionTarget() != null ? event.getContributionTarget() : 0;

            double actual = contributions.stream()
                .filter(c -> "EVENT".equals(c.getEntityType()) && c.getEntityId().equals(event.getId()))
                .filter(c -> "PAID".equals(c.getStatus()))
                .mapToDouble(Contribution::getAmount)
                .sum();

            targetSeries.getData().add(new XYChart.Data<>(eventName, target));
            actualSeries.getData().add(new XYChart.Data<>(eventName, actual));
        }

        contributionBarChart.getData().clear();
        contributionBarChart.getData().addAll(targetSeries, actualSeries);
    }

    private void loadEventsPieChart(List<Event> events) {
        Map<String, Long> eventsByStatus = events.stream()
            .collect(Collectors.groupingBy(Event::getStatus, Collectors.counting()));

        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();

        eventsByStatus.forEach((status, count) -> {
            String translatedStatus = translateStatus(status);
            pieChartData.add(new PieChart.Data(translatedStatus + " (" + count + ")", count));
        });

        if (pieChartData.isEmpty()) {
            pieChartData.add(new PieChart.Data("Aucune donnée", 1));
        }

        eventsPieChart.setData(pieChartData);
        eventsPieChart.setTitle("");
        eventsPieChart.setLegendVisible(true);

        // Appliquer des couleurs fixes basées sur le statut
        applyPieChartColors(pieChartData);
    }

    private void loadProjectsPieChart(List<Project> projects) {
        Map<String, Long> projectsByStatus = projects.stream()
            .collect(Collectors.groupingBy(Project::getStatus, Collectors.counting()));

        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();

        projectsByStatus.forEach((status, count) -> {
            String translatedStatus = translateStatus(status);
            pieChartData.add(new PieChart.Data(translatedStatus + " (" + count + ")", count));
        });

        if (pieChartData.isEmpty()) {
            pieChartData.add(new PieChart.Data("Aucune donnée", 1));
        }

        projectsPieChart.setData(pieChartData);
        projectsPieChart.setTitle("");
        projectsPieChart.setLegendVisible(true);

        // Appliquer des couleurs fixes basées sur le statut
        applyPieChartColors(pieChartData);
    }

    private void loadPaymentMethodsPieChart() {
        try {
            List<Contribution> contributions = contributionService.getAllContributions();

            // Grouper par méthode de paiement et compter
            Map<String, Long> contributionsByMethod = contributions.stream()
                .filter(c -> "PAID".equals(c.getStatus())) // Seulement les cotisations payées
                .collect(Collectors.groupingBy(Contribution::getPaymentMethod, Collectors.counting()));

            ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();

            contributionsByMethod.forEach((method, count) -> {
                String translatedMethod = translatePaymentMethod(method);
                pieChartData.add(new PieChart.Data(translatedMethod + " (" + count + ")", count));
            });

            if (pieChartData.isEmpty()) {
                pieChartData.add(new PieChart.Data("Aucune donnée", 1));
            }

            paymentMethodsPieChart.setData(pieChartData);
            paymentMethodsPieChart.setTitle("");
            paymentMethodsPieChart.setLegendVisible(true);

            // Appliquer des couleurs pour les méthodes de paiement
            applyPaymentMethodColors(pieChartData);
        } catch (SQLException e) {
            System.err.println("Error loading payment methods chart: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void applyPieChartColors(ObservableList<PieChart.Data> pieChartData) {
        // Attendre que le graphique soit rendu pour appliquer les couleurs
        javafx.application.Platform.runLater(() -> {
            int i = 0;
            for (PieChart.Data data : pieChartData) {
                String dataName = data.getName();
                String color = getColorForStatus(dataName);

                if (data.getNode() != null) {
                    data.getNode().setStyle("-fx-pie-color: " + color + ";");
                }
                i++;
            }
        });
    }

    private String getColorForStatus(String statusLabel) {
        // Retourner une couleur fixe basée sur le statut
        if (statusLabel.startsWith("Planifié")) {
            return "#0969da"; // Bleu
        } else if (statusLabel.startsWith("En cours")) {
            return "#bf8700"; // Jaune/Orange
        } else if (statusLabel.startsWith("Terminé")) {
            return "#1a7f37"; // Vert
        } else if (statusLabel.startsWith("Annulé")) {
            return "#cf222e"; // Rouge
        } else {
            return "#6e7781"; // Gris par défaut
        }
    }

    private String translateStatus(String status) {
        if (status == null) return "";
        switch (status) {
            case "PLANNED": return "Planifié";
            case "ONGOING": return "En cours";
            case "COMPLETED": return "Terminé";
            case "CANCELLED": return "Annulé";
            default: return status;
        }
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

    private void applyPaymentMethodColors(ObservableList<PieChart.Data> pieChartData) {
        javafx.application.Platform.runLater(() -> {
            for (PieChart.Data data : pieChartData) {
                String dataName = data.getName();
                String color = getColorForPaymentMethod(dataName);

                if (data.getNode() != null) {
                    data.getNode().setStyle("-fx-pie-color: " + color + ";");
                }
            }
        });
    }

    private String getColorForPaymentMethod(String methodLabel) {
        if (methodLabel.startsWith("Espèces")) {
            return "#1a7f37"; // Vert
        } else if (methodLabel.startsWith("Wave")) {
            return "#0969da"; // Bleu
        } else if (methodLabel.startsWith("Orange Money")) {
            return "#fb8500"; // Orange
        } else if (methodLabel.startsWith("Virement")) {
            return "#8250df"; // Violet
        } else {
            return "#6e7781"; // Gris par défaut
        }
    }

    @FXML
    private void handleNewMember() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MemberDialog.fxml"));
            Scene scene = new Scene(loader.load());

            MemberDialogController controller = loader.getController();
            controller.setMember(new Member());

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Nouveau membre");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(totalExpensesLabel.getScene().getWindow());
            dialogStage.setScene(scene);
            dialogStage.setResizable(false);

            dialogStage.showAndWait();

            if (controller.isSaved()) {
                try {
                    Member savedMember = controller.getMember();
                    memberService.createMember(savedMember);
                    showInfo("Succès", "Membre créé avec succès");
                    loadDashboardData();
                } catch (SQLException e) {
                    showError("Erreur", "Impossible de sauvegarder le membre: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            showError("Erreur", "Impossible d'ouvrir le dialogue: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleNewEvent() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/EventDialog.fxml"));
            Scene scene = new Scene(loader.load());

            EventDialogController controller = loader.getController();
            controller.setEvent(new Event());

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Nouvel événement");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(totalExpensesLabel.getScene().getWindow());
            dialogStage.setScene(scene);
            dialogStage.setResizable(false);

            dialogStage.showAndWait();

            if (controller.isSaved()) {
                try {
                    Event savedEvent = controller.getEvent();
                    eventService.createEvent(savedEvent);
                    showInfo("Succès", "Événement créé avec succès");
                    loadDashboardData();
                } catch (SQLException e) {
                    showError("Erreur", "Impossible de sauvegarder l'événement: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            showError("Erreur", "Impossible d'ouvrir le dialogue: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleNewProject() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ProjectDialog.fxml"));
            Scene scene = new Scene(loader.load());

            ProjectDialogController controller = loader.getController();
            controller.setProject(new Project());

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Nouveau projet");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(totalExpensesLabel.getScene().getWindow());
            dialogStage.setScene(scene);
            dialogStage.setResizable(false);

            dialogStage.showAndWait();

            if (controller.isSaved()) {
                try {
                    Project savedProject = controller.getProject();
                    projectService.createProject(savedProject);
                    showInfo("Succès", "Projet créé avec succès");
                    loadDashboardData();
                } catch (SQLException e) {
                    showError("Erreur", "Impossible de sauvegarder le projet: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            showError("Erreur", "Impossible d'ouvrir le dialogue: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleNewContribution() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ContributionDialog.fxml"));
            javafx.scene.Parent root = loader.load();

            ContributionDialogController controller = loader.getController();
            controller.setDialogStage(new Stage());
            controller.getDialogStage().setTitle("Nouvelle cotisation");
            controller.getDialogStage().initModality(Modality.WINDOW_MODAL);
            controller.getDialogStage().initOwner(totalExpensesLabel.getScene().getWindow());
            controller.getDialogStage().setScene(new Scene(root));
            controller.getDialogStage().showAndWait();

            if (controller.isConfirmed()) {
                showInfo("Succès", "Cotisation créée avec succès");
                loadDashboardData();
            }
        } catch (Exception e) {
            showError("Erreur", "Impossible d'ouvrir le dialogue: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
