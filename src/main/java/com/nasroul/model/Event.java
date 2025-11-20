package com.nasroul.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class Event extends SyncableEntity {
    private Integer id;
    private String name;
    private String description;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String location;
    private String status;
    private Integer organizerId;
    private String organizerName;
    private Integer maxCapacity;
    private boolean active;
    private Double contributionTarget;

    public Event() {
        super();
        this.status = "PLANNED";
        this.active = true;
        this.contributionTarget = 0.0;
    }

    @Override
    public Map<String, Object> getFieldValuesForHash() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", name);
        fields.put("description", description);
        fields.put("startDate", startDate);
        fields.put("endDate", endDate);
        fields.put("location", location);
        fields.put("status", status);
        fields.put("organizerId", organizerId);
        fields.put("maxCapacity", maxCapacity);
        fields.put("active", active);
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

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getOrganizerId() {
        return organizerId;
    }

    public void setOrganizerId(Integer organizerId) {
        this.organizerId = organizerId;
    }

    public String getOrganizerName() {
        return organizerName;
    }

    public void setOrganizerName(String organizerName) {
        this.organizerName = organizerName;
    }

    public Integer getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(Integer maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Double getContributionTarget() {
        return contributionTarget;
    }

    public void setContributionTarget(Double contributionTarget) {
        this.contributionTarget = contributionTarget;
    }
}
