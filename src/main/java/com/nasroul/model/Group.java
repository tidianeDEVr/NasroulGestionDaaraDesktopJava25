package com.nasroul.model;

import java.util.HashMap;
import java.util.Map;

public class Group extends SyncableEntity {
    private Integer id;
    private String name;
    private String description;
    private boolean active;

    public Group() {
        super();
        this.active = true;
    }

    @Override
    public Map<String, Object> getFieldValuesForHash() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", name);
        fields.put("description", description);
        fields.put("active", active);
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
