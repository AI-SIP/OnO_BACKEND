package com.aisip.OnO.backend.util.ai;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * AI 분석에 보낼 문제 이미지를 서버가 S3 에서 직접 읽어 data URL 로 바꾼다.
 *
 * 이전에는 S3 URL 을 그대로 넘겨 OpenAI 가 직접 내려받았는데, 그 다운로드가 타임아웃 나면 400 으로 실패했다.
 * 등록 경로 중 일부는 URL 을 검증하지 않으므로 우리 버킷 URL 만 받고, 읽기는 URL 이 아니라 bucket/key 로 한다.
 * 이미지 바이트와 base64 는 크기가 커서 로그에 남기지 않는다. 로그에는 key 만 남긴다.
 */
@Slf4j
@Component
public class AnalysisImageLoader {

    static final long MAX_OBJECT_BYTES = 20L * 1024 * 1024;
    static final int MAX_LONG_SIDE = 2048;
    private static final float JPEG_QUALITY = 0.85f;
    private static final String S3_HOST_SUFFIX = ".amazonaws.com/";
    private static final Set<String> HEIF_BRANDS = Set.of("heic", "heix", "hevc", "heim", "heis", "mif1", "msf1");

    private final AmazonS3Client amazonS3Client;
    private final String bucket;
    private final int maxImages;

    public AnalysisImageLoader(
            AmazonS3Client amazonS3Client,
            @Value("${cloud.aws.s3.bucket}") String bucket,
            @Value("${openai.analysis.max-images:5}") int maxImages
    ) {
        this.amazonS3Client = amazonS3Client;
        this.bucket = bucket;
        this.maxImages = maxImages;
    }

    enum ImageFormat {
        JPEG("image/jpeg"), PNG("image/png"), GIF("image/gif"), WEBP("image/webp");

        private final String mimeType;

        ImageFormat(String mimeType) {
            this.mimeType = mimeType;
        }
    }

    /**
     * 이미지 URL 목록을 OpenAI image_url.url 에 넣을 data URL 목록으로 바꾼다.
     * 재시도로 해결되지 않는 문제(외부 URL, 형식, 장수)는 NonRetryableAnalysisException,
     * 그 밖의 S3/IO 오류는 재시도 대상인 일반 RuntimeException 으로 던진다.
     */
    public List<String> loadAsDataUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new NonRetryableAnalysisException(AnalysisFailureReason.UNREADABLE_IMAGE, "분석할 문제 이미지가 없습니다.");
        }
        // S3 를 읽기 전에 판정해야 많은 장수 요청이 재시도마다 S3 를 두드리지 않는다
        if (imageUrls.size() > maxImages) {
            throw new NonRetryableAnalysisException(AnalysisFailureReason.TOO_MANY_IMAGES,
                    "문제 이미지가 " + imageUrls.size() + "장으로 상한 " + maxImages + "장을 넘습니다.");
        }

        List<String> keys = imageUrls.stream().map(this::extractKey).toList();
        List<String> dataUrls = new ArrayList<>(keys.size());
        for (String key : keys) {
            dataUrls.add(toDataUrl(key, readObject(key)));
        }
        return dataUrls;
    }

    String extractKey(String imageUrl) {
        String prefix = "https://" + bucket + ".s3.";
        if (imageUrl == null || !imageUrl.startsWith(prefix)) {
            throw new NonRetryableAnalysisException(AnalysisFailureReason.INVALID_IMAGE_SOURCE,
                    "우리 S3 버킷의 이미지 URL 이 아닙니다.");
        }
        int suffixIndex = imageUrl.indexOf(S3_HOST_SUFFIX, prefix.length());
        // 접두어와 .amazonaws.com/ 사이에 경로(/)가 끼어 있으면 호스트가 S3 가 아니다
        if (suffixIndex < 0 || imageUrl.substring(prefix.length(), suffixIndex).contains("/")) {
            throw new NonRetryableAnalysisException(AnalysisFailureReason.INVALID_IMAGE_SOURCE,
                    "S3 이미지 URL 형식이 아닙니다.");
        }
        String key = imageUrl.substring(suffixIndex + S3_HOST_SUFFIX.length());
        if (key.isBlank()) {
            throw new NonRetryableAnalysisException(AnalysisFailureReason.INVALID_IMAGE_SOURCE,
                    "S3 이미지 URL 에 key 가 없습니다.");
        }
        return key;
    }

    private byte[] readObject(String key) {
        S3Object s3Object;
        try {
            s3Object = amazonS3Client.getObject(bucket, key);
        } catch (AmazonServiceException e) {
            if (e.getStatusCode() == 404 || "NoSuchKey".equals(e.getErrorCode())) {
                throw new NonRetryableAnalysisException(AnalysisFailureReason.INVALID_IMAGE_SOURCE,
                        "S3 에 이미지가 없습니다. key=" + key, e);
            }
            throw new RuntimeException("S3 이미지 조회에 실패했습니다. key=" + key, e);
        } catch (RuntimeException e) {
            throw new RuntimeException("S3 이미지 조회에 실패했습니다. key=" + key, e);
        }

        try (S3Object object = s3Object; S3ObjectInputStream content = object.getObjectContent()) {
            long contentLength = object.getObjectMetadata() == null ? -1 : object.getObjectMetadata().getContentLength();
            if (contentLength > MAX_OBJECT_BYTES) {
                // 남은 본문을 다 받지 않고 연결을 끊는다
                content.abort();
                throw tooLarge(key, contentLength);
            }
            byte[] bytes = readAtMost(content, MAX_OBJECT_BYTES);
            if (bytes == null) {
                content.abort();
                throw tooLarge(key, -1);
            }
            return bytes;
        } catch (NonRetryableAnalysisException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new RuntimeException("S3 이미지를 읽는 중 오류가 발생했습니다. key=" + key, e);
        }
    }

    private NonRetryableAnalysisException tooLarge(String key, long contentLength) {
        return new NonRetryableAnalysisException(AnalysisFailureReason.UNSUPPORTED_IMAGE,
                "이미지가 20MB 를 넘습니다. key=" + key + ", size=" + contentLength);
    }

    /** 메타데이터의 크기를 믿지 못할 때를 위해 상한을 넘으면 null 을 돌려준다. */
    private byte[] readAtMost(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                return null;
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private String toDataUrl(String key, byte[] bytes) {
        ImageFormat format = detectFormat(bytes);
        if (format == null) {
            throw new NonRetryableAnalysisException(AnalysisFailureReason.UNSUPPORTED_IMAGE,
                    (isHeif(bytes) ? "HEIC/HEIF" : "알 수 없는") + " 형식의 이미지입니다. key=" + key);
        }

        EncodedImage encoded = new EncodedImage(bytes, format.mimeType);
        if (format == ImageFormat.JPEG || format == ImageFormat.PNG) {
            encoded = resizeIfNeeded(key, bytes, format);
        }
        log.info("Loaded analysis image - key: {}, format: {}, originalBytes: {}, sentBytes: {}",
                key, encoded.mimeType(), bytes.length, encoded.bytes().length);
        return "data:" + encoded.mimeType() + ";base64," + Base64.getEncoder().encodeToString(encoded.bytes());
    }

    static ImageFormat detectFormat(byte[] bytes) {
        if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) {
            return ImageFormat.JPEG;
        }
        if (startsWith(bytes, 0, 0x89, 0x50, 0x4E, 0x47)) {
            return ImageFormat.PNG;
        }
        if (asciiAt(bytes, 0, "GIF8")) {
            return ImageFormat.GIF;
        }
        if (asciiAt(bytes, 0, "RIFF") && asciiAt(bytes, 8, "WEBP")) {
            return ImageFormat.WEBP;
        }
        return null;
    }

    static boolean isHeif(byte[] bytes) {
        // ISO BMFF: 4바이트 box 크기 다음에 'ftyp', 그 뒤 4바이트가 major brand
        return asciiAt(bytes, 4, "ftyp") && bytes.length >= 12
                && HEIF_BRANDS.contains(new String(bytes, 8, 4, StandardCharsets.US_ASCII));
    }

    private static boolean startsWith(byte[] bytes, int offset, int... expected) {
        if (bytes.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean asciiAt(byte[] bytes, int offset, String expected) {
        return bytes.length >= offset + expected.length()
                && new String(bytes, offset, expected.length(), StandardCharsets.US_ASCII).equals(expected);
    }

    record EncodedImage(byte[] bytes, String mimeType) {
    }

    /**
     * 긴 변이 2048px 를 넘는 JPEG/PNG 만 줄인다. OpenAI 도 2048 로 줄여서 보므로 판독 품질은 같고 전송량만 준다.
     * 디코딩하지 못하면(CMYK JPEG 등) 원본을 그대로 보낸다. 줄이지 못했다고 분석을 실패시키지는 않는다.
     */
    private EncodedImage resizeIfNeeded(String key, byte[] bytes, ImageFormat format) {
        EncodedImage original = new EncodedImage(bytes, format.mimeType);
        // 다시 인코딩하면 EXIF 가 사라진다. 회전 정보가 있는 사진은 줄이면 옆으로 누운 채로 가므로 원본을 보낸다
        if (format == ImageFormat.JPEG && hasNonDefaultExifOrientation(bytes)) {
            return original;
        }
        try {
            BufferedImage decoded = decodeForLongSide(bytes, MAX_LONG_SIDE);
            if (decoded == null) {
                return original;
            }
            BufferedImage resized = scaleDown(decoded, MAX_LONG_SIDE);
            boolean keepAlpha = format == ImageFormat.PNG && resized.getColorModel().hasAlpha();
            byte[] reencoded = keepAlpha ? encodePng(resized) : encodeJpeg(resized);
            return new EncodedImage(reencoded, keepAlpha ? ImageFormat.PNG.mimeType : ImageFormat.JPEG.mimeType);
        } catch (Exception e) {
            log.warn("Analysis image resize failed, sending original - key: {}, error: {}", key, e.toString());
            return original;
        }
    }

    /**
     * 원본 크기를 먼저 읽고, 아주 큰 이미지는 서브샘플링으로 디코딩해 메모리를 아낀다(긴 변이 목표의 2배 미만이 되게).
     * 원본이 이미 목표 이하이면 디코딩하지 않고 null 을 돌려준다. 서브샘플링 뒤에도 긴 변은 목표보다 크다.
     */
    private BufferedImage decodeForLongSide(byte[] bytes, int targetLongSide) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                int longSide = Math.max(width, height);
                if (longSide <= targetLongSide) {
                    return null;
                }
                int subsampling = Math.max(1, longSide / (targetLongSide * 2));
                ImageReadParam param = reader.getDefaultReadParam();
                if (subsampling > 1) {
                    param.setSourceSubsampling(subsampling, subsampling, 0, 0);
                }
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
    }

    /** 한 번에 크게 줄이면 계단 현상이 생기므로 절반씩 줄이다가 마지막에 목표 크기로 맞춘다. */
    static BufferedImage scaleDown(BufferedImage source, int targetLongSide) {
        boolean hasAlpha = source.getColorModel().hasAlpha();
        int imageType = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        int longSide = Math.max(source.getWidth(), source.getHeight());
        double scale = Math.min(1.0, (double) targetLongSide / longSide);
        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage current = source;
        int width = source.getWidth();
        int height = source.getHeight();
        do {
            width = Math.max(targetWidth, width / 2);
            height = Math.max(targetHeight, height / 2);
            BufferedImage next = new BufferedImage(width, height, imageType);
            Graphics2D g = next.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.drawImage(current, 0, 0, width, height, null);
            } finally {
                g.dispose();
            }
            current = next;
        } while (width != targetWidth || height != targetHeight);
        return current;
    }

    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("PNG writer 가 없습니다.");
        }
        return out.toByteArray();
    }

    /**
     * JPEG 의 APP1(Exif) 에서 Orientation(0x0112) 이 1 이 아닌지 본다. 해석하지 못하면 false.
     */
    static boolean hasNonDefaultExifOrientation(byte[] jpeg) {
        int offset = 2;
        while (offset + 4 <= jpeg.length && (jpeg[offset] & 0xFF) == 0xFF) {
            int marker = jpeg[offset + 1] & 0xFF;
            if (marker == 0xDA || marker == 0xD9) {
                return false;
            }
            int segmentLength = ((jpeg[offset + 2] & 0xFF) << 8) | (jpeg[offset + 3] & 0xFF);
            int dataStart = offset + 4;
            if (marker == 0xE1 && asciiAt(jpeg, dataStart, "Exif") && dataStart + 6 + 8 <= jpeg.length) {
                Integer orientation = readOrientation(jpeg, dataStart + 6, Math.min(jpeg.length, offset + 2 + segmentLength));
                return orientation != null && orientation != 1;
            }
            offset += 2 + segmentLength;
        }
        return false;
    }

    private static Integer readOrientation(byte[] data, int tiffStart, int end) {
        boolean littleEndian;
        if (asciiAt(data, tiffStart, "II")) {
            littleEndian = true;
        } else if (asciiAt(data, tiffStart, "MM")) {
            littleEndian = false;
        } else {
            return null;
        }
        int ifdOffset = readInt(data, tiffStart + 4, littleEndian, end);
        int ifdStart = tiffStart + ifdOffset;
        int entryCount = readShort(data, ifdStart, littleEndian, end);
        for (int i = 0; i < entryCount; i++) {
            int entry = ifdStart + 2 + i * 12;
            if (entry + 12 > end) {
                return null;
            }
            if (readShort(data, entry, littleEndian, end) == 0x0112) {
                return readShort(data, entry + 8, littleEndian, end);
            }
        }
        return null;
    }

    private static int readShort(byte[] data, int offset, boolean littleEndian, int end) {
        if (offset < 0 || offset + 2 > end) {
            return -1;
        }
        int b0 = data[offset] & 0xFF;
        int b1 = data[offset + 1] & 0xFF;
        return littleEndian ? (b1 << 8) | b0 : (b0 << 8) | b1;
    }

    private static int readInt(byte[] data, int offset, boolean littleEndian, int end) {
        if (offset < 0 || offset + 4 > end) {
            return -1;
        }
        int b0 = data[offset] & 0xFF;
        int b1 = data[offset + 1] & 0xFF;
        int b2 = data[offset + 2] & 0xFF;
        int b3 = data[offset + 3] & 0xFF;
        return littleEndian
                ? (b3 << 24) | (b2 << 16) | (b1 << 8) | b0
                : (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }
}
