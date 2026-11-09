package com.stagecore.player;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import okhttp3.Request;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeNotNull;

public class CameraRelayCompatibilityTest {
    @Test public void matchesPinnedHubRequestAndReadsItsFrameFormat() throws Exception {
        String path = System.getenv("STAGECORE_CAMERA_CONTRACT");
        assumeNotNull(path);
        JSONObject contract = new JSONObject(Files.readString(Path.of(path)));
        assertEquals(1, contract.getInt("version"));
        assertEquals("TABLET_LIVE_SHOW", contract.getString("command"));
        assertEquals(CameraRelayStream.MEDIA_KEY,
                contract.getJSONObject("payload").getString("media_key"));
        String project = "contract-project";
        Request request = CameraRelayStream.request("https://hub.local:7843", project, "token");
        assertEquals(contract.getString("request_method"), request.method());
        assertEquals(contract.getString("request_path").replace("{project_id}", project),
                request.url().encodedPath());
        assertEquals(contract.getString("authorization_scheme") + " token", request.header("Authorization"));
        assertEquals(contract.getString("content_type"), request.header("Accept"));

        byte[] jpeg = Base64.getDecoder().decode(contract.getString("fixture_jpeg_base64"));
        ByteArrayOutputStream multipart = new ByteArrayOutputStream();
        multipart.write(("--contract\r\nContent-Type: " + contract.getString("frame_content_type")
                + "\r\nContent-Length: " + jpeg.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        multipart.write(jpeg);
        MjpegFrameReader reader = new MjpegFrameReader(new ByteArrayInputStream(multipart.toByteArray()),
                contract.getString("content_type") + "; boundary=contract");
        assertArrayEquals(jpeg, reader.nextJpeg());
    }
}
