package com.stagecore.player;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class MjpegFrameReaderTest {
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, 42, (byte) 0xff, (byte) 0xd9};
    private static final String HEADER = "multipart/x-mixed-replace; boundary=stagecore-relay-frame";

    private static byte[] part(String type, String length, byte[] data) {
        byte[] before = ("--stagecore-relay-frame\r\nContent-Type: " + type
                + "\r\nContent-Length: " + length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] bytes = new byte[before.length + data.length + 2];
        System.arraycopy(before, 0, bytes, 0, before.length);
        System.arraycopy(data, 0, bytes, before.length, data.length);
        bytes[bytes.length - 2] = '\r';
        bytes[bytes.length - 1] = '\n';
        return bytes;
    }

    @Test public void readsTwoConsecutiveJpegs() throws Exception {
        byte[] one = part("image/jpeg", "5", JPEG);
        byte[] both = new byte[one.length * 2];
        System.arraycopy(one, 0, both, 0, one.length);
        System.arraycopy(one, 0, both, one.length, one.length);
        MjpegFrameReader reader = new MjpegFrameReader(new ByteArrayInputStream(both), HEADER);
        assertArrayEquals(JPEG, reader.nextJpeg());
        assertArrayEquals(JPEG, reader.nextJpeg());
        assertThrows(EOFException.class, reader::nextJpeg);
    }

    @Test public void acceptsQuotedBoundaryAndCaseInsensitiveMime() throws Exception {
        MjpegFrameReader reader = new MjpegFrameReader(
                new ByteArrayInputStream(part("IMAGE/JPEG", "5", JPEG)),
                "Multipart/X-Mixed-Replace; boundary=\"stagecore-relay-frame\"");
        assertArrayEquals(JPEG, reader.nextJpeg());
    }

    @Test public void rejectsOversizedFrameBeforeReadingPayload() throws Exception {
        MjpegFrameReader reader = new MjpegFrameReader(
                new ByteArrayInputStream(part("image/jpeg", "524289", JPEG)), HEADER);
        IOException err = assertThrows(IOException.class, reader::nextJpeg);
        assertEquals("JPEG frame out of bounds", err.getMessage());
    }

    @Test public void rejectsWrongPartType() throws Exception {
        MjpegFrameReader reader = new MjpegFrameReader(
                new ByteArrayInputStream(part("video/mp4", "5", JPEG)), HEADER);
        assertThrows(IOException.class, reader::nextJpeg);
    }

    @Test public void rejectsTruncatedAndMalformedJpeg() throws Exception {
        MjpegFrameReader truncated = new MjpegFrameReader(
                new ByteArrayInputStream(part("image/jpeg", "20", JPEG)), HEADER);
        assertThrows(EOFException.class, truncated::nextJpeg);
        MjpegFrameReader malformed = new MjpegFrameReader(
                new ByteArrayInputStream(part("image/jpeg", "5", new byte[] {1, 2, 3, 4, 5})), HEADER);
        assertThrows(IOException.class, malformed::nextJpeg);
    }

    @Test public void rejectsWrongStreamTypeAndInvalidBoundary() {
        assertThrows(IOException.class, () -> new MjpegFrameReader(
                new ByteArrayInputStream(JPEG), "video/mp4"));
        assertThrows(IOException.class, () -> new MjpegFrameReader(
                new ByteArrayInputStream(JPEG), "multipart/x-mixed-replace; boundary="));
    }
}
