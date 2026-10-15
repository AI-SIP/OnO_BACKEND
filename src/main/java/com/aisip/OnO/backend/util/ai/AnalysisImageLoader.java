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
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
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
import java.util.regex.Pattern;

/**
 * AI 분석에 보낼 문제 이미지와 해설 이미지를 서버가 S3 에서 직접 읽어 data URL 로 바꾼다.
 *
 * 이전에는 S3 URL 을 그대로 넘겨 OpenAI 가 직접 내려받았는데, 그 다운로드가 타임아웃 나면 400 으로 실패했다.
 * 등록 경로 중 일부는 URL 을 검증하지 않으므로 우리 버킷 URL 만 받고, 읽기는 URL 이 아니라 bucket/key 로 한다.
 * 이미지 바이트와 base64 는 크기가 커서 로그에 남기지 않는다. 로그에는 key 만 남긴다.
 *
 * ImageIO 의 전역 캐시 설정(ImageIO.setUseCache)은 다른 곳의 이미지 처리에도 영향을 주므로 건드리지 않는다.
 * 대신 이 클래스의 입출력 스트림을 MemoryCache 스트림으로 직접 만들어 /tmp 디스크 캐시 파일을 쓰지 않는다.
 */
@Slf4j
@Component
public class AnalysisImageLoader {

    static final long MAX_OBJECT_BYTES = 20L * 1024 * 1024;
    static final long DEFAULT_MAX_TOTAL_BYTES = 30L * 1024 * 1024;
    static final int MAX_LONG_SIDE = 2048;
    /**
     * 헤더의 가로x세로가 이보다 크면 디코딩하지 않는다. 서브샘플링으로 줄여도 디코딩 중 버퍼가 커지고,
     * 문제 사진으로는 나올 일이 없는 크기(예: 8000x5000)다. 압축 폭탄처럼 작은 파일이 큰 크기를 주장하는 경우도 막는다.
     */
    static final long MAX_DECODE_PIXELS = 40_000_000L;

    /**
     * 분석에 쓰는 key 는 FileUploadService 가 만든 업로드 경로만 허용한다. 서버 권한으로 버킷의 다른 객체를 읽지 않게 하기 위해서다.
     * 현재 형식: image/yyyy/MM/dd/{UUID}.{확장자}
     * 서버 업로드는 원본 파일명의 확장자를 그대로 붙여서, 확장자에 공백이나 한글 같은 문자가 있으면 정상 업로드인데도
     * 여기서 거부된다. 드물고 그때도 한도는 돌려주므로, 인코딩이나 질의 문자열이 섞인 key 를 막는 쪽을 택한다.
     */
    private static final Pattern UPLOAD_KEY = Pattern.compile("image/[A-Za-z0-9/._-]+");
    /**
     * 2025-03 이전(커밋 b6d9bdc 전) 서버 업로드 형식: {문제 createdAt(LocalDateTime)}/{이미지 종류}_{원본 파일명}.
     * 이 시기 문제를 "다시 분석하기"로 요청할 수 있어 남겨 둔다. 원본 파일명은 사용자가 정한 것이라 문자를 제한하지 않고
     * 경로 구분자(/)와 제어 문자만 막는다.
     */
    private static final Pattern LEGACY_UPLOAD_KEY = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T[0-9:.]+/(problemImage|answerImage|solveImage|processImage)_[^/\\p{Cntrl}]+");
    private static final float JPEG_QUALITY = 0.85f;
    private static final String S3_HOST_SUFFIX = ".amazonaws.com/";
    private static final Set<String> HEIF_BRANDS = Set.of("heic", "heix", "hevc", "heim", "heis", "mif1", "msf1");

    static final int DEFAULT_MAX_ANSWER_IMAGES = 3;

    /** 해설 이미지를 뺀 이유. 메트릭 ono.ai.analysis.answer_image_skipped 의 reason 태그로 쓴다 */
    static final String SKIP_ACCESS_DENIED = "access_denied";
    static final String SKIP_TOTAL_SIZE = "total_size_limit";
    static final String SKIP_OVER_COUNT = "over_count";

    private final AmazonS3Client amazonS3Client;
    private final String bucket;
    private final int maxImages;
    private final long maxTotalBytes;
    private final int maxAnswerImages;

    public AnalysisImageLoader(
            AmazonS3Client amazonS3Client,
            @Value("${cloud.aws.s3.bucket}") String bucket,
            @Value("${openai.analysis.max-images:5}") int maxImages,
            @Value("${openai.analysis.max-total-bytes:" + DEFAULT_MAX_TOTAL_BYTES + "}") long maxTotalBytes,
            @Value("${openai.analysis.max-answer-images:" + DEFAULT_MAX_ANSWER_IMAGES + "}") int maxAnswerImages
    ) {
        this.amazonS3Client = amazonS3Client;
        this.bucket = bucket;
        this.maxImages = maxImages;
        this.maxTotalBytes = maxTotalBytes;
        this.maxAnswerImages = maxAnswerImages;
    }

    enum ImageFormat {
        JPEG("image/jpeg"), PNG("image/png"), GIF("image/gif"), WEBP("image/webp");

        private final String mimeType;

        ImageFormat(String mimeType) {
            this.mimeType = mimeType;
        }
    }

    /**
     * 문제 이미지 URL 목록을 OpenAI image_url.url 에 넣을 data URL 목록으로 바꾼다.
     * 재시도로 해결되지 않는 문제(외부 URL, 형식, 장수, 크기)는 NonRetryableAnalysisException,
     * 그 밖의 S3/IO 오류는 재시도 대상인 일반 RuntimeException 으로 던진다.
     */
    public List<String> loadAsDataUrls(List<String> imageUrls) {
        return loadForAnalysis(imageUrls, List.of()).problemImages();
    }

    /**
     * 문제 이미지와 해설 이미지를 함께 data URL 로 바꾼다. 총용량 상한은 둘을 합쳐 적용한다.
     *
     * 문제 이미지는 분석 대상이라 지금까지처럼 하나라도 문제가 있으면 분석을 실패시킨다.
     * 해설 이미지는 모델이 답을 맞춰 보는 보조 자료라, 해설 때문에 분석 전체가 실패하지 않게 한다.
     * - 상한(기본 3장)을 넘으면 앞에서부터 상한만큼만 쓴다
     * - key 제한, 형식, 크기 검증은 문제 이미지와 똑같이 하고, 걸리면 그 해설 이미지만 빼고 보낸다
     * - 문제 이미지를 먼저 담고, 해설 이미지를 더해 총용량을 넘으면 그 해설 이미지를 뺀다
     * - S3 403(AccessDenied)은 그 해설 이미지만 뺀다. 문제 이미지의 403 은 IAM 설정 오류를 DLQ 알림으로 드러내려고
     *   재시도 대상으로 두지만, 같은 IAM 오류라면 문제 이미지에서 이미 실패하므로 해설에서까지 재시도할 이유가 없다.
     *   없는 key 에도 403 이 올 수 있어(ListBucket 권한이 없을 때) 해설 한 장 때문에 분석 전체를 재시도시키지 않는다
     * - 그 밖의 S3 일시 오류(5xx, IO 오류)는 문제 이미지와 같이 재시도 대상으로 올린다. 재시도하면 해설까지 넣어 분석할 수 있다
     * - 뺀 이유는 LoadedImages.skippedAnswerReasons 에 담아 호출하는 쪽이 메트릭으로 남긴다
     */
    public LoadedImages loadForAnalysis(List<String> problemImageUrls, List<String> answerImageUrls) {
        if (problemImageUrls == null || problemImageUrls.isEmpty()) {
            throw new NonRetryableAnalysisException(AnalysisFailureReason.UNREADABLE_IMAGE, "분석할 문제 이미지가 없습니다.");
        }
        // S3 를 읽기 전에 판정해야 많은 장수 요청이 재시도마다 S3 를 두드리지 않는다
        if (problemImageUrls.size() > maxImages) {
            throw new NonRetryableAnalysisException(AnalysisFailureReason.TOO_MANY_IMAGES,
                    "문제 이미지가 " + problemImageUrls.size() + "장으로 상한 " + maxImages + "장을 넘습니다.");
        }
        List<String> answers = answerImageUrls == null ? List.of() : answerImageUrls;
        List<String> skippedAnswerReasons = new ArrayList<>();
        if (answers.size() > maxAnswerImages) {
            log.info("Answer images over limit, using first {} of {}", maxAnswerImages, answers.size());
            for (int i = maxAnswerImages; i < answers.size(); i++) {
                skippedAnswerReasons.add(SKIP_OVER_COUNT);
            }
            answers = answers.subList(0, maxAnswerImages);
        }

        List<String> keys = problemImageUrls.stream().map(this::extractKey).toList();
        List<String> problemDataUrls = new ArrayList<>(keys.size());
        long totalBytes = 0;
        for (String key : keys) {
            byte[] bytes;
            try {
                bytes = readObject(key);
            } catch (S3AccessDeniedException e) {
                // 403 은 재시도 대상으로 둔다. 없는 key 에도 403 이 올 수 있지만(ListBucket 권한이 없을 때),
                // 서버 IAM 설정이 깨진 경우에도 403 이 온다. 재시도하지 않으면 모든 분석이 DLQ 알림 없이
                // 조용히 실패하므로, 드문 없는 key 의 재시도 비용보다 운영자가 알아채는 쪽을 택한다.
                log.error("S3 access denied while loading analysis image - key: {}, errorCode: {}", key, e.errorCode());
                throw e;
            }
            EncodedImage encoded = encode(key, bytes);
            // GIF/WEBP, 회전 정보가 있는 작은 JPEG, 디코딩 실패 원본은 줄이지 않고 보내므로 요청 전체 크기를 따로 막는다
            totalBytes += encoded.bytes().length;
            if (totalBytes > maxTotalBytes) {
                throw new NonRetryableAnalysisException(AnalysisFailureReason.IMAGE_TOO_LARGE,
                        "분석 이미지 합계가 상한 " + maxTotalBytes + " 바이트를 넘습니다. total=" + totalBytes);
            }
            problemDataUrls.add(toDataUrl(encoded));
        }

        List<String> answerDataUrls = new ArrayList<>(answers.size());
        for (String answerUrl : answers) {
            EncodedImage encoded;
            try {
                String key = extractKey(answerUrl);
                encoded = encode(key, readObject(key));
            } catch (NonRetryableAnalysisException e) {
                log.warn("Skipping answer image - reason: {}, message: {}", e.getReason().tag(), e.getMessage());
                skippedAnswerReasons.add(e.getReason().tag());
                continue;
            } catch (S3AccessDeniedException e) {
                log.warn("Skipping answer image - reason: {}, message: {}", SKIP_ACCESS_DENIED, e.getMessage());
                skippedAnswerReasons.add(SKIP_ACCESS_DENIED);
                continue;
            }
            if (totalBytes + encoded.bytes().length > maxTotalBytes) {
                log.warn("Skipping answer image over total limit - total: {}, image: {}, limit: {}",
                        totalBytes, encoded.bytes().length, maxTotalBytes);
                skippedAnswerReasons.add(SKIP_TOTAL_SIZE);
                continue;
            }
            totalBytes += encoded.bytes().length;
            answerDataUrls.add(toDataUrl(encoded));
        }
        return new LoadedImages(problemDataUrls, answerDataUrls, skippedAnswerReasons);
    }

    /**
     * 분석에 보낼 data URL. 문제 이미지와 해설 이미지를 나눠 담아 프롬프트에서 라벨을 따로 붙인다.
     * skippedAnswerReasons 는 빼고 보낸 해설 이미지마다 그 이유(SKIP_* 또는 AnalysisFailureReason 태그)다.
     */
    public record LoadedImages(List<String> problemImages, List<String> answerImages, List<String> skippedAnswerReasons) {
        public LoadedImages {
            problemImages = List.copyOf(problemImages);
            answerImages = List.copyOf(answerImages);
            skippedAnswerReasons = skippedAnswerReasons == null ? List.of() : List.copyOf(skippedAnswerReasons);
        }

        public LoadedImages(List<String> problemImages, List<String> answerImages) {
            this(problemImages, answerImages, List.of());
        }
    }

    /**
     * S3 403(AccessDenied). 재시도 대상 실패라 일반 RuntimeException 을 잇는다.
     * 문제 이미지에서는 그대로 올려 재시도시키고, 해설 이미지에서는 그 장만 빼는 데 쓰려고 따로 구분한다.
     */
    static final class S3AccessDeniedException extends RuntimeException {
        private final String errorCode;

        S3AccessDeniedException(String message, AmazonServiceException cause) {
            super(message, cause);
            this.errorCode = cause.getErrorCode();
        }

        String errorCode() {
            return errorCode;
        }
    }

    private static String toDataUrl(EncodedImage encoded) {
        return "data:" + encoded.mimeType() + ";base64," + Base64.getEncoder().encodeToString(encoded.bytes());
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
        if (!isUploadKey(key)) {
            throw new NonRetryableAnalysisException(AnalysisFailureReason.INVALID_IMAGE_SOURCE,
                    "업로드 경로의 key 가 아닙니다.");
        }
        return key;
    }

    static boolean isUploadKey(String key) {
        if (key.contains("..")) {
            return false;
        }
        return UPLOAD_KEY.matcher(key).matches() || LEGACY_UPLOAD_KEY.matcher(key).matches();
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
            // 403 을 재시도할지, 그 장만 뺄지는 문제 이미지인지 해설 이미지인지에 따라 loadForAnalysis 가 정한다
            if (e.getStatusCode() == 403 || "AccessDenied".equals(e.getErrorCode())) {
                throw new S3AccessDeniedException("S3 이미지 조회 권한이 없습니다. key=" + key, e);
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
        return new NonRetryableAnalysisException(AnalysisFailureReason.IMAGE_TOO_LARGE,
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

    private EncodedImage encode(String key, byte[] bytes) {
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
        return encoded;
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
     * 다만 헤더가 MAX_DECODE_PIXELS 를 넘는 크기를 말하면 디코딩하지 않고 IMAGE_TOO_LARGE 로 끝낸다.
     *
     * 다시 인코딩하면 EXIF 가 사라진다. 회전(3/6/8)은 픽셀을 직접 돌려서 줄이고,
     * 반전이 섞인 값(2/4/5/7)은 드물어 원본을 보낸다. 긴 변이 2048 이하이면 회전 사진도 원본을 그대로 보낸다.
     */
    private EncodedImage resizeIfNeeded(String key, byte[] bytes, ImageFormat format) {
        EncodedImage original = new EncodedImage(bytes, format.mimeType);
        int orientation = format == ImageFormat.JPEG ? exifOrientation(bytes) : 1;
        if (orientation != 1 && !isRotationOnly(orientation)) {
            return original;
        }
        try {
            BufferedImage decoded = decodeForLongSide(bytes, MAX_LONG_SIDE);
            if (decoded == null) {
                return original;
            }
            BufferedImage resized = rotate(scaleDown(decoded, MAX_LONG_SIDE), orientation);
            boolean keepAlpha = format == ImageFormat.PNG && resized.getColorModel().hasAlpha();
            byte[] reencoded = keepAlpha ? encodePng(resized) : encodeJpeg(resized);
            return new EncodedImage(reencoded, keepAlpha ? ImageFormat.PNG.mimeType : ImageFormat.JPEG.mimeType);
        } catch (NonRetryableAnalysisException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Analysis image resize failed, sending original - key: {}, error: {}", key, e.toString());
            return original;
        }
    }

    private static boolean isRotationOnly(int orientation) {
        return orientation == 3 || orientation == 6 || orientation == 8;
    }

    /**
     * 원본 크기를 먼저 읽고, 큰 이미지는 서브샘플링으로 디코딩해 메모리를 아낀다.
     * 원본이 이미 목표 이하이면 디코딩하지 않고 null 을 돌려준다.
     */
    static BufferedImage decodeForLongSide(byte[] bytes, int targetLongSide) throws IOException {
        // ImageIO.createImageInputStream 은 전역 설정에 따라 /tmp 에 캐시 파일을 만든다. 메모리 캐시 스트림을 직접 쓴다
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_DECODE_PIXELS) {
                    throw new NonRetryableAnalysisException(AnalysisFailureReason.IMAGE_TOO_LARGE,
                            "이미지 픽셀 수가 상한을 넘습니다. size=" + width + "x" + height);
                }
                int longSide = Math.max(width, height);
                if (longSide <= targetLongSide) {
                    return null;
                }
                int subsampling = subsamplingFor(longSide, targetLongSide);
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

    /**
     * 디코딩한 긴 변(= ceil(longSide / s))이 목표의 2배 미만이 되는 가장 작은 서브샘플링 값.
     * ceil(longSide / s) <= 2*target - 1 이려면 s >= longSide / (2*target - 1) 이므로 올림 나눗셈을 쓴다.
     * 이 값이면 디코딩한 긴 변은 목표 이상, 목표의 2배 미만이라 마지막 축소에서 화질을 잃지 않는다.
     */
    static int subsamplingFor(int longSide, int targetLongSide) {
        int maxDecodedLongSide = targetLongSide * 2 - 1;
        return Math.max(1, (longSide + maxDecodedLongSide - 1) / maxDecodedLongSide);
    }

    /** EXIF Orientation 3(180도), 6(시계 방향 90도), 8(반시계 방향 90도)을 픽셀에 적용한다. 그 밖의 값은 그대로 둔다 */
    static BufferedImage rotate(BufferedImage source, int orientation) {
        if (!isRotationOnly(orientation)) {
            return source;
        }
        int width = source.getWidth();
        int height = source.getHeight();
        boolean swap = orientation != 3;
        int imageType = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage rotated = new BufferedImage(swap ? height : width, swap ? width : height, imageType);

        AffineTransform transform = new AffineTransform();
        switch (orientation) {
            case 3 -> {
                transform.translate(width, height);
                transform.rotate(Math.PI);
            }
            case 6 -> {
                transform.translate(height, 0);
                transform.rotate(Math.PI / 2);
            }
            default -> {
                transform.translate(0, width);
                transform.rotate(-Math.PI / 2);
            }
        }
        Graphics2D g = rotated.createGraphics();
        try {
            g.drawImage(source, transform, null);
        } finally {
            g.dispose();
        }
        return rotated;
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
        try (ImageOutputStream output = new MemoryCacheImageOutputStream(out)) {
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

    /** ImageIO.write(image, "png", out) 도 내부에서 캐시 스트림을 만들므로 메모리 캐시 스트림으로 직접 쓴다 */
    private static byte[] encodePng(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) {
            throw new IOException("PNG writer 가 없습니다.");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream output = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(output);
            writer.write(image);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /**
     * JPEG 의 APP1(Exif) 에서 Orientation(0x0112) 이 1 이 아닌지 본다. 해석하지 못하면 false.
     */
    static boolean hasNonDefaultExifOrientation(byte[] jpeg) {
        return exifOrientation(jpeg) != 1;
    }

    /**
     * JPEG 의 APP1(Exif) 에서 Orientation(0x0112) 값을 읽는다. 없거나 해석하지 못하면 1(기본).
     */
    static int exifOrientation(byte[] jpeg) {
        int offset = 2;
        while (offset + 4 <= jpeg.length && (jpeg[offset] & 0xFF) == 0xFF) {
            int marker = jpeg[offset + 1] & 0xFF;
            if (marker == 0xDA || marker == 0xD9) {
                return 1;
            }
            int segmentLength = ((jpeg[offset + 2] & 0xFF) << 8) | (jpeg[offset + 3] & 0xFF);
            int dataStart = offset + 4;
            if (marker == 0xE1 && asciiAt(jpeg, dataStart, "Exif") && dataStart + 6 + 8 <= jpeg.length) {
                Integer orientation = readOrientation(jpeg, dataStart + 6, Math.min(jpeg.length, offset + 2 + segmentLength));
                return orientation == null || orientation < 1 || orientation > 8 ? 1 : orientation;
            }
            offset += 2 + segmentLength;
        }
        return 1;
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
