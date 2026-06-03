package com.zc.iflyzcragback.service.storage;

import java.io.InputStream;

/**
 * 文件存储抽象。当前实现：MinIO（{@link MinioFileStorageService}）。
 * 后续如需切换到 S3 / 本地磁盘，只需新增一个实现并通过配置切换。
 */
public interface FileStorageService {

    /**
     * 上传文件。
     *
     * @param userId        所属用户 ID（用于路径隔离）
     * @param originalName  原始文件名（保留扩展名）
     * @param contentType   MIME 类型，可为 null
     * @param size          文件字节数
     * @param input         文件输入流，由调用方负责关闭
     * @return 存储后的 object key（可用于后续下载/删除）
     */
    String upload(Long userId, String originalName, String contentType, long size, InputStream input);

    /**
     * 下载文件，返回的输入流由调用方关闭。
     */
    InputStream download(String objectKey);

    /**
     * 删除文件。对象不存在时不抛错。
     */
    void delete(String objectKey);
}
