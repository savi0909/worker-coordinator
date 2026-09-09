package com.example.workeridclient;

public class ClockRollbackException extends IllegalStateException {
    public ClockRollbackException(long previous, long current) {
        super("clock moved backwards from " + previous + " to " + current + " milliseconds");
    }
}
