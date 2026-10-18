package com.haven.kegent.keep;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class MmkvReaderTest {
    private void field(ByteArrayOutputStream out, byte[] value) {
        out.write(value.length);
        out.write(value, 0, value.length);
    }

    private void entry(ByteArrayOutputStream out, String value) {
        field(out, "userid".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream s = new ByteArrayOutputStream();
        field(s, value.getBytes(StandardCharsets.UTF_8));
        field(out, s.toByteArray());
    }

    @Test
    public void latestActiveEntryWinsAndTrailingBytesAreIgnored() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(0);
        entry(body, "old-user");
        entry(body, "current-user");
        byte[] bytes = new byte[128];
        byte[] payload = body.toByteArray();
        bytes[0] = (byte) payload.length;
        System.arraycopy(payload, 0, bytes, 4, payload.length);
        assertEquals("current-user", MmkvReader.string(bytes, "userid"));
        assertEquals("", MmkvReader.string(bytes, "absent"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void truncatedSizeIsRejected() {
        MmkvReader.string(new byte[] {100, 0, 0, 0, 1}, "userid");
    }
}
