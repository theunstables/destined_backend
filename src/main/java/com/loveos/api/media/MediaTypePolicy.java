package com.loveos.api.media;

import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import java.util.Arrays;
import java.util.Map;

final class MediaTypePolicy {

  private static final Map<String, String> EXTENSIONS = Map.ofEntries(
      Map.entry("image/jpeg", ".jpg"), Map.entry("image/png", ".png"),
      Map.entry("image/webp", ".webp"), Map.entry("image/heic", ".heic"),
      Map.entry("video/mp4", ".mp4"), Map.entry("video/quicktime", ".mov"),
      Map.entry("audio/m4a", ".m4a"), Map.entry("audio/mp4", ".m4a"),
      Map.entry("audio/mpeg", ".mp3"), Map.entry("audio/webm", ".webm"),
      Map.entry("audio/ogg", ".ogg"));

  private MediaTypePolicy() {}

  static String extension(String contentType, byte[] header) {
    String type = contentType == null ? "" : contentType.toLowerCase().split(";", 2)[0].trim();
    String extension = EXTENSIONS.get(type);
    if (extension == null || !matches(type, header)) {
      throw new AppException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Cannot accept " + type);
    }
    return extension;
  }

  private static boolean matches(String type, byte[] bytes) {
    if (type.equals("image/jpeg")) return starts(bytes, 0xff, 0xd8, 0xff);
    if (type.equals("image/png")) return starts(bytes, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
    if (type.equals("image/webp")) return ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP");
    if (type.equals("audio/ogg")) return ascii(bytes, 0, "OggS");
    if (type.equals("audio/webm")) return starts(bytes, 0x1a, 0x45, 0xdf, 0xa3);
    if (type.equals("audio/mpeg")) {
      return ascii(bytes, 0, "ID3") || (bytes.length >= 2
          && (bytes[0] & 0xff) == 0xff && ((bytes[1] & 0xe0) == 0xe0));
    }
    if (type.equals("image/heic")) return isIsoBrand(bytes, "heic", "heix", "hevc", "hevx", "mif1", "msf1");
    if (type.equals("video/quicktime")) return isIsoBrand(bytes, "qt  ");
    if (type.equals("video/mp4")) return isIsoBrand(bytes, "isom", "iso2", "mp41", "mp42", "avc1", "M4V ");
    if (type.equals("audio/mp4") || type.equals("audio/m4a")) return isIsoBrand(bytes, "M4A ", "M4B ", "mp42", "isom");
    return false;
  }

  private static boolean isIsoBrand(byte[] bytes, String... brands) {
    if (bytes.length < 12 || !ascii(bytes, 4, "ftyp")) return false;
    String brand = new String(Arrays.copyOfRange(bytes, 8, 12), java.nio.charset.StandardCharsets.US_ASCII);
    return Arrays.asList(brands).contains(brand);
  }

  private static boolean ascii(byte[] bytes, int offset, String expected) {
    byte[] value = expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    if (bytes.length < offset + value.length) return false;
    for (int i = 0; i < value.length; i++) if (bytes[offset + i] != value[i]) return false;
    return true;
  }

  private static boolean starts(byte[] bytes, int... expected) {
    if (bytes.length < expected.length) return false;
    for (int i = 0; i < expected.length; i++) if ((bytes[i] & 0xff) != expected[i]) return false;
    return true;
  }
}