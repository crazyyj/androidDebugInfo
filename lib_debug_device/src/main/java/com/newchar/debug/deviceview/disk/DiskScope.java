package com.newchar.debug.deviceview.disk;

import java.io.File;

final class DiskScope {
    final String name;
    final File dir;

    DiskScope(String name, File dir) {
        this.name = name;
        this.dir = dir;
    }
}
