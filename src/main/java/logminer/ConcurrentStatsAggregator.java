package logminer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe aggregator that accumulates statistics from multiple log files
 * being processed concurrently.
 *
 * Thread-safety strategy:
 *   - Global counters use LongAdder (scalable under contention).
 *   - Per-action, per-status, and per-user maps use ConcurrentHashMap.
 *   - All mutations happen through thread-safe operations (computeIfAbsent + LongAdder).
 */
public final class ConcurrentStatsAggregator {

    // TODO: Declare three private final LongAdder fields:
    //   - validEvents
    //   - invalidLines
    //   - totalBytes

    // TODO: Declare three private final ConcurrentHashMap fields:
    //   - byAction:  ConcurrentHashMap<LogRecord.Action, LongAdder>
    //   - byStatus:  ConcurrentHashMap<Integer, LongAdder>
    //   - byUser:    ConcurrentHashMap<String, UserStats>

    /**
     * Records a valid log event. Called concurrently from multiple threads.
     * Must be thread-safe.
     */
    public void recordValid(LogRecord r) {
        // TODO: Increment validEvents.
        // TODO: Add r.bytes() to totalBytes.

        // TODO: Use computeIfAbsent on byAction to get or create a LongAdder, then increment it.
        // TODO: Use computeIfAbsent on byStatus to get or create a LongAdder, then increment it.

        // TODO: Use computeIfAbsent on byUser to get or create a UserStats, then call record(r) on it.
    }

    /**
     * Records that one invalid line was encountered. Called concurrently.
     */
    public void recordInvalid() {
        // TODO: Increment invalidLines.
    }

    /**
     * Produces a deterministic Summary snapshot from the accumulated data.
     * Called after all tasks have completed (single-threaded at this point).
     */
    public Summary toSummary(CliConfig cfg) {
        // TODO: Build a TreeMap<String, Summary.UserSummary> from byUser (sorted by userId).

        // TODO: Build a TreeMap<String, Long> for actions.
        //       Iterate over all LogRecord.Action values.
        //       Use getOrDefault to handle actions with zero count.

        // TODO: Build a TreeMap<String, Long> for statuses (key = Integer.toString(statusCode)).

        // TODO: Build topUsersByBytes:
        //       Stream the user summaries, map to Summary.UserBytes,
        //       sort by bytes descending (then userId ascending for ties),
        //       limit to cfg.topUsers().

        // TODO: Return a new Summary with all the assembled data.

        return null; // Placeholder
    }

    /**
     * Inner class for per-user statistics. Uses LongAdder for thread-safe updates.
     */
    private static final class UserStats {
        private final LongAdder events = new LongAdder();
        private final LongAdder bytes = new LongAdder();

        void record(LogRecord r) {
            events.increment();
            bytes.add(r.bytes());
        }

        Summary.UserSummary toUserSummary(String userId) {
            return new Summary.UserSummary(userId, events.sum(), bytes.sum());
        }
    }
}
