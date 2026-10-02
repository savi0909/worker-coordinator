package com.example.workeridclient;

public class LeaseUnavailableException extends IllegalStateException {
    public LeaseUnavailableException(String message) {
        super(message);
    }
}
