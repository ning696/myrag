package com.zc.iflyzcragback.dto;

import java.io.InputStream;

public record DocumentDownloadResource(
        String filename,
        Long fileSize,
        String fileType,
        InputStream inputStream
) {
}
