package com.hearintent.backend.logging;

import com.hearintent.backend.config.HearIntentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class FlatFileLogService {

    private static final Logger log = LoggerFactory.getLogger(FlatFileLogService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HearIntentProperties properties;
    private final Map<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();

    public FlatFileLogService(HearIntentProperties properties) {
        this.properties = properties;
        ensureDirectoryExists();
    }

    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(Paths.get(properties.logging().flatLogDir()));
            Files.createDirectories(Paths.get(properties.logging().flatLogDir(), "sessions"));
        } catch (IOException e) {
            log.error("Failed to create flat log directory: {}", e.getMessage());
        }
    }

    /**
     * Appends a single line to the day's audit log file using an exclusive
     * NIO FileChannel write under a per-file ReentrantLock, guaranteeing no
     * interleaved writes or torn lines from concurrent requests.
     */
    public void appendAuditLine(String category, String message) {
        String today = DATE_FORMAT.format(Instant.now().atZone(java.time.ZoneOffset.UTC));
        Path path = Paths.get(properties.logging().flatLogDir(), "audit-" + today + ".log");
        String line = String.format("[%s] [%s] %s%n", Instant.now(), category, message);
        writeAtomically(path, line);
    }

    public void appendErrorLine(String message, Throwable throwable) {
        String today = DATE_FORMAT.format(Instant.now().atZone(java.time.ZoneOffset.UTC));
        Path path = Paths.get(properties.logging().flatLogDir(), "error-" + today + ".log");
        String stack = throwable != null ? throwable.toString() : "";
        String line = String.format("[%s] %s | %s%n", Instant.now(), message, stack);
        writeAtomically(path, line);
    }

    /**
     * Writes (or appends to) a Markdown session report capturing the
     * transcript/intent timeline for a given speech session.
     */
    public void appendSessionMarkdown(String sessionId, String markdownLine) {
        Path path = Paths.get(properties.logging().flatLogDir(), "sessions", "session-" + sanitize(sessionId) + ".md");
        writeAtomically(path, markdownLine + System.lineSeparator());
    }

    private void writeAtomically(Path path, String content) {
        ReentrantLock lock = fileLocks.computeIfAbsent(path.toString(), p -> new ReentrantLock());
        lock.lock();
        try {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(
                    path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
        } catch (IOException e) {
            log.error("Failed to write flat log file {}: {}", path, e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private String sanitize(String input) {
        return input.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
