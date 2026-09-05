package com.movie_booking.model;

import java.time.LocalDateTime;

public class Theatre {
    private int theatreId;
    private String name;
    private String location;
    private TheatreStatus status;
    private LocalDateTime createdAt;

    public Theatre() {
    }

    public Theatre(int theatreId, String name, String location, TheatreStatus status,
            LocalDateTime createdAt) {
        this.theatreId = theatreId;
        this.name = name;
        this.location = location;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getTheatreId() { return theatreId; }
    public void setTheatreId(int theatreId) { this.theatreId = theatreId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public TheatreStatus getStatus() { return status; }
    public void setStatus(TheatreStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
