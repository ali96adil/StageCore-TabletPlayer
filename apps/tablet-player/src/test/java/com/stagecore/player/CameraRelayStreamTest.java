package com.stagecore.player;

import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.*;

public class CameraRelayStreamTest {
    @Test public void buildsOnlyThePairedHubContractRequest() {
        Request request = CameraRelayStream.request("https://hub.local:7843", "project-1", "runtime-token");
        assertEquals("GET", request.method());
        assertEquals("https://hub.local:7843/api/v1/stage-device-camera/projects/project-1/stream", request.url().toString());
        assertEquals("StageCoreSession runtime-token", request.header("Authorization"));
        assertEquals("multipart/x-mixed-replace", request.header("Accept"));
        assertThrows(IllegalArgumentException.class,
                () -> CameraRelayStream.request("http://hub.local:7843", "project-1", "token"));
        assertThrows(IllegalArgumentException.class,
                () -> CameraRelayStream.request("https://hub.local:7843", "../other", "token"));
    }

    @Test public void expirationRenewsOnlyCurrentScopeAndRejectsOldStream() {
        AtomicInteger renewals = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        AtomicBoolean current = new AtomicBoolean(true);
        CameraRelayStream stream = stream(200, Instant.EPOCH, current, renewals);
        stream.whenCancelled(cancellations::incrementAndGet);
        assertThrows(IOException.class, stream::open);
        assertThrows(IOException.class, stream::open);
        assertEquals(1, renewals.get());
        assertEquals(1, cancellations.get());

        current.set(false);
        assertThrows(IOException.class, stream(200, Instant.EPOCH, current, renewals)::open);
        assertEquals(1, renewals.get());
    }

    @Test public void assignmentDenialAndRedirectCloseWithoutRenewing() {
        AtomicInteger renewals = new AtomicInteger();
        for (int status : new int[]{302, 403, 404, 410}) {
            CameraRelayStream stream = stream(status, Instant.now().plusSeconds(30),
                    new AtomicBoolean(true), renewals);
            assertThrows(IOException.class, stream::open);
            assertFalse(stream.isCurrent());
        }
        assertEquals(1, renewals.get()); // Only HTTP 410 invalidates the credential.
    }

    @Test public void staleScopeCannotUseAResponseThatArrivesAfterRevocation() {
        AtomicBoolean current = new AtomicBoolean(true);
        AtomicInteger cancellations = new AtomicInteger();
        AtomicInteger renewals = new AtomicInteger();
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            current.set(false);
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("fixture").body(ResponseBody.create("", null)).build();
        }).build();
        CameraRelayStream stream = new CameraRelayStream(client, "https://hub.local:7843", "project-1",
                "runtime-token", Instant.now().plusSeconds(30), current::get, renewals::incrementAndGet);
        stream.whenCancelled(cancellations::incrementAndGet);
        assertThrows(IOException.class, stream::open);
        assertEquals(1, cancellations.get());
        assertEquals(0, renewals.get());
    }

    private static CameraRelayStream stream(int code, Instant expiry, AtomicBoolean current,
                                            AtomicInteger renewals) {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
                new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(code).message("fixture")
                        .body(ResponseBody.create("", null)).build()).build();
        return new CameraRelayStream(client, "https://hub.local:7843", "project-1",
                "runtime-token", expiry, current::get, renewals::incrementAndGet);
    }
}
