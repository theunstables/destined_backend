package com.loveos.api.media;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.ApiResponse;
import com.loveos.api.core.AppException;
import java.io.InputStream;
import java.io.IOException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MediaController {

  private final MediaService mediaService;

  public MediaController(MediaService mediaService) {
    this.mediaService = mediaService;
  }

  @PostMapping(path = "/v1/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<MediaService.UploadedMedia>> upload(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestPart("file") MultipartFile file) throws IOException {
    if (principal == null) throw AppException.unauthorized("Sign in to continue");
    return ResponseEntity.status(201)
        .body(ApiResponse.of(mediaService.upload(principal.userId(),
            new MediaService.UploadInput(file.getInputStream(), file.getContentType(), file.getSize()))));
  }

  @GetMapping("/media/{key}")
  public ResponseEntity<InputStreamResource> download(@PathVariable String key) {
    MediaService.StoredMedia media = mediaService.load(key);
    InputStream content = media.object().content();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(media.contentType()))
        .contentLength(media.object().bytes())
        .cacheControl(CacheControl.noCache())
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "default-src 'none'; sandbox")
        .body(new InputStreamResource(content));
  }
}