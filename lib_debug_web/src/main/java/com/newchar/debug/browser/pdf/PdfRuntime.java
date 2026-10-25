package com.newchar.debug.browser.pdf;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * pdf.js 资源包：assets 里只放一个 zip，首次使用时展开到内部目录再读。
 *
 * <p>散着放的 pdf.js 有两百来个文件，其中 {@code build/} 正好撞上仓库的
 * {@code build} 忽略规则，clone 下来就缺了主程序；打包成一个文件后不再受
 * 目录名影响，也省掉了 aapt 逐个文件的处理开销。</p>
 *
 * <p>展开位置固定在 {@code filesDir/pdfjs}，用版本戳（版本号 + 包更新时间）
 * 判断是否需要重新展开；整个过程在临时目录里完成再整体改名，避免半包状态
 * 被 WebView 读到。</p>
 */
final class PdfRuntime {

    /** assets 中的包路径。 */
    private static final String ZIP_ASSET = "pdfjs.zip";
    /** 展开目录名，位于 filesDir 下。 */
    private static final String ROOT_NAME = "pdfjs";
    /** 展开过程的临时目录名。 */
    private static final String TMP_NAME = "pdfjs.tmp";
    /** 被替换下来的旧目录名。 */
    private static final String OLD_NAME = "pdfjs.old";
    /** 版本戳文件名，放在展开目录内。 */
    private static final String STAMP_NAME = ".pdfjs-stamp";
    /** 就绪判定的关键入口，缺失即视为需要重新展开。 */
    private static final String ENTRY_VIEWER = "web/viewer.html";
    private static final int BUFFER_SIZE = 16 * 1024;

    private final Object lock = new Object();
    private final File root;
    private final Context appContext;
    private volatile boolean ready;

    /**
     * 创建资源包。
     *
     * @param context 任意上下文，内部取 Application
     */
    PdfRuntime(Context context) {
        appContext = context.getApplicationContext();
        root = new File(appContext.getFilesDir(), ROOT_NAME);
    }

    /**
     * 展开后的根目录，只有 {@link #isReady()} 为真时内容才完整。
     *
     * @return 资源根目录
     */
    File root() {
        return root;
    }

    /**
     * 资源是否已展开可用。
     *
     * @return 可用时为 true
     */
    boolean isReady() {
        return ready;
    }

    /**
     * 保证资源已展开，可重复调用；失败时保留旧目录不动。
     *
     * @throws IOException 包缺失、损坏或写入失败
     */
    void ensureReady() throws IOException {
        if (ready) {
            return;
        }
        synchronized (lock) {
            if (ready) {
                return;
            }
            String stamp = currentStamp();
            if (new File(root, ENTRY_VIEWER).isFile() && stamp.equals(readStamp(new File(root, STAMP_NAME)))) {
                ready = true;
                return;
            }
            unpack(stamp);
            ready = true;
        }
    }

    /**
     * 把 zip 展开到临时目录，再整体替换到目标位置。
     */
    private void unpack(String stamp) throws IOException {
        File home = appContext.getFilesDir();
        File tmp = new File(home, TMP_NAME);
        File old = new File(home, OLD_NAME);
        deleteTree(tmp);
        deleteTree(old);
        if (!tmp.mkdirs() && !tmp.isDirectory()) {
            throw new IOException("无法创建 pdf.js 临时目录");
        }
        try (InputStream raw = appContext.getAssets().open(ZIP_ASSET, AssetManager.ACCESS_STREAMING);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw, BUFFER_SIZE))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                File target = resolveInside(tmp, entry.getName());
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("无法创建目录：" + entry.getName());
                }
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(target), BUFFER_SIZE)) {
                    int read;
                    while ((read = zip.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                }
            }
            writeStamp(tmp, stamp);
        }
        if (root.exists() && !root.renameTo(old)) {
            throw new IOException("无法替换旧的 pdf.js 目录");
        }
        if (!tmp.renameTo(root)) {
            throw new IOException("无法写入 pdf.js 目录");
        }
        deleteTree(old);
    }

    /**
     * 把包内成员名限制在目标目录内，挡掉 {@code ../} 与绝对路径。
     */
    private File resolveInside(File base, String name) throws IOException {
        File target = new File(base, name);
        String basePath = base.getCanonicalPath();
        if (!basePath.endsWith(File.separator)) {
            basePath += File.separator;
        }
        if (!target.getCanonicalPath().startsWith(basePath)) {
            throw new IOException("非法压缩包成员：" + name);
        }
        return target;
    }

    /**
     * 版本戳：包更新后必须重新展开。
     */
    @SuppressWarnings("deprecation")
    private String currentStamp() {
        long updated = 0L;
        int version = 0;
        try {
            PackageInfo info = appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), 0);
            updated = info.lastUpdateTime;
            version = info.versionCode;
        } catch (PackageManager.NameNotFoundException ignored) {
            // 取不到就退回空戳，靠关键文件是否存在兜底
        }
        return version + "-" + updated;
    }

    private String readStamp(File marker) {
        if (!marker.isFile()) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(marker), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            return line == null ? "" : line;
        } catch (IOException ignored) {
            return "";
        }
    }

    private void writeStamp(File dir, String stamp) throws IOException {
        File marker = new File(dir, STAMP_NAME);
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(marker), StandardCharsets.UTF_8)) {
            writer.write(stamp);
        }
    }

    private static void deleteTree(File target) {
        if (target == null || !target.exists()) {
            return;
        }
        File[] children = target.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        target.delete();
    }
}
