package com.anju.domain.file;

import com.anju.common.Result;
import com.anju.domain.file.dto.ChunkUploadRequest;
import com.anju.domain.file.dto.FileUploadRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/file")
@Tag(name = "File", description = "File upload, preview, recycle bin, and version endpoints")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/upload-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','OPERATOR')")
    @Operation(summary = "Upload a real file")
    public Result<SysFile> uploadFile(@RequestParam("file") MultipartFile file, 
                                      @RequestParam(value = "hash", required = false) String hash,
                                      @RequestParam(value = "expiresAt", required = false) java.time.LocalDateTime expiresAt) {
        FileUploadRequest request = new FileUploadRequest();
        request.setFile(file);
        request.setHash(hash);
        request.setExpiresAt(expiresAt);
        return Result.success(fileService.uploadFile(request));
    }

    @GetMapping("/check")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','OPERATOR')")
    @Operation(summary = "Check fast upload by hash")
    public Result<Object> checkFastUpload(@RequestParam String hash) {
        SysFile sysFile = fileService.checkFastUpload(hash);
        if (sysFile != null && sysFile.getStoragePath() != null) {
            return Result.success(sysFile); // Instant upload success
        }
        return Result.success(null); // File not found, proceed to normal upload
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','OPERATOR')")
    @Operation(summary = "Upload file chunk")
    public Result<SysFile> uploadChunk(@RequestBody ChunkUploadRequest request) {
        return Result.success(fileService.uploadChunk(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','OPERATOR')")
    @Operation(summary = "Get file by id")
    public Result<SysFile> getFile(@PathVariable Long id) {
        return Result.success(fileService.getFile(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','OPERATOR')")
    @Operation(summary = "List accessible files")
    public Result<List<SysFile>> listFiles() {
        return Result.success(fileService.listAccessibleFiles());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','OPERATOR')")
    @Operation(summary = "Move file to recycle bin")
    public Result<SysFile> moveToRecycleBin(@PathVariable Long id) {
        return Result.success(fileService.moveToRecycleBin(id));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','OPERATOR')")
    @Operation(summary = "Restore file from recycle bin")
    public Result<SysFile> restoreFromRecycleBin(@PathVariable Long id) {
        return Result.success(fileService.restoreFromRecycleBin(id));
    }

    @GetMapping("/recycle-bin")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','OPERATOR')")
    @Operation(summary = "List recycle bin")
    public Result<List<SysFile>> listRecycleBin() {
        return Result.success(fileService.listRecycleBin());
    }

    @PostMapping("/{hash}/rollback/{targetVersion}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','OPERATOR')")
    @Operation(summary = "Rollback file version")
    public Result<SysFile> rollbackVersion(@PathVariable String hash, @PathVariable Integer targetVersion) {
        return Result.success(fileService.rollbackVersion(hash, targetVersion));
    }

    @GetMapping("/{id}/preview")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','OPERATOR')")
    @Operation(summary = "Preview file metadata")
    public Result<Map<String, Object>> preview(@PathVariable Long id) {
        return Result.success(fileService.preview(id));
    }

    @GetMapping("/{id}/content")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','OPERATOR')")
    @Operation(summary = "Get content descriptor")
    public Result<Map<String, Object>> content(@PathVariable Long id) {
        return Result.success(fileService.contentDescriptor(id));
    }
}
