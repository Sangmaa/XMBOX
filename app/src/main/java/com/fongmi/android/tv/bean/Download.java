package com.fongmi.android.tv.bean;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.download.DownloadFile;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Entity
public class Download {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_DOWNLOADING = 1;
    public static final int STATUS_COMPLETED = 2;
    public static final int STATUS_FAILED = -1;
    public static final int STATUS_PAUSED = -2;

    @PrimaryKey
    @NonNull
    @SerializedName("id")
    private String id;
    @SerializedName("vodPic")
    private String vodPic;
    @SerializedName("vodName")
    private String vodName;
    @SerializedName("vodId")
    private String vodId;
    @SerializedName("url")
    private String url;
    @SerializedName("header")
    private String header;
    @SerializedName("createTime")
    private long createTime;
    @SerializedName("progress")
    private int progress;
    @SerializedName("status")
    private String status;
    @SerializedName("statusInt")
    private int statusInt;
    @SerializedName("duration")
    private long duration;
    @SerializedName("speed")
    private long speed;
    @SerializedName("localPath")
    private String localPath;
    @SerializedName("episodeName")
    private String episodeName;
    @SerializedName("totalBytes")
    private long totalBytes;
    @SerializedName("downloaded")
    private long downloaded;
    @SerializedName("error")
    private String error;

    public static Download objectFrom(String str) {
        try {
            return new Gson().fromJson(str, Download.class);
        } catch (Exception e) {
            return new Download();
        }
    }

    public Download() {
        this.createTime = System.currentTimeMillis();
        this.progress = 0;
        this.status = "pending";
    }

    public Download(@NonNull String id, String vodPic, String vodName, String url) {
        this(id, vodPic, vodName, url, "");
    }

    public Download(@NonNull String id, String vodPic, String vodName, String url, String header) {
        this.id = id;
        this.vodPic = vodPic;
        this.vodName = vodName;
        this.url = url;
        this.header = header;
        this.createTime = System.currentTimeMillis();
        this.progress = 0;
        this.status = "pending";
    }

    @NonNull
    public String getId() {
        return id == null ? "" : id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getVodPic() {
        return vodPic == null ? "" : vodPic;
    }

    public void setVodPic(String vodPic) {
        this.vodPic = vodPic;
    }

    public String getVodName() {
        return vodName == null ? "" : vodName;
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public String getVodId() {
        return vodId == null ? "" : vodId;
    }

    public void setVodId(String vodId) {
        this.vodId = vodId;
    }

    public String getUrl() {
        return url == null ? "" : url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getHeader() {
        return header == null ? "" : header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(100, progress));
    }

    public String getStatus() {
        return status == null ? "pending" : status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getStatusInt() {
        return statusInt;
    }

    public void setStatusInt(int statusInt) {
        this.statusInt = statusInt;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getSpeed() {
        return speed;
    }

    public void setSpeed(long speed) {
        this.speed = speed;
    }

    public String getLocalPath() {
        return localPath == null ? "" : localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public String getEpisodeName() {
        return episodeName == null ? "" : episodeName;
    }

    public void setEpisodeName(String episodeName) {
        this.episodeName = episodeName;
    }

    public long getDownloaded() {
        return downloaded;
    }

    public void setDownloaded(long downloaded) {
        this.downloaded = downloaded;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public String getError() {
        return error == null ? "" : error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean isCompleted() {
        return statusInt == STATUS_COMPLETED;
    }

    public boolean isDownloading() {
        return statusInt == STATUS_DOWNLOADING;
    }

    public boolean isFailed() {
        return statusInt == STATUS_FAILED;
    }

    public boolean isPaused() {
        return statusInt == STATUS_PAUSED;
    }

    public boolean fileExists() {
        return !getLocalPath().isEmpty() && new File(getLocalPath()).isFile();
    }

    /**
     * 已完成记录的文件是否像可播放视频：排除下载失败却把播放列表/错误页当成视频保存的情况。
     */
    public boolean isValidFile() {
        if (!fileExists()) return false;
        File file = new File(getLocalPath());
        if (file.length() < 50_000) return false;
        // 文件被重名下载覆盖或损坏时，大小与记录不一致
        if (totalBytes > 0 && Math.abs(file.length() - totalBytes) > 1) return false;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] head = new byte[16];
            int count = raf.read(head);
            if (count < 4) return false;
            String text = new String(head, 0, Math.min(count, 8), StandardCharsets.US_ASCII);
            return !text.startsWith("#EXTM3U") && !text.startsWith("<!DOCTYPE") && !text.startsWith("<html");
        } catch (Exception e) {
            return false;
        }
    }

    public String getDisplayName() {
        if (getEpisodeName().isEmpty()) return getVodName();
        if (getVodName().isEmpty()) return getEpisodeName();
        return getVodName() + " " + getEpisodeName();
    }

    public Download save() {
        AppDatabase.get().getDownloadDao().insertOrUpdate(this);
        return this;
    }

    public void delete() {
        AppDatabase.get().getDownloadDao().delete(this);
        deleteVideoFile();
    }

    public static void clear() {
        List<Download> allDownloads = AppDatabase.get().getDownloadDao().getAll();
        for (Download download : allDownloads) download.deleteVideoFile();
        AppDatabase.get().getDownloadDao().clear();
    }

    public void deleteVideoFile() {
        try {
            if (!getLocalPath().isEmpty()) {
                File file = new File(getLocalPath());
                deleteRecursive(file);
                deleteRecursive(new File(file.getParentFile(), file.getName() + ".part"));
                deleteRecursive(new File(file.getParentFile(), file.getName() + ".parts"));
            }
            // 兼容旧版本：旧代码把文件放在 TV/Download 根目录
            if (!getVodName().isEmpty()) {
                File legacy = new File(com.github.catvod.utils.Path.download(), DownloadFile.sanitize(getVodName()) + DownloadFile.extensionFor(getUrl()));
                deleteRecursive(legacy);
            }
        } catch (Exception e) {
            com.github.catvod.utils.Logger.e("Download: 删除视频文件出错", e);
        }
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursive(child);
        }
        file.delete();
    }

    public static List<Download> getAll() {
        return AppDatabase.get().getDownloadDao().getAll();
    }

    public static Download find(String id) {
        return AppDatabase.get().getDownloadDao().find(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Download)) return false;
        Download download = (Download) obj;
        return getId().equals(download.getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
