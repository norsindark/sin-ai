package com.sinai.media.internal;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import com.sinai.media.config.MediaProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FfmpegRunner {

    private static final int OUTPUT_TAIL_LIMIT = 4000;
    private static final Duration TERMINATION_WAIT = Duration.ofSeconds(5);

    private final MediaProperties properties;

    public void run(List<String> arguments) {
        var command = buildCommand(properties.ffmpegPath(), arguments);
        var tail = new ByteArrayOutputStream();
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            closeQuietly(process.getOutputStream());
            var stream = process.getInputStream();
            var reader = Thread.ofVirtual().start(() -> drain(stream, tail));
            var finished = process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                terminate(process);
                reader.join(1000);
                throw new SinAiException(ErrorCode.MEDIA_PROCESSING_ERROR,
                        "ffmpeg timed out after " + properties.timeout() + ": " + snapshot(tail));
            }
            reader.join(5000);
            var exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new SinAiException(ErrorCode.MEDIA_PROCESSING_ERROR,
                        "ffmpeg exited with code " + exitCode + ": " + snapshot(tail));
            }
        } catch (IOException e) {
            throw new SinAiException(ErrorCode.MEDIA_PROCESSING_ERROR,
                    "ffmpeg failed to run: " + snapshot(tail), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (Objects.nonNull(process)) {
                terminate(process);
            }
            throw new SinAiException(ErrorCode.MEDIA_PROCESSING_ERROR,
                    "ffmpeg interrupted: " + snapshot(tail), e);
        }
    }

    static List<String> buildCommand(String ffmpegPath, List<String> arguments) {
        var command = new ArrayList<String>();
        command.add(ffmpegPath);
        command.add("-hide_banner");
        command.add("-nostdin");
        command.add("-y");
        command.addAll(arguments);
        return List.copyOf(command);
    }

    private static void terminate(Process process) {
        process.destroyForcibly();
        try {
            process.waitFor(TERMINATION_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(OutputStream stream) {
        try {
            stream.close();
        } catch (IOException e) {
            return;
        }
    }

    private static void drain(InputStream stream, ByteArrayOutputStream tail) {
        try (var in = stream) {
            var buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                synchronized (tail) {
                    tail.write(buffer, 0, read);
                    if (tail.size() > OUTPUT_TAIL_LIMIT * 2) {
                        var bytes = tail.toByteArray();
                        tail.reset();
                        tail.write(bytes, bytes.length - OUTPUT_TAIL_LIMIT, OUTPUT_TAIL_LIMIT);
                    }
                }
            }
        } catch (IOException e) {
            var message = "[output stream closed: " + e.getMessage() + "]";
            synchronized (tail) {
                tail.writeBytes(message.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private static String snapshot(ByteArrayOutputStream tail) {
        synchronized (tail) {
            var bytes = tail.toByteArray();
            var start = Math.max(0, bytes.length - OUTPUT_TAIL_LIMIT);
            while (start < bytes.length && (bytes[start] & 0xC0) == 0x80) {
                start++;
            }
            return new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8);
        }
    }
}
