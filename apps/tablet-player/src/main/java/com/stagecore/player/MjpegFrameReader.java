package com.stagecore.player;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Bounded, HTTP multipart MJPEG parser. Never accepts an unbounded JPEG body. */
final class MjpegFrameReader {
    static final int MAX_FRAME_BYTES = 512 * 1024;
    private static final int MAX_LINE_BYTES = 1024;
    private final InputStream input;
    private final String delimiter;

    MjpegFrameReader(InputStream input, String contentType) throws IOException {
        this.input = input;
        this.delimiter = "--" + parseBoundary(contentType);
    }

    static String parseBoundary(String contentType) throws IOException {
        if (contentType == null) throw new IOException("Missing MJPEG Content-Type");
        String[] tokens = contentType.split(";");
        if (!"multipart/x-mixed-replace".equalsIgnoreCase(tokens[0].trim())) {
            throw new IOException("Expected multipart/x-mixed-replace");
        }
        for (int i = 1; i < tokens.length; i++) {
            String parameter = tokens[i].trim();
            if (parameter.toLowerCase(Locale.US).startsWith("boundary=")) {
                String boundary = parameter.substring("boundary=".length()).trim();
                if (boundary.length() >= 2 && boundary.startsWith("\"") && boundary.endsWith("\"")) {
                    boundary = boundary.substring(1, boundary.length() - 1);
                }
                if (boundary.startsWith("--")) boundary = boundary.substring(2);
                if (boundary.isEmpty() || boundary.length() > 70 || !boundary.matches("[A-Za-z0-9'()+_,./:=?-]+")) {
                    throw new IOException("Invalid multipart boundary");
                }
                return boundary;
            }
        }
        throw new IOException("No multipart boundary");
    }

    byte[] nextJpeg() throws IOException {
        // A previous part's trailing CRLF produces an empty line before the boundary.
        String boundaryLine;
        do {
            boundaryLine = readLine();
        } while (boundaryLine.isEmpty());

        if ((delimiter + "--").equals(boundaryLine)) throw new EOFException("MJPEG stream ended");
        if (!delimiter.equals(boundaryLine)) throw new IOException("Unexpected multipart boundary");

        String mime = null;
        int length = -1;
        int count = 0;
        while (true) {
            String line = readLine();
            if (line.isEmpty()) break;
            if (++count > 16) throw new IOException("Too many MJPEG part headers");
            int colon = line.indexOf(':');
            if (colon <= 0) throw new IOException("Malformed MJPEG part header");
            String key = line.substring(0, colon).trim().toLowerCase(Locale.US);
            String value = line.substring(colon + 1).trim();
            if ("content-type".equals(key)) mime = value;
            if ("content-length".equals(key)) {
                try {
                    length = Integer.parseInt(value);
                } catch (NumberFormatException error) {
                    throw new IOException("Invalid JPEG length", error);
                }
            }
        }

        if (!"image/jpeg".equalsIgnoreCase(mime)) throw new IOException("Expected image/jpeg part");
        if (length < 4 || length > MAX_FRAME_BYTES) throw new IOException("JPEG frame out of bounds");
        byte[] jpeg = new byte[length];
        int position = 0;
        while (position < length) {
            int received = input.read(jpeg, position, length - position);
            if (received == -1) throw new EOFException("Truncated JPEG");
            if (received == 0) {
                int single = input.read();
                if (single == -1) throw new EOFException("Truncated JPEG");
                jpeg[position++] = (byte) single;
            } else {
                position += received;
            }
        }
        if ((jpeg[0] & 0xff) != 0xff || (jpeg[1] & 0xff) != 0xd8
                || (jpeg[length - 2] & 0xff) != 0xff || (jpeg[length - 1] & 0xff) != 0xd9) {
            throw new IOException("Invalid JPEG markers");
        }
        return jpeg;
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(64);
        while (true) {
            int value = input.read();
            if (value == -1) throw new EOFException("MJPEG connection closed");
            if (value == '\n') {
                byte[] bytes = buffer.toByteArray();
                int size = bytes.length;
                if (size > 0 && bytes[size - 1] == '\r') size--;
                return new String(bytes, 0, size, StandardCharsets.US_ASCII);
            }
            if (buffer.size() >= MAX_LINE_BYTES) throw new IOException("MJPEG header line too long");
            buffer.write(value);
        }
    }
}
