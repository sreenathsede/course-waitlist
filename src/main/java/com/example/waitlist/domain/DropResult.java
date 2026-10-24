package com.example.waitlist.domain;

import java.util.List;

/**
 * @param learnerId       the learner that dropped
 * @param droppedFrom     whether the learner held a seat or only a waitlist place
 * @param promotedLearners learners moved from the waitlist into enrollment because of this drop (FIFO order)
 */
public record DropResult(String learnerId, DroppedFrom droppedFrom, List<String> promotedLearners) {
    /** Where the dropped learner was. */
    public enum DroppedFrom { ENROLLMENT, WAITLIST }
}
