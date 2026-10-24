package com.example.waitlist.domain;

import java.util.List;

/** Consistent point-in-time view of the offering (taken under the service lock). */
public record OfferingSnapshot(String code, int capacity, int seatsAvailable,
                               String prerequisite, List<String> enrolled, List<String> waitlist) {
}
