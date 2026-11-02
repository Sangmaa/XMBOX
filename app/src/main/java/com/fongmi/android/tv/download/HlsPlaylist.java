package com.fongmi.android.tv.download;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 精简 HLS 播放列表解析器（纯 Java，无 Android 依赖，可直接单测）。
 * 支持 master/media playlist、EXT-X-KEY(AES-128)、EXT-X-MAP、EXT-X-BYTERANGE。
 */
public final class HlsPlaylist {

    public static final class Variant {
        public final long bandwidth;
        public final String uri;

        Variant(long bandwidth, String uri) {
            this.bandwidth = bandwidth;
            this.uri = uri;
        }
    }

    public static final class Segment {
        public final String uri;
        public final long durationMs;
        public final long rangeOffset;
        public final long rangeLength;
        public final String keyUri;
        public final String iv;
        public final int sequence;

        Segment(String uri, long durationMs, long rangeOffset, long rangeLength, String keyUri, String iv, int sequence) {
            this.uri = uri;
            this.durationMs = durationMs;
            this.rangeOffset = rangeOffset;
            this.rangeLength = rangeLength;
            this.keyUri = keyUri;
            this.iv = iv;
            this.sequence = sequence;
        }

        public boolean hasRange() {
            return rangeLength > 0;
        }
    }

    public boolean master;
    public final List<Variant> variants = new ArrayList<>();
    public final List<Segment> segments = new ArrayList<>();
    public String initUri;
    public long initRangeOffset = -1;
    public long initRangeLength = -1;
    public long targetDurationMs;

    public boolean isMaster() {
        return master && !variants.isEmpty();
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }

    public static HlsPlaylist parse(String content, String baseUri) {
        HlsPlaylist playlist = new HlsPlaylist();
        if (content == null) return playlist;
        String[] lines = content.split("\n");
        String pendingKeyUri = null;
        String pendingIv = null;
        long pendingRangeOffset = -1;
        long pendingRangeLength = -1;
        double pendingDuration = 0;
        int mediaSequence = 0;
        long pendingVariantBandwidth = -1;
        boolean nextIsVariant = false;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                try {
                    mediaSequence = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                } catch (NumberFormatException ignored) {
                }
            } else if (line.startsWith("#EXT-X-TARGETDURATION:")) {
                try {
                    playlist.targetDurationMs = Math.round(Double.parseDouble(line.substring(line.indexOf(':') + 1).trim()) * 1000);
                } catch (NumberFormatException ignored) {
                }
            } else if (line.startsWith("#EXT-X-KEY:")) {
                String attrs = line.substring(line.indexOf(':') + 1);
                String method = attr(attrs, "METHOD");
                if (method == null || "NONE".equalsIgnoreCase(method)) {
                    pendingKeyUri = null;
                    pendingIv = null;
                } else {
                    pendingKeyUri = resolve(baseUri, attr(attrs, "URI"));
                    pendingIv = attr(attrs, "IV");
                }
            } else if (line.startsWith("#EXT-X-MAP:")) {
                String attrs = line.substring(line.indexOf(':') + 1);
                playlist.initUri = resolve(baseUri, attr(attrs, "URI"));
                String range = attr(attrs, "BYTERANGE");
                if (range != null) {
                    long[] parsed = parseRange(range);
                    playlist.initRangeOffset = parsed[0];
                    playlist.initRangeLength = parsed[1];
                }
            } else if (line.startsWith("#EXT-X-STREAM-INF:")) {
                playlist.master = true;
                String attrs = line.substring(line.indexOf(':') + 1);
                String bandwidth = attr(attrs, "BANDWIDTH");
                try {
                    pendingVariantBandwidth = bandwidth == null ? -1 : Long.parseLong(bandwidth.trim());
                } catch (NumberFormatException e) {
                    pendingVariantBandwidth = -1;
                }
                nextIsVariant = true;
            } else if (line.startsWith("#EXTINF:")) {
                String value = line.substring(line.indexOf(':') + 1);
                int comma = value.indexOf(',');
                if (comma >= 0) value = value.substring(0, comma);
                try {
                    pendingDuration = Double.parseDouble(value.trim());
                } catch (NumberFormatException e) {
                    pendingDuration = 0;
                }
            } else if (line.startsWith("#EXT-X-BYTERANGE:")) {
                long[] parsed = parseRange(line.substring(line.indexOf(':') + 1));
                pendingRangeOffset = parsed[0];
                pendingRangeLength = parsed[1];
            } else if (line.startsWith("#")) {
                // ignore unknown tags
            } else {
                String uri = resolve(baseUri, line);
                if (nextIsVariant) {
                    playlist.variants.add(new Variant(pendingVariantBandwidth, uri));
                    nextIsVariant = false;
                    pendingVariantBandwidth = -1;
                } else {
                    long offset = pendingRangeOffset;
                    // 无 offset 的 BYTERANGE 表示紧接着上一个分片
                    if (pendingRangeLength > 0 && offset < 0) {
                        Segment prev = playlist.segments.isEmpty() ? null : playlist.segments.get(playlist.segments.size() - 1);
                        offset = prev == null ? 0 : prev.rangeOffset + prev.rangeLength;
                    }
                    playlist.segments.add(new Segment(uri, Math.round(pendingDuration * 1000), offset, pendingRangeLength,
                            pendingKeyUri, pendingIv, mediaSequence + playlist.segments.size()));
                    pendingDuration = 0;
                    pendingRangeOffset = -1;
                    pendingRangeLength = -1;
                }
            }
        }
        return playlist;
    }

    /** 选择码率最高的子播放列表（无 BANDWIDTH 时取第一个）。 */
    public String bestVariant() {
        Variant best = null;
        for (Variant variant : variants) {
            if (best == null) {
                best = variant;
            } else if (variant.bandwidth > best.bandwidth && variant.bandwidth > 0) {
                best = variant;
            }
        }
        return best == null ? null : best.uri;
    }

    public static String resolve(String baseUri, String ref) {
        if (ref == null) return null;
        if (baseUri == null || baseUri.isEmpty()) return ref;
        try {
            return URI.create(baseUri).resolve(ref.trim()).toString();
        } catch (Exception e) {
            return ref;
        }
    }

    public static long[] parseRange(String value) {
        String range = value.trim();
        int at = range.indexOf('@');
        try {
            if (at >= 0) {
                long length = Long.parseLong(range.substring(0, at).trim());
                long offset = Long.parseLong(range.substring(at + 1).trim());
                return new long[]{offset, length};
            }
            return new long[]{-1, Long.parseLong(range)};
        } catch (NumberFormatException e) {
            return new long[]{-1, -1};
        }
    }

    public static String attr(String attrs, String key) {
        if (attrs == null) return null;
        int from = 0;
        while (from < attrs.length()) {
            int eq = attrs.indexOf('=', from);
            if (eq < 0) return null;
            int end = attrs.indexOf(',', eq + 1);
            String name = attrs.substring(from, eq).trim();
            String value;
            if (attrs.charAt(eq + 1) == '"') {
                int close = attrs.indexOf('"', eq + 2);
                if (close < 0) close = attrs.length();
                value = attrs.substring(eq + 2, Math.min(close, attrs.length()));
                end = attrs.indexOf(',', close + 1);
            } else {
                value = end < 0 ? attrs.substring(eq + 1).trim() : attrs.substring(eq + 1, end).trim();
            }
            if (name.equalsIgnoreCase(key)) return value;
            if (end < 0) return null;
            from = end + 1;
        }
        return null;
    }
}
