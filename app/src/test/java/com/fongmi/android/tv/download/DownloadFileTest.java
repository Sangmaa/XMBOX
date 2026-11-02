package com.fongmi.android.tv.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;

public class DownloadFileTest {

    @Test
    public void sanitizeReplacesIllegalCharacters() {
        assertEquals("a_b_c_d_e_f_g_h_i_j", DownloadFile.sanitize("a/b:c*d?e\"f<g>h|i\\j"));
    }

    @Test
    public void sanitizeTrimsDotsAndSpaces() {
        assertEquals("name", DownloadFile.sanitize(" name. "));
        assertEquals("video", DownloadFile.sanitize(""));
        assertEquals("", DownloadFile.sanitize(null));
    }

    @Test
    public void sanitizeLimitsLength() {
        String longName = new String(new char[200]).replace('\0', 'x');
        assertEquals(80, DownloadFile.sanitize(longName).length());
    }

    @Test
    public void isHlsDetectsPlaylist() {
        assertTrue(DownloadFile.isHls("https://a.com/x/index.m3u8?token=1"));
        assertTrue(DownloadFile.isHls("https://a.com/x/playlist.txt"));
        assertFalse(DownloadFile.isHls("https://a.com/x/video.mp4"));
        assertFalse(DownloadFile.isHls(null));
    }

    @Test
    public void extensionPrefersRealMediaExtension() {
        assertEquals(".mp4", DownloadFile.extensionFor("https://a.com/x/v.m3u8"));
        assertEquals(".mp4", DownloadFile.extensionFor("https://a.com/x/v.mp4?token=1"));
        assertEquals(".mkv", DownloadFile.extensionFor("https://a.com/x/v.MKV"));
        assertEquals(".mp4", DownloadFile.extensionFor("https://a.com/x/stream"));
    }

    @Test
    public void relativePathGroupsByVodName() {
        String path = DownloadFile.relativePath("剧名", "第1集", "https://a.com/x/index.m3u8");
        assertEquals("剧名" + File.separator + "第1集.mp4", path);
    }

    @Test
    public void partAndPartsDirSitNextToTarget() {
        File target = new File(new File(new File("tmp"), "root"), "vod" + File.separator + "ep.mp4");
        assertEquals("ep.mp4.part", DownloadFile.part(target).getName());
        assertEquals("ep.mp4.parts", DownloadFile.partsDir(target).getName());
        assertEquals(target.getParentFile(), DownloadFile.part(target).getParentFile());
    }

    @Test
    public void formatSizeUsesReadableUnits() {
        assertEquals("0B", DownloadFile.formatSize(0));
        assertEquals("1.0KB", DownloadFile.formatSize(1024));
        assertEquals("1.5MB", DownloadFile.formatSize(1024 * 1024 + 512 * 1024));
        assertEquals("1.0KB/s", DownloadFile.formatSpeed(1024));
    }
}
