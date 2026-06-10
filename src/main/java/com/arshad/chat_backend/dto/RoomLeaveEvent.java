package com.arshad.chat_backend.dto;

public class RoomLeaveEvent {
    private final String roomName;
    private final String payload;

    public RoomLeaveEvent(String roomName, String payload) {
        this.roomName = roomName;
        this.payload = payload;
    }

    public String getRoomName() { return roomName; }
    public String getPayload() { return payload; }
}