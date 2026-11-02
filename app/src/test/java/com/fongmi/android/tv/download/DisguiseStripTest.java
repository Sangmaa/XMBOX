package com.fongmi.android.tv.download;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class DisguiseStripTest {

    private static byte[] ts(int packets) {
        byte[] data = new byte[packets * 188];
        for (int i = 0; i < packets; i++) data[i * 188] = 0x47;
        return data;
    }

    @Test
    public void stripsPngPrefix() {
        byte[] png = new byte[126];
        png[0] = (byte) 0x89;
        png[1] = 'P';
        png[2] = 'N';
        png[3] = 'G';
        byte[] body = ts(8);
        byte[] input = new byte[png.length + body.length];
        System.arraycopy(png, 0, input, 0, png.length);
        System.arraycopy(body, 0, input, png.length, body.length);
        assertArrayEquals(body, HlsDownloader.stripDisguise(input));
    }

    @Test
    public void keepsPlainTs() {
        byte[] body = ts(8);
        assertSame(body, HlsDownloader.stripDisguise(body));
    }

    @Test
    public void keepsPureImage() {
        byte[] png = new byte[1024];
        png[0] = (byte) 0x89;
        png[1] = 'P';
        png[2] = 'N';
        png[3] = 'G';
        byte[] out = HlsDownloader.stripDisguise(png);
        assertEquals((byte) 0x89, out[0]);
        assertEquals(png.length, out.length);
    }
}
