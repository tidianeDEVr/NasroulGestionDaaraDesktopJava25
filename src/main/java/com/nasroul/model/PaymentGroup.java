package com.nasroul.model;

import java.util.HashMap;
import java.util.Map;

public class PaymentGroup extends SyncableEntity {
    private Integer id;
    private Integer groupId;
    private String groupName;          // For display (from JOIN)
    private String entityType;         // "EVENT" or "PROJECT"
    private Integer entityId;
    private String entityName;         // For display (from JOIN)
    private Double amount;             // Amount per member

    public PaymentGroup() {
        super();
        this.amount = 0.0;
    }

    @Override
    public Map<String, Object> getFieldValuesForHash() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("groupId", groupId);
        fields.put("entityType", entityType);
        fields.put("entityId", entityId);
        fields.put("amount", amount);
        return fields;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getGroupId() {
        return groupId;
    }

    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Integer getEntityId() {
        return entityId;
    }

    public void setEntityId(Integer entityId) {
        this.entityId = entityId;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "PaymentGroup{" +
                "id=" + id +
                ", groupName='" + groupName + '\'' +
                ", entityType='" + entityType + '\'' +
                ", entityName='" + entityName + '\'' +
                ", amount=" + amount +
                '}';
    }
}
