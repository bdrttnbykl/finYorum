package com.finyorum.dto;

public record SignalChangeResponse(
        String previous,
        String current,
        boolean changed
) {
}
