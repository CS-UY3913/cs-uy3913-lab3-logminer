package logminer;

import java.util.Locale;

/**
 * Parses a single CSV line into a LogRecord.
 * The expected CSV format (no quoted fields):
 *   timestamp,userId,action,bytes,status
 */
public final class CsvLogParser {
    private CsvLogParser() {}

    public static final String REQUIRED_HEADER = "timestamp,userId,action,bytes,status";

    /**
     * Parses a single CSV data line into a LogRecord.
     *
     * @param line the raw CSV line (not a header)
     * @return a valid LogRecord
     * @throws LogParseException if the line is invalid
     */
    public static LogRecord parseLine(String line) throws LogParseException {
        // TODO: If line is null, throw LogParseException with message "null line".
        // TODO: Trim whitespace. If the trimmed line is empty, throw LogParseException with "empty line".

        // TODO: Split the line by comma using split(",", -1).
        // TODO: If the number of fields is not 5, throw LogParseException:
        //       "expected 5 fields, got " + actual count.

        // TODO: Trim each field. Validate:
        //   - timestamp must not be empty ("timestamp empty")
        //   - userId must not be empty ("userId empty")

        // TODO: Parse the action field (uppercase it first).
        //       Use LogRecord.Action.valueOf(). If it fails (IllegalArgumentException),
        //       throw LogParseException: "unknown action: " + raw value.

        // TODO: Parse bytes as an integer.
        //       Catch NumberFormatException and rethrow as LogParseException: "bytes not an int: " + raw.
        //       If bytes < 0, throw LogParseException: "bytes must be non-negative: " + value.

        // TODO: Parse status as an integer.
        //       Catch NumberFormatException and rethrow as LogParseException: "status not an int: " + raw.

        // TODO: Return a new LogRecord with the parsed values.

        return null; // Placeholder
    }
}
