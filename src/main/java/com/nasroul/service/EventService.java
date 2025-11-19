package com.nasroul.service;

import com.nasroul.dao.EventDAO;
import com.nasroul.model.Event;

import java.sql.SQLException;
import java.util.List;

public class EventService {
    private final EventDAO eventDAO;

    public EventService() {
        this.eventDAO = new EventDAO();
    }

    public void createEvent(Event event) throws SQLException {
        validateEvent(event);
        eventDAO.create(event);
    }

    public Event getEventById(int id) throws SQLException {
        return eventDAO.findById(id);
    }

    public List<Event> getAllEvents() throws SQLException {
        return eventDAO.findAll();
    }

    public void updateEvent(Event event) throws SQLException {
        validateEvent(event);
        eventDAO.update(event);
    }

    public void deleteEvent(int id) throws SQLException {
        eventDAO.delete(id);
    }

    public void bulkCreate(List<Event> events) throws SQLException {
        for (Event event : events) {
            try {
                createEvent(event);
            } catch (SQLException | IllegalArgumentException e) {
                System.err.println("Failed to import event: " + event.getName() + " - " + e.getMessage());
            }
        }
    }

    private void validateEvent(Event event) {
        if (event.getName() == null || event.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Event name is required");
        }
        if (event.getStartDate() == null) {
            throw new IllegalArgumentException("Start date is required");
        }
    }
}
