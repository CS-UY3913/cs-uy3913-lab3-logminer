package logminer;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public final class LogMinerTest {

    @Test
    void parser_validLine() throws Exception {
        LogRecord r = CsvLogParser.parseLine("2026-03-01T10:00:00Z,ana,LOGIN,0,200");
        assertEquals("ana", r.userId());
        assertEquals(LogRecord.Action.LOGIN, r.action());
        assertEquals(0, r.bytes());
        assertEquals(200, r.status());
    }

    @Test
    void parser_rejectsNegativeBytes() {
        LogParseException ex = assertThrows(LogParseException.class,
                () -> CsvLogParser.parseLine("2026-03-01T11:10:00Z,ana,UPLOAD,-5,200"));
        assertTrue(ex.getMessage().contains("non-negative"));
    }

    @Test
    void parser_rejectsWrongFieldCount() {
        assertThrows(LogParseException.class,
                () -> CsvLogParser.parseLine("bad,line,that,will_fail"));
    }

    @Test
    void endToEnd_sampleData() throws Exception {
        Path tmp = Files.createTempDirectory("lab3");
        Path in = tmp.resolve("logs");
        Path out = tmp.resolve("out");
        Files.createDirectories(in);

        Files.writeString(in.resolve("log1.csv"), """
            timestamp,userId,action,bytes,status
            2026-03-01T10:00:00Z,ana,LOGIN,0,200
            2026-03-01T10:02:00Z,ana,UPLOAD,120,201
            2026-03-01T10:05:00Z,sam,DOWNLOAD,250,200
            2026-03-01T10:06:30Z,ana,DOWNLOAD,250,200
            """.strip() + "\n");

        Files.writeString(in.resolve("log2.csv"), """
            timestamp,userId,action,bytes,status
            2026-03-01T11:00:00Z,sam,UPLOAD,512,500
            2026-03-01T11:05:00Z,mina,DOWNLOAD,128,200
            bad,line,that,will_fail
            2026-03-01T11:10:00Z,ana,UPLOAD,-5,200
            """.strip() + "\n");

        int exit = new LogMinerApp().run(new String[]{
                "--input", in.toString(),
                "--output", out.toString(),
                "--threads", "4",
                "--topUsers", "5"
        });
        assertEquals(0, exit);

        String users = Files.readString(out.resolve("users.csv"));
        assertTrue(users.contains("ana,3,370"));
        assertTrue(users.contains("sam,2,762"));

        String json = Files.readString(out.resolve("summary.json"));
        assertTrue(json.contains("\"validEvents\": 6"));
        assertTrue(json.contains("\"invalidLines\": 2"));
        assertTrue(json.contains("\"totalBytes\": 1260"));

        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(out.resolve("summary.bin"))))) {
            byte[] magic = dis.readNBytes(4);
            assertArrayEquals("L3SB".getBytes(), magic);
            int version = dis.readUnsignedByte();
            assertEquals(1, version);
        }
    }
}
