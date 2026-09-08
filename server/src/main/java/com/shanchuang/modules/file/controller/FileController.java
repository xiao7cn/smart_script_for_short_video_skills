package com.shanchuang.modules.file.controller;

import com.shanchuang.common.result.R;
import com.shanchuang.common.security.CurrentUser;
import com.shanchuang.modules.file.entity.StoredFile;
import com.shanchuang.modules.file.service.FileStorageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileStorageService fileStorage;

    public FileController(FileStorageService fileStorage) {
        this.fileStorage = fileStorage;
    }

    @PostMapping
    public R<UploadVO> upload(@RequestParam("file") MultipartFile file) {
        StoredFile stored = fileStorage.store(CurrentUser.id(), file);
        return R.ok(new UploadVO(stored.getFileId(), stored.getFileName(), stored.getSizeBytes()));
    }

    public record UploadVO(String fileId, String fileName, Long size) {
    }
}
