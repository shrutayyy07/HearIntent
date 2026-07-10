package com.hearintent.backend.upload;

import com.hearintent.backend.security.AuthenticatedUser;
import com.hearintent.backend.upload.dto.FileProcessingResultDto;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
public class FileUploadController {

    private final FileUploadService fileUploadService;

    public FileUploadController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public Mono<ResponseEntity<FileProcessingResultDto>> uploadMedia(@RequestPart("file") FilePart filePart) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> (AuthenticatedUser) ctx.getAuthentication().getPrincipal())
                .map(AuthenticatedUser::userId)
                .switchIfEmpty(Mono.just(UUID.randomUUID()))
                .flatMap(userId -> fileUploadService.processUploadedMedia(userId, filePart))
                .map(ResponseEntity::ok);
    }
}
