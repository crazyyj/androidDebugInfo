package com.newchar.debug.browser.pdf;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 把外部 PDF 落到可随机读取的内部缓存，并用不透明令牌对外暴露。
 *
 * <p>pdf.js 需要按 Range 读取，ContentResolver 提供的流不一定可 seek，
 * 因此统一先复制成文件；令牌只允许 32 位十六进制，杜绝路径穿越。</p>
 */
public final class PdfDocuments {

    private static final Pattern TOKEN = Pattern.compile("[0-9a-f]{32}");
    private static final int BUFFER_SIZE = 8 * 1024;

    private final File documentsDir;
    private final File outputDir;
    private final Map<String, File> documents = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /**
     * 创建文档仓库，目录位于应用内部缓存，不进入外部存储。
     *
     * @param context 任意上下文，内部取 Application
     */
    public PdfDocuments(Context context) {
        File root = new File(context.getApplicationContext().getCacheDir(), "debug_pdf");
        documentsDir = new File(root, "docs");
        outputDir = new File(root, "out");
        //noinspection ResultOfMethodCallIgnored
        documentsDir.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        outputDir.mkdirs();
    }

    /**
     * 复制 content:// 或 file:// 指向的文档。
     *
     * @param context 用于解析 Uri
     * @param uri     系统选择器返回的地址
     * @return 令牌
     * @throws IOException 读取或落盘失败
     */
    public String register(Context context, Uri uri) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("无法读取所选文件");
            }
            return register(input);
        }
    }

    /**
     * 复制字节流。
     *
     * @param input 文档内容流，由调用方关闭
     * @return 令牌
     * @throws IOException 落盘失败
     */
    public String register(InputStream input) throws IOException {
        String token = newToken();
        File target = new File(documentsDir, token + ".pdf");
        File temp = new File(documentsDir, token + ".part");
        try (OutputStream output = new FileOutputStream(temp)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } catch (IOException error) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw error;
        }
        if (!temp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("缓存 PDF 失败");
        }
        documents.put(token, target);
        return token;
    }

    /**
     * 保存页面回写的 PDF。
     *
     * @param suggestedName 页面提供的文件名，仅取末段并过滤非法字符
     * @param content       完整文件字节
     * @return 落盘文件
     * @throws IOException 写入失败
     */
    public File write(String suggestedName, byte[] content) throws IOException {
        File target = new File(outputDir, safeName(suggestedName));
        try (OutputStream output = new FileOutputStream(target)) {
            output.write(content);
        }
        return target;
    }

    /**
     * 按令牌取文件，非法令牌一律返回 null。
     *
     * @param token 文档令牌
     * @return 可读文件，不存在时为 null
     */
    public File find(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            return null;
        }
        File file = documents.get(token);
        return file != null && file.isFile() ? file : null;
    }

    /**
     * 删除令牌对应的临时文件。
     *
     * @param token 文档令牌
     */
    public void release(String token) {
        File file = documents.remove(token);
        if (file != null) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private String newToken() {
        byte[] raw = new byte[16];
        random.nextBytes(raw);
        StringBuilder builder = new StringBuilder(raw.length * 2);
        for (byte value : raw) {
            int unsigned = value & 0xFF;
            if (unsigned < 0x10) {
                builder.append('0');
            }
            builder.append(Integer.toHexString(unsigned));
        }
        return builder.toString();
    }

    private String safeName(String suggested) {
        String base = suggested == null || suggested.isEmpty() ? "document" : suggested;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        if (!base.toLowerCase(Locale.US).endsWith(".pdf")) {
            base = base + ".pdf";
        }
        if (base.length() > 96) {
            base = base.substring(base.length() - 96);
        }
        return System.currentTimeMillis() + "_" + base;
    }
}
