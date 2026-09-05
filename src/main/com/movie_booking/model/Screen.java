package com.movie_booking.model;

public class Screen {
    private int screenId;
    private int theatreId;
    private String name;
    private int capacity;
    private ScreenStatus status;

    public Screen() {
    }

    public Screen(int screenId, int theatreId, String name, int capacity, ScreenStatus status) {
        this.screenId = screenId;
        this.theatreId = theatreId;
        this.name = name;
        this.capacity = capacity;
        this.status = status;
    }

    public int getScreenId() { return screenId; }
    public void setScreenId(int screenId) { this.screenId = screenId; }
    public int getTheatreId() { return theatreId; }
    public void setTheatreId(int theatreId) { this.theatreId = theatreId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public ScreenStatus getStatus() { return status; }
    public void setStatus(ScreenStatus status) { this.status = status; }
}
