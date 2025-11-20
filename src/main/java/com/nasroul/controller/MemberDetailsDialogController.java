package com.nasroul.controller;

import com.nasroul.model.Contribution;
import com.nasroul.model.Event;
import com.nasroul.model.Member;
import com.nasroul.service.ContributionService;
import com.nasroul.service.EventService;
import com.nasroul.service.ProjectService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class MemberDetailsDialogController {

    @FXML private StackPane avatarContainer;
    @FXML private ImageView avatarImageView;
    @FXML private Label lblMemberName;
    @FXML private Label lblEmail;
    @FXML private Label lblPhone;
    @FXML private Label lblBirthDate;
    @FXML private Label lblJoinDate;
    @FXML private Label lblRole;
    @FXML private Label lblGroup;
    @FXML private Label lblAddress;
    @FXML private Label lblTotalContributed;
    @FXML private Label lblTotalPaid;
    @FXML private Label lblTotalPending;
    @FXML private Label lblContributionCount;
    @FXML private ComboBox<String> cbFilterType;
    @FXML private ComboBox<Object> cbFilterEntity;
    @FXML private TableView<Contribution> contributionTable;
    @FXML private TableColumn<Contribution, String> colType;
    @FXML private TableColumn<Contribution, String> colEntity;
    @FXML private TableColumn<Contribution, Double> colAmount;
    @FXML private TableColumn<Contribution, String> colDate;
    @FXML private TableColumn<Contribution, String> colStatus;
    @FXML private TableColumn<Contribution, String> colPaymentMethod;

    private Member member;
    private final ContributionService contributionService;
    private final EventService eventService;
    private final ProjectService projectService;
    private final ObservableList<Contribution> allContributions = FXCollections.observableArrayList();
    private final ObservableList<Contribution> filteredContributions = FXCollections.observableArrayList();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final NumberFormat numberFormatter = NumberFormat.getInstance(Locale.FRANCE);

    public MemberDetailsDialogController() {
        this.contributionService = new ContributionService();
        this.eventService = new EventService();
        this.projectService = new ProjectService();
    }

    @FXML
    private void initialize() {
        setupTableColumns();
        setupFilters();
    }

    private void setupTableColumns() {
        colType.setCellValueFactory(cellData ->
            new javafx.beans.property.SimpleStringProperty(getEntityTypeLabel(cellData.getValue().getEntityType())));

        colEntity.setCellValueFactory(cellData -> {
            try {
                return new javafx.beans.property.SimpleStringProperty(getEntityName(cellData.getValue()));
            } catch (SQLException e) {
                return new javafx.beans.property.SimpleStringProperty("Erreur");
            }
        });

        colAmount.setCellValueFactory(cellData ->
            new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getAmount()));
        colAmount.setCellFactory(column -> new TableCell<Contribution, Double>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                setText(empty || amount == null ? null : numberFormatter.format(amount));
            }
        });

        colDate.setCellValueFactory(cellData ->
            new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getDate() != null ?
                cellData.getValue().getDate().format(dateFormatter) : ""));

        colStatus.setCellValueFactory(cellData ->
            new javafx.beans.property.SimpleStringProperty(getStatusLabel(cellData.getValue().getStatus())));

        colPaymentMethod.setCellValueFactory(cellData ->
            new javafx.beans.property.SimpleStringProperty(getPaymentMethodLabel(cellData.getValue().getPaymentMethod())));

        contributionTable.setItems(filteredContributions);
        contributionTable.setMinHeight(200);
    }

    private void setupFilters() {
        cbFilterType.getItems().addAll("Tous", "Événement", "Projet");
        cbFilterType.setValue("Tous");

        cbFilterType.setOnAction(event -> {
            String selectedType = cbFilterType.getValue();
            cbFilterEntity.getItems().clear();
            cbFilterEntity.setValue(null);

            if ("Tous".equals(selectedType)) {
                applyFilter();
                return;
            }

            try {
                if ("Événement".equals(selectedType)) {
                    List<Event> events = eventService.getAllEvents();
                    cbFilterEntity.getItems().addAll(events);
                    cbFilterEntity.setCellFactory(param -> new ListCell<Object>() {
                        @Override
                        protected void updateItem(Object item, boolean empty) {
                            super.updateItem(item, empty);
                            setText(empty || item == null ? null : ((Event) item).getName());
                        }
                    });
                    cbFilterEntity.setButtonCell(new ListCell<Object>() {
                        @Override
                        protected void updateItem(Object item, boolean empty) {
                            super.updateItem(item, empty);
                            setText(empty || item == null ? null : ((Event) item).getName());
                        }
                    });
                } else if ("Projet".equals(selectedType)) {
                    var projects = projectService.getAllProjects();
                    cbFilterEntity.getItems().addAll(projects);
                    cbFilterEntity.setCellFactory(param -> new ListCell<Object>() {
                        @Override
                        protected void updateItem(Object item, boolean empty) {
                            super.updateItem(item, empty);
                            if (empty || item == null) {
                                setText(null);
                            } else {
                                try {
                                    setText((String) item.getClass().getMethod("getName").invoke(item));
                                } catch (Exception e) {
                                    setText(item.toString());
                                }
                            }
                        }
                    });
                    cbFilterEntity.setButtonCell(new ListCell<Object>() {
                        @Override
                        protected void updateItem(Object item, boolean empty) {
                            super.updateItem(item, empty);
                            if (empty || item == null) {
                                setText(null);
                            } else {
                                try {
                                    setText((String) item.getClass().getMethod("getName").invoke(item));
                                } catch (Exception e) {
                                    setText(item.toString());
                                }
                            }
                        }
                    });
                }
            } catch (SQLException e) {
                showError("Erreur", "Impossible de charger les données: " + e.getMessage());
            }
        });

        cbFilterEntity.setOnAction(event -> applyFilter());
    }

    public void setMember(Member member) {
        this.member = member;
        loadMemberDetails();
        loadContributions();
    }

    private void loadMemberDetails() {
        if (member == null) return;

        lblMemberName.setText(member.getFullName());
        lblEmail.setText(member.getEmail() != null ? member.getEmail() : "-");
        lblPhone.setText(member.getPhone() != null ? member.getPhone() : "-");
        lblBirthDate.setText(member.getBirthDate() != null ? member.getBirthDate().format(dateFormatter) : "-");
        lblJoinDate.setText(member.getJoinDate() != null ? member.getJoinDate().format(dateFormatter) : "-");
        lblRole.setText(member.getRole() != null ? member.getRole() : "-");
        lblGroup.setText(member.getGroupName() != null ? member.getGroupName() : "-");
        lblAddress.setText(member.getAddress() != null ? member.getAddress() : "-");

        // Load avatar
        if (member.getAvatar() != null && member.getAvatar().length > 0) {
            try {
                Image avatarImage = new Image(new ByteArrayInputStream(member.getAvatar()));
                // Remove any default avatar SVG
                avatarContainer.getChildren().removeIf(node -> node instanceof Circle || node instanceof SVGPath);
                avatarImageView.setImage(avatarImage);
            } catch (Exception e) {
                setDefaultAvatar();
            }
        } else {
            setDefaultAvatar();
        }
    }

    private void setDefaultAvatar() {
        // Clear the ImageView
        avatarImageView.setImage(null);

        // Remove any previous default avatar SVG
        avatarContainer.getChildren().removeIf(node -> node instanceof Circle || node instanceof SVGPath);

        // Create a circular background
        Circle background = new Circle(60);
        background.setFill(Color.web("#e1e4e8"));

        // Create user icon SVG path (simplified user silhouette)
        SVGPath userIcon = new SVGPath();
        userIcon.setContent("M 0,-20 C -11,-20 -20,-11 -20,0 C -20,11 -11,20 0,20 C 11,20 20,11 20,0 C 20,-11 11,-20 0,-20 Z M 0,25 C -22,25 -40,35 -40,50 L -40,60 L 40,60 L 40,50 C 40,35 22,25 0,25 Z");
        userIcon.setFill(Color.web("#57606a"));
        userIcon.setScaleX(1.2);
        userIcon.setScaleY(1.2);
        userIcon.setTranslateY(5);

        avatarContainer.getChildren().addAll(background, userIcon);
    }

    private void loadContributions() {
        if (member == null) return;

        try {
            List<Contribution> contributions = contributionService.getAllContributions().stream()
                .filter(c -> c.getMemberId().equals(member.getId()))
                .collect(Collectors.toList());

            allContributions.setAll(contributions);
            applyFilter();
            calculateStatistics(contributions);
        } catch (SQLException e) {
            showError("Erreur", "Impossible de charger les cotisations: " + e.getMessage());
        }
    }

    private void applyFilter() {
        String filterType = cbFilterType.getValue();
        Object filterEntity = cbFilterEntity.getValue();

        if ("Tous".equals(filterType) || filterType == null) {
            filteredContributions.setAll(allContributions);
        } else {
            String entityTypeCode = getEntityTypeCode(filterType);

            if (filterEntity == null) {
                // Filter by type only
                filteredContributions.setAll(
                    allContributions.stream()
                        .filter(c -> entityTypeCode.equals(c.getEntityType()))
                        .collect(Collectors.toList())
                );
            } else {
                // Filter by type and specific entity
                int entityId = 0;
                try {
                    if ("EVENT".equals(entityTypeCode)) {
                        entityId = ((Event) filterEntity).getId();
                    } else {
                        entityId = (int) filterEntity.getClass().getMethod("getId").invoke(filterEntity);
                    }
                } catch (Exception e) {
                    showError("Erreur", "Impossible de filtrer: " + e.getMessage());
                    return;
                }

                final int finalEntityId = entityId;
                filteredContributions.setAll(
                    allContributions.stream()
                        .filter(c -> entityTypeCode.equals(c.getEntityType()) && c.getEntityId().equals(finalEntityId))
                        .collect(Collectors.toList())
                );
            }
        }
    }

    private void calculateStatistics(List<Contribution> contributions) {
        double totalPaid = contributions.stream()
            .filter(c -> "PAID".equals(c.getStatus()))
            .mapToDouble(Contribution::getAmount)
            .sum();

        double totalPending = contributions.stream()
            .filter(c -> "PENDING".equals(c.getStatus()))
            .mapToDouble(Contribution::getAmount)
            .sum();

        double totalContributed = totalPaid + totalPending;

        lblTotalContributed.setText(numberFormatter.format(totalContributed) + " CFA");
        lblTotalPaid.setText(numberFormatter.format(totalPaid) + " CFA");
        lblTotalPending.setText(numberFormatter.format(totalPending) + " CFA");
        lblContributionCount.setText(String.valueOf(contributions.size()));
    }

    private String getEntityName(Contribution contribution) throws SQLException {
        String entityType = contribution.getEntityType();
        int entityId = contribution.getEntityId();

        if ("EVENT".equals(entityType)) {
            Event event = eventService.getEventById(entityId);
            return event != null ? event.getName() : "Inconnu";
        } else if ("PROJECT".equals(entityType)) {
            var project = projectService.getProjectById(entityId);
            if (project != null) {
                try {
                    return (String) project.getClass().getMethod("getName").invoke(project);
                } catch (Exception e) {
                    return "Inconnu";
                }
            }
        }
        return "Inconnu";
    }

    private String getEntityTypeLabel(String code) {
        if (code == null) return "N/A";
        switch (code) {
            case "EVENT": return "Événement";
            case "PROJECT": return "Projet";
            default: return code;
        }
    }

    private String getEntityTypeCode(String frenchLabel) {
        switch (frenchLabel) {
            case "Événement": return "EVENT";
            case "Projet": return "PROJECT";
            default: return frenchLabel;
        }
    }

    private String getStatusLabel(String code) {
        if (code == null) return "N/A";
        switch (code) {
            case "PAID": return "Payé";
            case "PENDING": return "En attente";
            default: return code;
        }
    }

    private String getPaymentMethodLabel(String code) {
        if (code == null) return "N/A";
        return switch (code) {
            case "CASH" -> "Espèces";
            case "WAVE" -> "Wave";
            case "ORANGE_MONEY" -> "Orange Money";
            case "BANK_TRANSFER" -> "Virement";
            default -> code;
        };
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) lblMemberName.getScene().getWindow();
        stage.close();
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
