# Lab 3: Concurrency + File I/O — LogMiner

## Objective

This lab focuses on building a multithreaded data processing pipeline using the JDK concurrency framework and file I/O APIs. You will implement **LogMiner**, a concurrent CSV log summariser that processes multiple input files in parallel and produces structured output reports.

By the end of this lab, you will be able to:

* Design a multithreaded program using `ExecutorService` with correct lifecycle management.
* Implement a thread-safe shared aggregator using `ConcurrentHashMap` and `LongAdder`.
* Justify thread safety using strategies such as confinement, thread-safe data types, and synchronisation.
* Perform streaming, buffered file I/O with `BufferedReader`/`BufferedWriter` and `try-with-resources`.
* Write portable binary data using `DataOutputStream`.
* Produce deterministic CSV and JSON outputs from concurrent inputs.

---

## Section 1: Setup

### 1. Install the Required Tools

* **Java 17** (Temurin-17 is recommended)
* **IntelliJ IDEA**
* **Apache Maven**

### 2. Clone the Starter Code Repository

```bash
git clone https://github.com/CS-UY3913/cs-uy3913-lab3-logminer.git
cd cs-uy3913-lab3-logminer
```

### 3. Open the Project in IntelliJ

* Open IntelliJ and select **Open**.
* Navigate to the cloned folder.
* Ensure IntelliJ recognizes the `pom.xml` file as a Maven project.
* Let Maven finish indexing and resolving dependencies (SLF4J, Logback, and JUnit 5).

---

## Section 2: Part 1 – Domain Model & Custom Exception

### Step 1: Review Provided Files

These files are provided and should **not** be modified:

* `LogRecord.java` — An immutable record representing a single parsed log event.
* `Summary.java` — A record holding the final aggregated data for output generation.
* `FileTaskResult.java` — A record returned by each file-processing task.
* `CliConfig.java` — Parses command-line arguments.
* `Main.java` — The entry point (delegates to `LogMinerApp`).

### Step 2: Implement `LogParseException.java`

Implement the following in:

```
src/main/java/logminer/
```

* Must extend `Exception` (this is a checked exception).
* Create a constructor that takes a `String message`.
* Call `super(message)` to pass the error description to the parent class.

---

## Section 3: Part 2 – CSV Parsing

### Step 1: Implement `CsvLogParser.java`

This class parses a single CSV line into a `LogRecord`.

#### Input Format

Each CSV file has a header row followed by data rows:

```
timestamp,userId,action,bytes,status
```

* `timestamp`: ISO-8601 string (kept as opaque string — no time parsing required).
* `userId`: non-empty string.
* `action`: one of `LOGIN`, `LOGOUT`, `UPLOAD`, `DOWNLOAD`.
* `bytes`: integer ≥ 0.
* `status`: integer (e.g., 200, 201, 500).

#### Validation Rules

* If the line is null, throw `LogParseException` with `"null line"`.
* Trim the line. If empty, throw `LogParseException` with `"empty line"`.
* Split by comma. If field count ≠ 5, throw `LogParseException` with `"expected 5 fields, got N"`.
* Trim each field. Validate `timestamp` and `userId` are non-empty.
* Parse `action` using `LogRecord.Action.valueOf()`. On failure, throw `LogParseException`.
* Parse `bytes` as an integer. Catch `NumberFormatException` and rethrow as `LogParseException`. Enforce `bytes >= 0`.
* Parse `status` as an integer. Catch `NumberFormatException` and rethrow as `LogParseException`.

---

## Section 4: Part 3 – Thread-Safe Aggregation

### Step 1: Implement `ConcurrentStatsAggregator.java`

This is the shared data structure that all file-processing threads write to concurrently.

#### Fields

* Global counters using `LongAdder`: `validEvents`, `invalidLines`, `totalBytes`.
* Maps using `ConcurrentHashMap`:
  * `byAction`: `ConcurrentHashMap<LogRecord.Action, LongAdder>`
  * `byStatus`: `ConcurrentHashMap<Integer, LongAdder>`
  * `byUser`: `ConcurrentHashMap<String, UserStats>`

#### Thread-Safe Updates

* Use `computeIfAbsent()` to get or create `LongAdder`/`UserStats` entries atomically.
* All mutations happen through thread-safe operations — no unsynchronised mutable state escapes.

#### Deterministic Summary

Implement `toSummary()`:

* Use `TreeMap` for sorted output ordering (by userId, by action name, by status code).
* Build `topUsersByBytes` by sorting users by bytes descending, then userId ascending for ties.

**Document your thread-safety argument.** You should be able to say: *"All shared state is in concurrent maps + LongAdders; mutations happen through thread-safe operations."*

---

## Section 5: Part 4 – Concurrent File Processing

### Step 1: Implement `ErrorSink.java`

This class writes invalid lines to `errors.csv`. It must be thread-safe.

* Open a `BufferedWriter` using `Files.newBufferedWriter()`.
* Write the header: `file,lineNumber,error,rawLine`.
* The `record()` method must be `synchronized` so concurrent writers do not interleave lines.
* The `close()` method must also be `synchronized`.
* Implement `AutoCloseable` so it works with `try-with-resources`.

### Step 2: Implement `FileDiscovery.java`

* Use `Files.list()` inside a `try-with-resources` block.
* Filter to `.csv` files only.
* Sort by filename and return a `List<Path>`.

### Step 3: Implement `LogFileTask.java`

This class implements `Callable<FileTaskResult>` — one instance per input file.

* Open the file using `Files.newBufferedReader()` with `try-with-resources`.
* Read and validate the header line (must match `CsvLogParser.REQUIRED_HEADER`).
* For each data line:
  * Parse with `CsvLogParser.parseLine()`.
  * On success: call `agg.recordValid()`.
  * On `LogParseException`: increment invalid counter, call `agg.recordInvalid()`, and call `errors.record()`.
* Return a `FileTaskResult` with the file name, lines read, and invalid count.

### Step 4: Implement Concurrent Execution in `LogMinerApp.java`

Complete the `// --- SECTION 3: CONCURRENT EXECUTION ---` block:

1. Create a fixed thread pool: `Executors.newFixedThreadPool(config.threads())`.
2. Use `try-with-resources` with `ErrorSink.create()`.
3. Submit one `LogFileTask` per input file to the pool.
4. Wait for all tasks using `Future.get()`:
   * On `InterruptedException`: restore interrupt flag, `shutdownNow()`, return 3.
   * On `ExecutionException`: log the cause, `shutdownNow()`, return 3.
5. In the `finally` block: call `pool.shutdown()`.

---

## Section 6: Part 5 – Output Writers

### Step 1: Implement `UsersCsvWriter.java`

Write `users.csv` with header and sorted rows:

```
userId,events,totalBytes
ana,3,370
mina,1,128
sam,2,762
```

Use `Files.newBufferedWriter()` with `try-with-resources`.

### Step 2: Implement `SummaryJsonWriter.java`

Write valid JSON (RFC 8259). Use the provided `jsonString()` and `objLongMap()` helpers.

Required structure:

```json
{
  "meta": { "generatedBy": "LogMiner", "inputDir": "...", "threads": 4 },
  "totals": { "validEvents": 6, "invalidLines": 2, "totalBytes": 1260 },
  "counts": {
    "byAction": { "DOWNLOAD": 3, "LOGIN": 1, "LOGOUT": 0, "UPLOAD": 2 },
    "byStatus": { "200": 4, "201": 1, "500": 1 }
  },
  "perUser": [
    {"userId": "ana", "events": 3, "bytes": 370},
    {"userId": "mina", "events": 1, "bytes": 128},
    {"userId": "sam", "events": 2, "bytes": 762}
  ],
  "topUsersByBytes": [
    {"userId": "sam", "bytes": 762},
    {"userId": "ana", "bytes": 370},
    {"userId": "mina", "bytes": 128}
  ]
}
```

### Step 3: Implement `SummaryBinaryWriter.java`

Write a portable binary snapshot using `DataOutputStream`:

| Field | Type | Description |
|-------|------|-------------|
| Magic | 4 bytes | ASCII `"L3SB"` |
| Version | 1 byte | `1` |
| validEvents | int (4 bytes) | Total valid events |
| invalidLines | int (4 bytes) | Total invalid lines |
| totalBytes | long (8 bytes) | Total bytes transferred |
| userCount | int (4 bytes) | Number of user records |
| *Per user:* | | |
| userIdLen | unsigned short (2 bytes) | UTF-8 byte length of userId |
| userId | n bytes | UTF-8 encoded userId |
| events | int (4 bytes) | User's event count |
| bytes | long (8 bytes) | User's total bytes |

---

## Section 7: Part 6 – Testing & Submission

### Step 1: Run the Main Application

Run `Main.java` with arguments. From the project root:

```bash
mvn compile exec:java -Dexec.mainClass="logminer.Main" \
  -Dexec.args="--input src/main/resources/logs --output target/output --threads 4 --topUsers 5"
```

Or configure a Run Configuration in IntelliJ with program arguments:

```
--input src/main/resources/logs --output target/output --threads 4 --topUsers 5
```

If implemented correctly, you should see:

```
Wrote outputs to: target/output
```

And the output directory should contain:

* `users.csv` — per-user totals sorted by userId
* `summary.json` — full structured report
* `summary.bin` — binary snapshot
* `errors.csv` — logged invalid lines

#### Expected `users.csv`:

```
userId,events,totalBytes
ana,3,370
mina,1,128
sam,2,762
```

#### Expected `errors.csv`:

```
file,lineNumber,error,rawLine
"log2.csv",4,"expected 5 fields, got 4","bad,line,that,will_fail"
"log2.csv",5,"bytes must be non-negative: -5","2026-03-01T11:10:00Z,ana,UPLOAD,-5,200"
```

### Step 2: Run Local Unit Tests

A basic test suite (`LogMinerTest.java`) is provided.

Run:

```bash
mvn clean test
```

Expected output:

```
Tests run: 4, Failures: 0, Errors: 0, BUILD SUCCESS
```

### Step 3: Create Submission ZIP

Run this command from your project root and upload to Gradescope:

```bash
zip -r submission.zip src/ pom.xml
```

---

## Section 8: Project Structure & Troubleshooting

### Project Structure

```
cs-uy3913-lab3-logminer/
├── pom.xml
├── README.md
└── src/
    ├── main/java/logminer/
    │   ├── CliConfig.java                       ← CLI parsing (Do not modify)
    │   ├── ConcurrentStatsAggregator.java
    │   ├── CsvLogParser.java
    │   ├── ErrorSink.java
    │   ├── FileDiscovery.java
    │   ├── FileTaskResult.java (Record)         ← Do not modify
    │   ├── LogFileTask.java
    │   ├── LogMinerApp.java
    │   ├── LogParseException.java
    │   ├── LogRecord.java (Record)              ← Do not modify
    │   ├── Main.java                            ← Entry point (Do not modify)
    │   ├── Summary.java (Record)                ← Do not modify
    │   ├── SummaryBinaryWriter.java
    │   ├── SummaryJsonWriter.java
    │   └── UsersCsvWriter.java
    ├── main/resources/logs/
    │   ├── log1.csv                             ← Sample data
    │   └── log2.csv                             ← Sample data (with bad rows)
    └── test/java/logminer/
        └── LogMinerTest.java
```

### Troubleshooting

**Red underlines in test files**

* This is expected initially because your TODO methods haven't been implemented yet.

**Maven issues**

* Right-click `pom.xml`
* Select **Reload Project**

**Program hangs after processing**

* This usually means the `ExecutorService` was not shut down. Make sure you call `pool.shutdown()` in a `finally` block.

**Non-deterministic outputs / flaky tests**

* Make sure you sort map keys (use `TreeMap`) and user lists before writing outputs.

---

## Section 9: Submission Checklist & Grading

### Submission Checklist

* [ ] **Package Names**: All `.java` files include `package logminer;`.
* [ ] **Custom Exception**: `LogParseException` extends `Exception` with a message constructor.
* [ ] **CSV Parsing**: `CsvLogParser` validates all fields and throws `LogParseException` on invalid input.
* [ ] **Thread-Safe Aggregator**: Uses `ConcurrentHashMap` + `LongAdder`; no data races.
* [ ] **Concurrent Execution**: Uses `ExecutorService` with correct shutdown semantics.
* [ ] **Synchronised Error Sink**: `ErrorSink.record()` is `synchronized` to prevent interleaving.
* [ ] **Streaming I/O**: Uses `BufferedReader`/`BufferedWriter` with `try-with-resources`.
* [ ] **Deterministic Outputs**: JSON and CSV outputs are sorted and match expected values.
* [ ] **Binary Snapshot**: `summary.bin` follows the specified format exactly.
* [ ] **All Tests Pass**: Running `mvn clean test` produces `BUILD SUCCESS`.
* [ ] **ZIP Structure**: Your ZIP contains only the `src/` folder and `pom.xml`.

### Grading Criteria

**CSV Ingestion + Validation (20 pts)**

* Correct header handling; correct parsing; rejects invalid lines without crashing; correct invalid count.

**Concurrency Architecture (20 pts)**

* Uses `ExecutorService` (not manual thread-per-file); correct shutdown; handles task failures deterministically.

**Thread Safety + Synchronisation (20 pts)**

* Correct use of thread-safe types (`ConcurrentHashMap`, `LongAdder`) and synchronised error writing; no data races in aggregator.

**File I/O Quality (15 pts)**

* Uses buffered readers/writers; streamed processing; `try-with-resources`; creates output directories.

**Output Correctness (15 pts)**

* Matches required schemas; deterministic ordering; binary format exactly as specified (magic/version/fields).

**Testing + Code Quality (10 pts)**

* All provided tests pass; readable structure; thread-safety argument documented.
