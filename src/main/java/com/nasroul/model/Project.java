package com.nasroul.model;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class Project extends SyncableEntity {
    private Integer id;
    private String name;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Double budget;
    private Double targetBudget;
    private Integer managerId;
    private String managerName;
    private Double contributionTarget;

    public Project() {
        super();
        this.status = "PLANNING";
        this.budget = 0.0;
        this.targetBudget = 0.0;
        this.contributionTarget = 0.0;
    }

    @Override
    public Map<String, Object> getFieldValuesForHash() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", name);
        fields.put("description", description);
        fields.put("startDate", startDate);
        fields.put("endDate", endDate);
        fields.put("status", status);
        fields.put("budget", budget);
        fields.put("targetBudget", targetBudget);
        fields.put("managerId", managerId);
        fields.put("contributionTarget", contributionTarget);
        return fields;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getBudget() {
        return budget;
    }

    public void setBudget(Double budget) {
        this.budget = budget;
    }

    public Double getTargetBudget() {
        return targetBudget;
    }

    public void setTargetBudget(Double targetBudget) {
        this.targetBudget = targetBudget;
    }

    public Integer getManagerId() {
        return managerId;
    }

    public void setManagerId(Integer managerId) {
        this.managerId = managerId;
    }

    public String getManagerName() {
        return managerName;
    }

    public void setManagerName(String managerName) {
        this.managerName = managerName;
    }

    public Double getContributionTarget() {
        return contributionTarget;
    }

    public void setContributionTarget(Double contributionTarget) {
        this.contributionTarget = contributionTarget;
    }
}
