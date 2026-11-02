package com.newchar.debug.browser.permission;

import android.content.Context;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * 验证网站媒体授权按 origin 持久化，路径和默认端口不会扩大授权边界。
 */
@RunWith(AndroidJUnit4.class)
public class WebMediaPermissionStoreTest {

    private WebMediaPermissionStore store;
    private String origin;

    /**
     * 每个用例使用一个新的测试 origin，避免影响其他设备测试。
     */
    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        store = new WebMediaPermissionStore(context);
        origin = "https://media-test-" + System.nanoTime() + ".example";
    }

    /**
     * 清理本用例写入的设备私有授权记录。
     */
    @After
    public void tearDown() {
        store.revoke(origin);
    }

    /**
     * 默认 HTTPS 端口和任意路径都归为同一个 origin。
     */
    @Test
    public void normalizesSchemeHostDefaultPortAndPath() {
        String withPath = WebMediaPermissionOrigin.from(Uri.parse(origin + ":443/path/to/page?x=1#part"));
        String withoutPath = WebMediaPermissionOrigin.from(Uri.parse(origin + "/another/page"));
        assertEquals(origin, withPath);
        assertEquals(origin, withoutPath);
    }

    /**
     * 录音和摄像分别记录；确认录音不会自动授予摄像。
     */
    @Test
    public void storesOnlyConfirmedMediaResources() {
        store.grant(origin, WebMediaPermissionStore.AUDIO);
        assertEquals(WebMediaPermissionStore.AUDIO, store.getGranted(origin));
        store.grant(origin, WebMediaPermissionStore.VIDEO);
        assertEquals(WebMediaPermissionStore.AUDIO | WebMediaPermissionStore.VIDEO,
                store.getGranted(origin));
    }

    /**
     * 协议或非默认端口改变时视为不同来源，不能复用已授予的网站权限。
     */
    @Test
    public void separatesDifferentOrigins() {
        String http = "http" + origin.substring("https".length());
        String customPort = origin + ":8443";
        store.grant(origin, WebMediaPermissionStore.AUDIO);
        assertEquals(0, store.getGranted(http));
        assertEquals(0, store.getGranted(customPort));
    }

    /**
     * 文件、本地数据等没有可验证网络 origin 的地址不能获得持久化媒体授权。
     */
    @Test
    public void rejectsNonWebOrigins() {
        assertNull(WebMediaPermissionOrigin.from(Uri.parse("file:///android_asset/page.html")));
        assertNull(WebMediaPermissionOrigin.from(Uri.parse("data:text/html,hello")));
        assertNull(WebMediaPermissionOrigin.from(Uri.parse("https:///missing-host")));
    }
}