package com.nasroul.model;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class Contribution extends SyncableEntity {
    private Integer id;
    private Integer memberId;
    private String memberName;
    private String entityType; // "EVENT" or "PROJECT"
    private Integer entityId;
    private String entityName;
    private Double amount;
    private LocalDate date;
    private String status; // "PENDING", "PAID", "OVERDUE"
    private String paymentMethod;
    private String notes;

    public Contribution() {
        super();
        this.status = "PENDING";
        this.date = LocalDate.now();
    }

    @Override
    public Map<String, Object> getFieldValuesForHash() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("memberId", memberId);
        fields.put("entityType", entityType);
        fields.put("entityId", entityId);
        fields.put("amount", amount);
        fields.put("date", date);
        fields.put("status", status);
        fields.put("paymentMethod", paymentMethod);
        fields.put("notes", notes);
        return fields;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getMemberId() {
        return memberId;
    }

    public void setMemberId(Integer memberId) {
        this.memberId = memberId;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
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

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
