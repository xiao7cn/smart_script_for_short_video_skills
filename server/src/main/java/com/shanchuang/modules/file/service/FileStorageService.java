package com.shanchuang.modules.file.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanchuang.common.exception.BizException;
import com.shanchuang.common.util.IdUtil;
import com.shanchuang.modules.file.entity.StoredFile;
import com.shanchuang.modules.file.mapper.StoredFileMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

/**
 * 素材存储。
 *
 * 录屏与音频存私有目录，不进公共 CDN：这些素材涉及他人肖像与声音，
 * Skill 的合规底线要求不外传。
 */
@Service
public class FileStorageService {

    private static final List<String> ALLOWED_EXT = List.of("mp4", "mov", "m4a", "mp3", "wav");
    private static final long MAX_BYTES = 500L * 1024 * 1024;

    private final StoredFileMapper mapper;
    private final Path root;

    public FileStorageService(StoredFileMapper mapper,
                              @Value("${app.storage.dir:./data/uploads}") String dir) {
        this.mapper = mapper;
        this.root = Paths.get(dir).toAbsolutePath().normalize();
    }

    public StoredFile store(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("文件为空");
        }
        if (file.getSize() > MAX_BYTES) {
            throw BizException.of(413, "文件超过 500MB 限制");
        }
        String original = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String ext = extensionOf(original);
        if (!ALLOWED_EXT.contains(ext)) {
            throw BizException.badRequest("仅支持 " + String.join(" / ", ALLOWED_EXT));
        }

        String fileId = IdUtil.fileId();
        // 按用户分目录，顺带避免单目录文件数爆炸
        Path dir = root.resolve(String.valueOf(userId));
        Path target = dir.resolve(fileId + "." + ext);
        try {
            Files.createDirectories(dir);
            file.transferTo(target);
        } catch (IOException e) {
            throw BizException.of(500, "文件保存失败: " + e.getMessage());
        }

        StoredFile row = new StoredFile();
        row.setFileId(fileId);
        row.setUserId(userId);
        row.setFileName(original);
        row.setStorePath(root.relativize(target).toString());
        row.setSizeBytes(file.getSize());
        row.setMime(file.getContentType());
        mapper.insert(row);
        return row;
    }

    /** 交给 Agent 侧工具用的绝对路径。必须校验归属，否则能靠 fileId 读别人的素材 */
    public String absolutePath(Long userId, String fileId) {
        StoredFile row = mapper.selectOne(Wrappers.<StoredFile>lambdaQuery()
                .eq(StoredFile::getFileId, fileId)
                .last("limit 1"));
        if (row == null) {
            throw BizException.notFound("文件不存在");
        }
        if (!row.getUserId().equals(userId)) {
            throw BizException.forbidden("无权访问该文件");
        }
        return root.resolve(row.getStorePath()).toString();
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
