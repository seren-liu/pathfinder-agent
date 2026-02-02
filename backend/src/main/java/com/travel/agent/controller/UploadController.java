package com.travel.agent.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import com.travel.agent.dto.response.CommonResponse;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${s3.bucket}")
    private String bucket;

    @Value("${s3.region}")
    private String region;

    @PostMapping("/presign")
    public CommonResponse<PresignResp> presign(@RequestBody PresignReq req) {
        String ext = FilenameUtils.getExtension(req.getFileName());
        String key = String.format("trip-%d/%s/%s.%s",
                req.getTripId() == null ? 0 : req.getTripId(),
                LocalDate.now(),
                UUID.randomUUID(),
                ext
        );

        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(req.getContentType())
                .build();

        PresignedPutObjectRequest signed = s3Presigner.presignPutObject(b -> b
                .signatureDuration(Duration.ofMinutes(3))
                .putObjectRequest(put));

        String publicUrl = String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, key);
        PresignResp resp = new PresignResp();
        resp.setUploadUrl(signed.url().toString());
        resp.setPublicUrl(publicUrl);
        resp.setKey(key);
        return CommonResponse.success(resp);
    }

    @DeleteMapping("/object")
    public CommonResponse<Boolean> deleteObject(@RequestParam("key") String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        return CommonResponse.success(Boolean.TRUE);
    }

    @Data
    public static class PresignReq {
        private Long tripId;
        private String fileName;
        private String contentType;
    }

    @Data
    public static class PresignResp {
        private String uploadUrl;
        private String publicUrl;
        private String key;
    }
}
