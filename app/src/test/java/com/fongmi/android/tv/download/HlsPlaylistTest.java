package com.fongmi.android.tv.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HlsPlaylistTest {

    @Test
    public void parsesMasterAndPicksHighestBandwidth() {
        String master = "#EXTM3U\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=800000\n"
                + "low/index.m3u8\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=2400000\n"
                + "high/index.m3u8\n";
        HlsPlaylist playlist = HlsPlaylist.parse(master, "https://cdn.example.com/vod/a.m3u8");
        assertTrue(playlist.isMaster());
        assertEquals(2, playlist.variants.size());
        assertEquals("https://cdn.example.com/vod/high/index.m3u8", playlist.bestVariant());
    }

    @Test
    public void parsesMediaSegments() {
        String media = "#EXTM3U\n"
                + "#EXT-X-TARGETDURATION:4\n"
                + "#EXT-X-MEDIA-SEQUENCE:7\n"
                + "#EXTINF:4.0,\n"
                + "seg-1.ts\n"
                + "#EXTINF:4.0,\n"
                + "seg-2.ts\n"
                + "#EXT-X-ENDLIST\n";
        HlsPlaylist playlist = HlsPlaylist.parse(media, "https://cdn.example.com/vod/a.m3u8");
        assertFalse(playlist.isMaster());
        assertEquals(2, playlist.segments.size());
        assertEquals("https://cdn.example.com/vod/seg-1.ts", playlist.segments.get(0).uri);
        assertEquals(4000, playlist.segments.get(0).durationMs);
        assertEquals(7, playlist.segments.get(0).sequence);
        assertEquals(8, playlist.segments.get(1).sequence);
        assertEquals(4000, playlist.targetDurationMs);
    }

    @Test
    public void parsesAesKeyAndInitMap() {
        String media = "#EXTM3U\n"
                + "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\",IV=0x1234\n"
                + "#EXT-X-MAP:URI=\"init.mp4\",BYTERANGE=\"720@0\"\n"
                + "#EXTINF:4.0,\n"
                + "seg-1.ts\n";
        HlsPlaylist playlist = HlsPlaylist.parse(media, "https://cdn.example.com/vod/a.m3u8");
        assertEquals("https://cdn.example.com/vod/init.mp4", playlist.initUri);
        assertEquals(0, playlist.initRangeOffset);
        assertEquals(720, playlist.initRangeLength);
        assertEquals("https://cdn.example.com/vod/key.bin", playlist.segments.get(0).keyUri);
        assertEquals("0x1234", playlist.segments.get(0).iv);
    }

    @Test
    public void parsesByteRanges() {
        String media = "#EXTM3U\n"
                + "#EXT-X-BYTERANGE:100@0\n"
                + "#EXTINF:2.0,\n"
                + "video.mp4\n"
                + "#EXT-X-BYTERANGE:50\n"
                + "#EXTINF:2.0,\n"
                + "video.mp4\n";
        HlsPlaylist playlist = HlsPlaylist.parse(media, "https://cdn.example.com/vod/a.m3u8");
        assertEquals(100, playlist.segments.get(0).rangeLength);
        assertEquals(0, playlist.segments.get(0).rangeOffset);
        assertEquals(50, playlist.segments.get(1).rangeLength);
        assertEquals(100, playlist.segments.get(1).rangeOffset);
        assertTrue(playlist.segments.get(0).hasRange());
    }

    @Test
    public void emptyInputYieldsEmptyPlaylist() {
        HlsPlaylist playlist = HlsPlaylist.parse(null, null);
        assertTrue(playlist.isEmpty());
        assertFalse(playlist.isMaster());
    }
}
