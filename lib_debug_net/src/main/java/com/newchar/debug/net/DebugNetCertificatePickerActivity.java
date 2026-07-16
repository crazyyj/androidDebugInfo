package com.newchar.debug.net;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 系统文件选择器 Activity，用于选择证书文件。
 * 选中后通过 setResult 返回证书路径和类型给调用方。
 *
 * <p>支持两种调用方式：
 * <ol>
 * <li>被 {@link com.newchar.debug.router.ResultProxyActivity} 启动：
 *     通过 setResult 回传路径和类型，由 ResultProxyActivity 透传给插件回调。</li>
 * <li>直接启动（无调用者）：setResult 被忽略，行为无副作用。</li>
 * </ol>
 */
public class DebugNetCertificatePickerActivity extends Activity {

    private static final int REQUEST_PICK_CERT = 0x4001;

    public static final String EXTRA_CERT_PATH = "debug_net_cert_path";
    public static final String EXTRA_KEYSTORE_TYPE = "debug_net_keystore_type";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 不设置 contentView，直接启动文件选择器
        Intent pickIntent = new Intent(Intent.ACTION_GET_CONTENT);
        pickIntent.setType("*/*");
        pickIntent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(pickIntent, "选择证书文件"), REQUEST_PICK_CERT);
        } catch (Exception e) {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode, data);
        if (requestCode == REQUEST_PICK_CERT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                String filePath = copyFileFromUri(uri);
                if (filePath != null) {
                    String keystoreType = detectKeystoreType(filePath);
                    // 通过 setResult 回传证书路径和类型
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra(EXTRA_CERT_PATH, filePath);
                    resultIntent.putExtra(EXTRA_KEYSTORE_TYPE, keystoreType);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                    return;
                }
            }
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    /**
     * 将选择的文件复制到应用私有目录，返回绝对路径。
     * 文件大小限制 1MB（避免误选大文件）。
     */
    private String copyFileFromUri(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > 1024 * 1024) {
                    return null; // 超过 1MB 视为无效
                }
                baos.write(buffer, 0, read);
            }
            if (total == 0) {
                return null;
            }
            byte[] data = baos.toByteArray();
            String fileName = getFileName(uri, "certificate");
            java.io.File dest = new java.io.File(getFilesDir(), fileName);
            try (FileOutputStream out = new FileOutputStream(dest)) {
                out.write(data);
                out.flush();
            }
            return dest.getAbsolutePath();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 通过文件扩展名和文件头部字节识别证书类型。
     * - PKCS12 (.p12/.pfx): ASN.1 OID 1.2.840.113549.1.7.2
     * - BKS (.bks): 文件头 "BKS!"
     * - 默认 PKCS12
     */
    private String detectKeystoreType(String filePath) {
        String pathLower = filePath.toLowerCase();
        // 1. 扩展名快速判断
        if (pathLower.endsWith(".bks")) {
            return DebugNetConfig.KEYSTORE_TYPE_BKS;
        }
        if (pathLower.endsWith(".p12") || pathLower.endsWith(".pfx")
                || pathLower.endsWith(".der") || pathLower.endsWith(".crt") || pathLower.endsWith(".cert")) {
            return DebugNetConfig.KEYSTORE_TYPE_PKCS12;
        }
        // 2. 文件头部字节识别
        try (InputStream in = new java.io.FileInputStream(filePath)) {
            byte[] header = new byte[1024];
            int len = in.read(header);
            if (len < 0) {
                return DebugNetConfig.KEYSTORE_TYPE_PKCS12;
            }
            // BKS: 文件头 "BKS!" (前4字节)
            if (len >= 4 && header[0] == 'B' && header[1] == 'K'
                    && header[2] == 'S' && header[3] == '!') {
                return DebugNetConfig.KEYSTORE_TYPE_BKS;
            }
            // PKCS12: ASN.1 以 0x30 开头，包含 OID 0x2A 0x86 0x48 0x86 0xF7 0x0D 0x01 0x07 0x02
            if (len >= 14 && header[0] == 0x30) {
                for (int i = 0; i < len - 8; i++) {
                    if (header[i] == 0x2A && header[i + 1] == 0x86
                            && header[i + 2] == 0x48 && header[i + 3] == 0x86
                            && header[i + 4] == 0xF7 && header[i + 5] == 0x0D
                            && header[i + 6] == 0x01 && header[i + 7] == 0x07) {
                        return DebugNetConfig.KEYSTORE_TYPE_PKCS12;
                    }
                }
            }
        } catch (IOException e) {
            // 忽略
        }
        return DebugNetConfig.KEYSTORE_TYPE_PKCS12;
    }

    private String getFileName(Uri uri, String defaultName) {
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                try {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        return cursor.getString(idx);
                    }
                } finally {
                    cursor.close();
                }
            }
        }
        String path = uri.getSchemeSpecificPart();
        if (path != null) {
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < path.length() - 1) {
                String name = path.substring(lastSlash + 1);
                if (!TextUtils.isEmpty(name)) {
                    return name;
                }
            }
        }
        return defaultName;
    }
}
