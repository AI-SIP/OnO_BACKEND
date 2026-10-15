package com.aisip.OnO.backend.util.ai;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 분석 이미지 로더. S3 는 Mockito 목이라 실제로 나가지 않는다.
 * 테스트 이미지는 BufferedImage 를 코드로 그려 인코딩하고, HEIC/WEBP 는 매직 바이트만 맞춘 바이트 배열을 쓴다.
 */
@DisplayName("분석 이미지 로더")
class AnalysisImageLoaderTest {

    private static final String BUCKET = "test-ono-bucket";
    private static final String URL_PREFIX = "https://" + BUCKET + ".s3.ap-northeast-2.amazonaws.com/";

    private AmazonS3Client amazonS3Client;
    private AnalysisImageLoader loader;

    @BeforeEach
    void setUp() {
        amazonS3Client = mock(AmazonS3Client.class);
        loader = new AnalysisImageLoader(amazonS3Client, BUCKET, 5, AnalysisImageLoader.DEFAULT_MAX_TOTAL_BYTES, AnalysisImageLoader.DEFAULT_MAX_ANSWER_IMAGES);
    }

    // ---- 테스트 데이터 ----

    /** close/abort 여부를 기록하는 스트림 */
    static class TrackingInputStream extends FilterInputStream {
        boolean closed;

        TrackingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private TrackingInputStream stubObject(String key, byte[] bytes) {
        return stubObject(key, new ByteArrayInputStream(bytes), (long) bytes.length);
    }

    private TrackingInputStream stubObject(String key, InputStream source, Long contentLength) {
        TrackingInputStream tracking = new TrackingInputStream(source);
        S3Object object = new S3Object();
        object.setBucketName(BUCKET);
        object.setKey(key);
        ObjectMetadata metadata = new ObjectMetadata();
        if (contentLength != null) {
            metadata.setContentLength(contentLength);
        }
        object.setObjectMetadata(metadata);
        object.setObjectContent(new S3ObjectInputStream(tracking, null));
        when(amazonS3Client.getObject(BUCKET, key)).thenReturn(object);
        return tracking;
    }

    private static BufferedImage drawImage(int width, int height, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D g = image.createGraphics();
        try {
            if (type == BufferedImage.TYPE_INT_ARGB) {
                g.setColor(new Color(255, 255, 255, 0));
            } else {
                g.setColor(Color.WHITE);
            }
            g.fillRect(0, 0, width, height);
            g.setColor(Color.BLACK);
            g.drawString("1 + 1 = ?", Math.max(1, width / 4), Math.max(12, height / 2));
            g.fillRect(0, 0, Math.max(1, width / 10), Math.max(1, height / 10));
        } finally {
            g.dispose();
        }
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            assertThat(ImageIO.write(image, format, out)).as(format + " writer").isTrue();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String mimeOf(String dataUrl) {
        return dataUrl.substring("data:".length(), dataUrl.indexOf(";base64,"));
    }

    private static byte[] bytesOf(String dataUrl) {
        return Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(";base64,") + ";base64,".length()));
    }

    private static BufferedImage decode(byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(image).as("다시 인코딩된 이미지는 디코딩할 수 있어야 한다").isNotNull();
        return image;
    }

    private static byte[] webpBytes() {
        byte[] bytes = new byte[32];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 4);
        bytes[4] = 24;
        System.arraycopy("WEBPVP8 ".getBytes(StandardCharsets.US_ASCII), 0, bytes, 8, 8);
        return bytes;
    }

    private static byte[] heifBytes(String brand) {
        byte[] bytes = new byte[32];
        bytes[3] = 24;
        System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, bytes, 4, 4);
        System.arraycopy(brand.getBytes(StandardCharsets.US_ASCII), 0, bytes, 8, 4);
        return bytes;
    }

    /** Orientation 태그만 든 APP1(Exif) 세그먼트를 끼워 넣는다 */
    private static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        byte[] app1 = new byte[]{
                (byte) 0xFF, (byte) 0xE1, 0x00, 0x22,           // APP1, 길이 34
                'E', 'x', 'i', 'f', 0x00, 0x00,                 // Exif 헤더
                'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,   // TIFF 헤더(빅엔디언), IFD0 오프셋 8
                0x00, 0x01,                                     // 엔트리 1개
                0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, // Orientation, SHORT, count 1
                0x00, (byte) orientation, 0x00, 0x00,           // 값
                0x00, 0x00, 0x00, 0x00                          // 다음 IFD 없음
        };
        // ImageIO 가 만든 JPEG 는 SOI 뒤에 JFIF APP0 이 온다. APP0 은 맨 앞이어야 하므로 그 뒤에 넣는다
        int insertAt = 2;
        if ((jpeg[2] & 0xFF) == 0xFF && (jpeg[3] & 0xFF) == 0xE0) {
            insertAt = 4 + (((jpeg[4] & 0xFF) << 8) | (jpeg[5] & 0xFF));
        }
        byte[] result = new byte[jpeg.length + app1.length];
        System.arraycopy(jpeg, 0, result, 0, insertAt);
        System.arraycopy(app1, 0, result, insertAt, app1.length);
        System.arraycopy(jpeg, insertAt, result, insertAt + app1.length, jpeg.length - insertAt);
        return result;
    }

    private static int brightness(BufferedImage image, int x, int y) {
        int rgb = image.getRGB(x, y);
        return (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
    }

    /** JPEG 의 SOF0 헤더에 적힌 가로세로만 바꾼다. 본문은 작은 그대로라 헤더만 큰 크기를 주장하는 파일이 된다 */
    private static byte[] withJpegDimensions(byte[] jpeg, int width, int height) {
        byte[] patched = jpeg.clone();
        for (int i = 2; i + 8 < patched.length; i++) {
            if ((patched[i] & 0xFF) == 0xFF && (patched[i + 1] & 0xFF) == 0xC0) {
                patched[i + 5] = (byte) (height >> 8);
                patched[i + 6] = (byte) height;
                patched[i + 7] = (byte) (width >> 8);
                patched[i + 8] = (byte) width;
                return patched;
            }
        }
        throw new IllegalStateException("SOF0 를 찾지 못했다");
    }

    private static void assertReason(Throwable thrown, AnalysisFailureReason expected) {
        assertThat(thrown).isInstanceOf(NonRetryableAnalysisException.class);
        assertThat(((NonRetryableAnalysisException) thrown).getReason()).isEqualTo(expected);
    }

    // ---- 테스트 ----

    @Nested
    @DisplayName("URL 검증")
    class UrlValidation {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "https://other-bucket.s3.ap-northeast-2.amazonaws.com/image/a.jpg",
                "https://cdn.test/image.jpg",
                "http://test-ono-bucket.s3.ap-northeast-2.amazonaws.com/image/a.jpg",
                "https://test-ono-bucket.s3.evil.com/x/.amazonaws.com/image/a.jpg",
                "https://test-ono-bucket.s3.ap-northeast-2.amazonaws.com/",
                "https://test-ono-bucket.s3.ap-northeast-2.example.com/image/a.jpg"
        })
        @DisplayName("우리 버킷 S3 URL 이 아니면 S3 를 읽지 않고 INVALID_IMAGE_SOURCE 로 거부한다")
        void rejectsForeignUrl(String url) {
            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> loader.loadAsDataUrls(List.of(url)));

            assertReason(thrown, AnalysisFailureReason.INVALID_IMAGE_SOURCE);
            verifyNoInteractions(amazonS3Client);
        }

        @Test
        @DisplayName("URL 에서 .amazonaws.com/ 뒤를 key 로 읽는다")
        void readsKeyAfterHost() {
            byte[] jpeg = encode(drawImage(100, 80, BufferedImage.TYPE_INT_RGB), "jpg");
            stubObject("image/2026/10/abc.jpg", jpeg);

            loader.loadAsDataUrls(List.of(URL_PREFIX + "image/2026/10/abc.jpg"));

            verify(amazonS3Client).getObject(BUCKET, "image/2026/10/abc.jpg");
        }

        @Test
        @DisplayName("목록 중 하나라도 외부 URL 이면 앞의 이미지도 읽지 않는다")
        void validatesAllBeforeReading() {
            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> loader.loadAsDataUrls(
                    List.of(URL_PREFIX + "image/a.jpg", "https://cdn.test/b.jpg")));

            assertReason(thrown, AnalysisFailureReason.INVALID_IMAGE_SOURCE);
            verifyNoInteractions(amazonS3Client);
        }
    }

    @Nested
    @DisplayName("장수")
    class ImageCount {

        @Test
        @DisplayName("상한(5장)을 넘으면 S3 를 한 번도 읽지 않고 TOO_MANY_IMAGES")
        void rejectsTooManyImages() {
            List<String> urls = Collections.nCopies(6, URL_PREFIX + "image/a.jpg");

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> loader.loadAsDataUrls(urls));

            assertReason(thrown, AnalysisFailureReason.TOO_MANY_IMAGES);
            verifyNoInteractions(amazonS3Client);
        }

        @Test
        @DisplayName("상한과 같은 5장은 통과한다")
        void acceptsExactlyMaxImages() {
            byte[] jpeg = encode(drawImage(50, 50, BufferedImage.TYPE_INT_RGB), "jpg");
            for (int i = 0; i < 5; i++) {
                stubObject("image/" + i + ".jpg", jpeg);
            }
            List<String> urls = List.of(0, 1, 2, 3, 4).stream().map(i -> URL_PREFIX + "image/" + i + ".jpg").toList();

            assertThat(loader.loadAsDataUrls(urls)).hasSize(5);
        }

        @Test
        @DisplayName("16장짜리 요청(운영 재시도 초과 사례)도 S3 를 읽기 전에 거부한다")
        void rejectsProductionSixteenImages() {
            List<String> urls = Collections.nCopies(16, URL_PREFIX + "image/a.jpg");

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> loader.loadAsDataUrls(urls));

            assertReason(thrown, AnalysisFailureReason.TOO_MANY_IMAGES);
            verifyNoInteractions(amazonS3Client);
        }

        @Test
        @DisplayName("빈 목록과 null 은 UNREADABLE_IMAGE")
        void rejectsEmpty() {
            assertReason(org.assertj.core.api.Assertions.catchThrowable(() -> loader.loadAsDataUrls(List.of())),
                    AnalysisFailureReason.UNREADABLE_IMAGE);
            assertReason(org.assertj.core.api.Assertions.catchThrowable(() -> loader.loadAsDataUrls(null)),
                    AnalysisFailureReason.UNREADABLE_IMAGE);
            verifyNoInteractions(amazonS3Client);
        }
    }

    @Nested
    @DisplayName("형식 판별")
    class FormatDetection {

        @Test
        @DisplayName("작은 JPEG 는 원본 그대로 image/jpeg data URL 이 된다")
        void passesSmallJpeg() {
            byte[] jpeg = encode(drawImage(400, 300, BufferedImage.TYPE_INT_RGB), "jpg");
            stubObject("image/a.jpg", jpeg);

            String dataUrl = loader.loadAsDataUrls(List.of(URL_PREFIX + "image/a.jpg")).get(0);

            assertThat(dataUrl).startsWith("data:image/jpeg;base64,");
            assertThat(bytesOf(dataUrl)).isEqualTo(jpeg);
        }

        @Test
        @DisplayName("작은 PNG 는 원본 그대로 image/png")
        void passesSmallPng() {
            byte[] png = encode(drawImage(400, 300, BufferedImage.TYPE_INT_RGB), "png");
            stubObject("image/a.png", png);

            String dataUrl = loader.loadAsDataUrls(List.of(URL_PREFIX + "image/a.png")).get(0);

            assertThat(mimeOf(dataUrl)).isEqualTo("image/png");
            assertThat(bytesOf(dataUrl)).isEqualTo(png);
        }

        @Test
        @DisplayName("확장자가 아니라 내용으로 판별한다(.jpg 이름의 PNG 는 image/png)")
        void detectsByMagicBytesNotExtension() {
            byte[] png = encode(drawImage(100, 100, BufferedImage.TYPE_INT_RGB), "png");
            stubObject("image/disguised.jpg", png);

            String dataUrl = loader.loadAsDataUrls(List.of(URL_PREFIX + "image/disguised.jpg")).get(0);

            assertThat(mimeOf(dataUrl)).isEqualTo("image/png");
        }

        @Test
        @DisplayName("GIF 는 크기와 상관없이 원본 그대로 image/gif")
        void passesGif() {
            byte[] gif = encode(drawImage(3000, 100, BufferedImage.TYPE_INT_RGB), "gif");
            stubObject("image/a.gif", gif);

            String dataUrl = loader.loadAsDataUrls(List.of(URL_PREFIX + "image/a.gif")).get(0);

            assertThat(mimeOf(dataUrl)).isEqualTo("image/gif");
            assertThat(bytesOf(dataUrl)).isEqualTo(gif);
        }

        @Test
        @DisplayName("WEBP 는 원본 그대로 image/webp")
        void passesWebp() {
            byte[] webp = webpBytes();
            stubObject("image/a.webp", webp);

            String dataUrl = loader.loadAsDataUrls(List.of(URL_PREFIX + "image/a.webp")).get(0);

            assertThat(mimeOf(dataUrl)).isEqualTo("image/webp");
            assertThat(bytesOf(dataUrl)).isEqualTo(webp);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"heic", "heix", "hevc", "heim", "heis", "mif1", "msf1"})
        @DisplayName("HEIC/HEIF 는 UNSUPPORTED_IMAGE")
        void rejectsHeif(String brand) {
            stubObject("image/a.heic", heifBytes(brand));

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> loader.loadAsDataUrls(List.of(URL_PREFIX + "image/a.heic")));

            assertReason(thrown, AnalysisFailureReason.UNSUPPORTED_IMAGE);
            assertThat(thrown).hasMessageContaining("HEIC");
        }

        @Test
        @DisplayName("알 수 없는 형식(PDF, 빈 파일, RIFF 지만 WEBP 아님)은 UNSUPPORTED_IMAGE")
        void rejectsUnknownFormats() {
            stubObject("image/doc.pdf", "%PDF-1.7 fake".getBytes(StandardCharsets.US_ASCII));
            stubObject("image/empty.jpg", new byte[0]);
            byte[] wav = webpBytes();
            System.arraycopy("WAVE".getBytes(StandardCharsets.US_ASCII), 0, wav, 8, 4);
            stubObject("image/sound.webp", wav);

            for (String key : List.of("image/doc.pdf", "image/empty.jpg", "image/sound.webp")) {
                Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                        () -> loader.loadAsDataUrls(List.of(URL_PREFIX + key)));
                assertReason(thrown, AnalysisFailureReason.UNSUPPORTED_IMAGE);
            }
        }
    }

    @Nested
    @DisplayName("크기 상한과 S3 오류")
    class SizeAndS3Errors {

        @Test
        @DisplayName("메타데이터가 20MB 를 넘으면 본문을 읽지 않고 IMAGE_TOO_LARGE, 스트림은 닫는다")
        void rejectsTooLargeByMetadata() {
            TrackingInputStream stream = stubObject("image/huge.jpg",
                    new ByteArrayInputStream(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
                    AnalysisImageLoader.MAX_OBJECT_BYTES + 1);

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> loader.loadAsDataUrls(List.of(URL_PREFIX + "image/huge.jpg")));

            assertReason(thrown, AnalysisFailureReason.IMAGE_TOO_LARGE);
            assertThat(stream.closed).isTrue();
        }

        @Test
        @DisplayName("메타데이터에 크기가 없어도 실제로 20MB 를 넘게 읽히면 IMAGE_TOO_LARGE")
        void rejectsTooLargeByActualBytes() {
            byte[] big = new byte[(int) AnalysisImageLoader.MAX_OBJECT_BYTES + 1];
            big[0] = (byte) 0xFF;
            big[1] = (byte) 0xD8;
            big[2] = (byte) 0xFF;
            TrackingInputStream stream = stubObject("image/huge.jpg", new ByteArrayInputStream(big), null);

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> loader.loadAsDataUrls(List.of(URL_PREFIX + "image/huge.jpg")));

            assertReason(thrown, AnalysisFailureReason.IMAGE_TOO_LARGE);
            assertThat(stream.closed).isTrue();
        }

        @Test
        @DisplayName("정확히 20MB 는 허용한다")
        void acceptsExactlyLimit() {
            byte[] limit = new byte[(int) AnalysisImageLoader.MAX_OBJECT_BYTES];
            System.arraycopy(webpBytes(), 0, limit, 0, 16);
            stubObject("image/limit.webp", limit);

            assertThat(loader.loadAsDataUrls(List.of(URL_PREFIX + "image/limit.webp"))).hasSize(1);
        }

        @Test
        @DisplayName("S3 404 는 재시도하지 않는 INVALID_IMAGE_SOURCE")
        void notFoundIsNonRetryable() {
            AmazonS3Exception notFound = new AmazonS3Exception("Not Found");
            notFound.setStatusCode(404);
            notFound.setErrorCode("NoSuchKey");
            when(amazonS3Client.getObject(BUCKET, "image/gone.jpg")).thenThrow(notFound);

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> loader.loadAsDataUrls(List.of(URL_PREFIX + "image/gone.jpg")));

            assertReason(thrown, AnalysisFailureReason.INVALID_IMAGE_SOURCE);
        }

        @Test
        @DisplayName("상태 코드 없이 NoSuchKey 만 와도 INVALID_IMAGE_SOURCE")
        void noSuchKeyErrorCodeIsNonRetryable() {
            AmazonS3Exception notFound = new AmazonS3Exception("The specified key does not exist.");
            notFound.setErrorCode("NoSuchKey");
            when(amazonS3Client.getObject(BUCKET, "image/gone.jpg")).thenThrow(notFound);

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> loader.loadAsDataUrls(List.of(URL_PREFIX + "image/gone.jpg")));

            assertReason(thrown, AnalysisFailureReason.INVALID_IMAGE_SOURCE);
        }

        @Test
        @DisplayName("S3 403(AccessDenied)은 재시도 대상이라 IAM 설정이 깨지면 DLQ 알림까지 간다")
        void accessDeniedIsRetryable() {
            // 없는 key 에도 403 이 올 수 있지만, 서버 IAM 설정이 깨졌을 때도 403 이 온다.
            // 재시도하지 않으면 모든 분석이 알림 없이 조용히 실패하므로 재시도 대상으로 둔다
            AmazonS3Exception denied = new AmazonS3Exception("Access Denied");
            denied.setStatusCode(403);
            denied.setErrorCode("AccessDenied");
            when(amazonS3Client.getObject(BUCKET, "image/gone.jpg")).thenThrow(denied);

            assertThatThrownBy(() -> loader.loadAsDataUrls(List.of(URL_PREFIX + "image/gone.jpg")))
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(NonRetryableAnalysisException.class)
                    .hasCause(denied);
        }

        @Test
        @DisplayName("S3 503 은 재시도 대상 RuntimeException")
        void serviceErrorIsRetryable() {
            AmazonS3Exception slowDown = new AmazonS3Exception("Slow Down");
            slowDown.setStatusCode(503);
            slowDown.setErrorCode("SlowDown");
            when(amazonS3Client.getObject(BUCKET, "image/a.jpg")).thenThrow(slowDown);

            assertThatThrownBy(() -> loader.loadAsDataUrls(List.of(URL_PREFIX + "image/a.jpg")))
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(NonRetryableAnalysisException.class)
                    .hasCause(slowDown);
        }

        @Test
        @DisplayName("S3 연결 오류도 재시도 대상 RuntimeException")
        void clientErrorIsRetryable() {
            when(amazonS3Client.getObject(anyString(), anyString())).thenThrow(new SdkClientException("connection reset"));

            assertThatThrownBy(() -> loader.loadAsDataUrls(List.of(URL_PREFIX + "image/a.jpg")))
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(NonRetryableAnalysisException.class);
        }

        @Test
        @DisplayName("본문을 읽다 IO 오류가 나면 재시도 대상이고 스트림은 닫는다")
        void readErrorIsRetryableAndCloses() {
            InputStream broken = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("connection reset");
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    throw new IOException("connection reset");
                }
            };
            TrackingInputStream stream = stubObject("image/a.jpg", broken, 1000L);

            assertThatThrownBy(() -> loader.loadAsDataUrls(List.of(URL_PREFIX + "image/a.jpg")))
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(NonRetryableAnalysisException.class);
            assertThat(stream.closed).isTrue();
        }

        @Test
        @DisplayName("정상으로 읽은 뒤에도 스트림을 닫는다")
        void closesStreamOnSuccess() {
            TrackingInputStream stream = stubObject("image/a.jpg",
                    encode(drawImage(100, 100, BufferedImage.TYPE_INT_RGB), "jpg"));

            loader.loadAsDataUrls(List.of(URL_PREFIX + "image/a.jpg"));

            assertThat(stream.closed).isTrue();
        }

        @Test
        @DisplayName("형식이 맞지 않아 거부할 때도 스트림은 이미 닫혀 있다")
        void closesStreamOnUnsupported() {
            TrackingInputStream stream = stubObject("image/a.heic", heifBytes("heic"));

            org.assertj.core.api.Assertions.catchThrowable(
                    () -> loader.loadAsDataUrls(List.of(URL_PREFIX + "image/a.heic")));

            assertThat(stream.closed).isTrue();
        }

        @Test
        @DisplayName("앞 이미지에서 실패하면 뒤 이미지는 읽지 않는다")
        void stopsAtFirstFailure() {
            stubObject("image/a.heic", heifBytes("heic"));

            org.assertj.core.api.Assertions.catchThrowable(() -> loader.loadAsDataUrls(
                    List.of(URL_PREFIX + "image/a.heic", URL_PREFIX + "image/b.jpg")));

            verify(amazonS3Client, never()).getObject(BUCKET, "image/b.jpg");
        }
    }

    @Nested
    @DisplayName("리사이즈")
    class Resize {

        @Test
        @DisplayName("긴 변이 2048 을 넘는 JPEG 는 비율을 지켜 긴 변 2048 의 JPEG 로 줄인다")
        void shrinksLargeJpeg() throws IOException {
            byte[] jpeg = encode(drawImage(4000, 3000, BufferedImage.TYPE_INT_RGB), "jpg");
            stubObject("image/big.jpg", jpeg);

            String dataUrl = loader.loadAsDataUrls(List.of(URL_PREFIX + "image/big.jpg")).get(0);

            assertThat(mimeOf(dataUrl)).isEqualTo("image/jpeg");
            BufferedImage resized = decode(bytesOf(dataUrl));
            assertThat(resized.getWidth()).isEqualTo(2048);
            assertThat(resized.getHeight()).isEqualTo(1536);
        }

        @Test
        @DisplayName("세로로 긴 이미지는 높이를 2048 로 맞춘다")
        void shrinksTallImage() throws IOException {
            byte[] jpeg = encode(drawImage(1500, 4500, BufferedImage.TYPE_INT_RGB), "jpg");
            stubObject("image/tall.jpg", jpeg);

            BufferedImage resized = decode(bytesOf(loader.loadAsDataUrls(List.of(URL_PREFIX + "image/tall.jpg")).get(0)));

            assertThat(resized.getHeight()).isEqualTo(2048);
            assertThat(resized.getWidth()).isEqualTo(683);
        }

        @Test
        @DisplayName("아주 큰 이미지(서브샘플링 경로)도 긴 변 2048 로 줄어든다")
        void shrinksHugeImageWithSubsampling() throws IOException {
            byte[] jpeg = encode(drawImage(10000, 1000, BufferedImage.TYPE_INT_RGB), "jpg");
            stubObject("image/huge.jpg", jpeg);

            BufferedImage resized = decode(bytesOf(loader.loadAsDataUrls(List.of(URL_PREFIX + "image/huge.jpg")).get(0)));

            assertThat(resized.getWidth()).isEqualTo(2048);
            assertThat(resized.getHeight()).isBetween(203, 206);
        }

        @Test
        @DisplayName("긴 변이 정확히 2048 이면 줄이지 않고 원본을 보낸다")
        void keepsExactlyLimit() {
            byte[] jpeg = encode(drawImage(2048, 1000, BufferedImage.TYPE_INT_RGB), "jpg");
            stubObject("image/edge.jpg", jpeg);

            assertThat(bytesOf(loader.loadAsDataUrls(List.of(URL_PREFIX + "image/edge.jpg")).get(0))).isEqualTo(jpeg);
        }

        @Test
        @DisplayName("알파가 있는 큰 PNG 는 투명도를 지키려고 PNG 로 다시 인코딩한다")
        void keepsAlphaPngAsPng() throws IOException {
            byte[] png = encode(drawImage(3000, 2000, BufferedImage.TYPE_INT_ARGB), "png");
            stubObject("image/alpha.png", png);

            String dataUrl = loader.loadAsDataUrls(List.of(URL_PREFIX + "image/alpha.png")).get(0);

            assertThat(mimeOf(dataUrl)).isEqualTo("image/png");
            BufferedImage resized = decode(bytesOf(dataUrl));
            assertThat(resized.getColorModel().hasAlpha()).isTrue();
            assertThat(resized.getWidth()).isEqualTo(2048);
            assertThat(resized.getHeight()).isEqualTo(1365);
        }

        @Test
        @DisplayName("알파가 없는 큰 PNG 는 JPEG 로 다시 인코딩한다")
        void convertsOpaquePngToJpeg() throws IOException {
            byte[] png = encode(drawImage(3000, 2000, BufferedImage.TYPE_INT_RGB), "png");
            stubObject("image/opaque.png", png);

            String dataUrl = loader.loadAsDataUrls(List.of(URL_PREFIX + "image/opaque.png")).get(0);

            assertThat(mimeOf(dataUrl)).isEqualTo("image/jpeg");
            assertThat(AnalysisImageLoader.detectFormat(bytesOf(dataUrl))).isEqualTo(AnalysisImageLoader.ImageFormat.JPEG);
            assertThat(decode(bytesOf(dataUrl)).getWidth()).isEqualTo(2048);
        }

        @Test
        @DisplayName("디코딩할 수 없는 JPEG 는 실패시키지 않고 원본을 보낸다")
        void sendsOriginalWhenDecodingFails() {
            byte[] broken = new byte[5000];
            broken[0] = (byte) 0xFF;
            broken[1] = (byte) 0xD8;
            broken[2] = (byte) 0xFF;
            for (int i = 3; i < broken.length; i++) {
                broken[i] = (byte) (i * 31);
            }
            stubObject("image/broken.jpg", broken);

            String dataUrl = loader.loadAsDataUrls(List.of(URL_PREFIX + "image/broken.jpg")).get(0);

            assertThat(mimeOf(dataUrl)).isEqualTo("image/jpeg");
            assertThat(bytesOf(dataUrl)).isEqualTo(broken);
        }

        @Test
        @DisplayName("EXIF Orientation 6 인 큰 JPEG 는 시계 방향으로 돌린 뒤 줄여 가로세로가 바뀐다")
        void rotatesClockwiseThenShrinks() throws IOException {
            byte[] rotated = withExifOrientation(encode(drawImage(4000, 3000, BufferedImage.TYPE_INT_RGB), "jpg"), 6);
            stubObject("image/rotated.jpg", rotated);

            String dataUrl = loader.loadAsDataUrls(List.of(URL_PREFIX + "image/rotated.jpg")).get(0);

            assertThat(mimeOf(dataUrl)).isEqualTo("image/jpeg");
            BufferedImage result = decode(bytesOf(dataUrl));
            assertThat(result.getWidth()).as("4000x3000 을 세운 3000x4000 을 줄인 크기").isEqualTo(1536);
            assertThat(result.getHeight()).isEqualTo(2048);
            // 원본 왼쪽 위의 검은 사각형은 시계 방향 90도 회전 뒤 오른쪽 위로 간다
            assertThat(brightness(result, result.getWidth() - 20, 20)).as("오른쪽 위").isLessThan(64);
            assertThat(brightness(result, 20, 20)).as("왼쪽 위").isGreaterThan(192);
            assertThat(brightness(result, 20, result.getHeight() - 20)).as("왼쪽 아래").isGreaterThan(192);
        }

        @Test
        @DisplayName("EXIF Orientation 8 은 반시계 방향으로 돌린다")
        void rotatesCounterClockwise() throws IOException {
            byte[] rotated = withExifOrientation(encode(drawImage(4000, 3000, BufferedImage.TYPE_INT_RGB), "jpg"), 8);
            stubObject("image/rotated.jpg", rotated);

            BufferedImage result = decode(bytesOf(loader.loadAsDataUrls(List.of(URL_PREFIX + "image/rotated.jpg")).get(0)));

            assertThat(result.getWidth()).isEqualTo(1536);
            assertThat(result.getHeight()).isEqualTo(2048);
            // 왼쪽 위는 반시계 방향 90도 회전 뒤 왼쪽 아래로 간다
            assertThat(brightness(result, 20, result.getHeight() - 20)).as("왼쪽 아래").isLessThan(64);
            assertThat(brightness(result, 20, 20)).as("왼쪽 위").isGreaterThan(192);
        }

        @Test
        @DisplayName("EXIF Orientation 3 은 180도 돌리고 가로세로는 그대로다")
        void rotatesHalfTurn() throws IOException {
            byte[] rotated = withExifOrientation(encode(drawImage(4000, 3000, BufferedImage.TYPE_INT_RGB), "jpg"), 3);
            stubObject("image/rotated.jpg", rotated);

            BufferedImage result = decode(bytesOf(loader.loadAsDataUrls(List.of(URL_PREFIX + "image/rotated.jpg")).get(0)));

            assertThat(result.getWidth()).isEqualTo(2048);
            assertThat(result.getHeight()).isEqualTo(1536);
            assertThat(brightness(result, result.getWidth() - 20, result.getHeight() - 20)).as("오른쪽 아래").isLessThan(64);
            assertThat(brightness(result, 20, 20)).as("왼쪽 위").isGreaterThan(192);
        }

        @Test
        @DisplayName("회전 정보가 있어도 긴 변이 2048 이하이면 다시 인코딩하지 않고 원본을 보낸다")
        void keepsSmallRotatedOriginal() {
            byte[] rotated = withExifOrientation(encode(drawImage(1600, 1200, BufferedImage.TYPE_INT_RGB), "jpg"), 6);
            stubObject("image/small-rotated.jpg", rotated);

            assertThat(bytesOf(loader.loadAsDataUrls(List.of(URL_PREFIX + "image/small-rotated.jpg")).get(0))).isEqualTo(rotated);
        }

        @ParameterizedTest(name = "Orientation {0}")
        @ValueSource(ints = {2, 4, 5, 7})
        @DisplayName("반전이 섞인 Orientation(2/4/5/7)은 큰 사진이어도 원본을 보낸다")
        void keepsMirroredOriginal(int orientation) {
            byte[] mirrored = withExifOrientation(encode(drawImage(3000, 2000, BufferedImage.TYPE_INT_RGB), "jpg"), orientation);
            stubObject("image/mirrored.jpg", mirrored);

            assertThat(AnalysisImageLoader.exifOrientation(mirrored)).isEqualTo(orientation);
            assertThat(bytesOf(loader.loadAsDataUrls(List.of(URL_PREFIX + "image/mirrored.jpg")).get(0))).isEqualTo(mirrored);
        }

        @Test
        @DisplayName("EXIF 회전이 기본값(1)이면 그대로 줄인다")
        void shrinksWhenExifOrientationIsDefault() throws IOException {
            byte[] upright = withExifOrientation(encode(drawImage(4000, 3000, BufferedImage.TYPE_INT_RGB), "jpg"), 1);
            stubObject("image/upright.jpg", upright);

            assertThat(AnalysisImageLoader.hasNonDefaultExifOrientation(upright)).isFalse();
            assertThat(decode(bytesOf(loader.loadAsDataUrls(List.of(URL_PREFIX + "image/upright.jpg")).get(0))).getWidth())
                    .isEqualTo(2048);
        }
    }

    @Nested
    @DisplayName("업로드 경로 key 제한")
    class KeyRestriction {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "config/application-prod.yml",
                "backup/db.sql",
                "IMAGE/2026/10/15/a.jpg",
                "images/2026/10/15/a.jpg",
                "image/../config/application-prod.yml",
                "image/2026/10/15/..jpg",
                "image/2026/10/15/a b.jpg",
                "image/2026/10/15/a.jpg?versionId=1",
                "image/2026/10/15/%2e%2e/a.jpg",
                "image/2026/10/15/한글.jpg",
                "image/2026/10/15/a\\b.jpg",
                "2024-08-01T12:34:56/memo_a.jpg",
                "2024-08-01T12:34:56/problemImage_../../x.jpg",
                "2024-08-01T12:34:56/problemImage_a/b.jpg"
        })
        @DisplayName("업로드 경로가 아니거나, .. 이 있거나, 허용하지 않는 문자가 있으면 S3 를 읽지 않고 INVALID_IMAGE_SOURCE")
        void rejectsKeyOutsideUploadPath(String key) {
            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> loader.loadAsDataUrls(List.of(URL_PREFIX + key)));

            assertReason(thrown, AnalysisFailureReason.INVALID_IMAGE_SOURCE);
            verifyNoInteractions(amazonS3Client);
        }

        @Test
        @DisplayName("지금 업로드 형식(image/yyyy/MM/dd/UUID.확장자)은 허용한다")
        void acceptsCurrentUploadKey() {
            String key = "image/2026/10/15/3f2b8c1e-4d5a-4b6c-9e7f-0a1b2c3d4e5f.jpg";
            stubObject(key, encode(drawImage(100, 80, BufferedImage.TYPE_INT_RGB), "jpg"));

            assertThat(loader.loadAsDataUrls(List.of(URL_PREFIX + key))).hasSize(1);
            verify(amazonS3Client).getObject(BUCKET, key);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "2024-08-01T12:34:56.123456/problemImage_IMG_0001.jpg",
                "2024-12-31T09:05/problemImage_사진 1.png",
                "2025-02-28T23:59:59.1/answerImage_scan.jpeg"
        })
        @DisplayName("2025-03 이전 서버 업로드 형식(createdAt/종류_원본파일명)도 허용한다")
        void acceptsLegacyUploadKey(String key) {
            stubObject(key, encode(drawImage(100, 80, BufferedImage.TYPE_INT_RGB), "jpg"));

            assertThat(loader.loadAsDataUrls(List.of(URL_PREFIX + key))).hasSize(1);
            verify(amazonS3Client).getObject(BUCKET, key);
        }

        @Test
        @DisplayName("목록 중 하나라도 허용하지 않는 key 면 앞의 이미지도 읽지 않는다")
        void validatesAllKeysBeforeReading() {
            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> loader.loadAsDataUrls(
                    List.of(URL_PREFIX + "image/2026/10/15/a.jpg", URL_PREFIX + "config/secret.yml")));

            assertReason(thrown, AnalysisFailureReason.INVALID_IMAGE_SOURCE);
            verifyNoInteractions(amazonS3Client);
        }
    }

    @Nested
    @DisplayName("디코딩 메모리")
    class DecodeMemory {

        @Test
        @DisplayName("서브샘플링 값은 디코딩한 긴 변이 목표의 2배 미만이 되게 올림으로 정한다")
        void subsamplingKeepsDecodedLongSideBelowTwiceTarget() {
            // 예전 식(longSide / (target * 2))은 4096~8191px 에서 1 이라 원본 해상도로 디코딩했다
            assertThat(AnalysisImageLoader.subsamplingFor(5000, 2048)).isEqualTo(2);
            assertThat(AnalysisImageLoader.subsamplingFor(8000, 2048)).isEqualTo(2);
            assertThat(AnalysisImageLoader.subsamplingFor(8191, 2048)).isEqualTo(3);
            assertThat(AnalysisImageLoader.subsamplingFor(4096, 2048)).isEqualTo(2);
            assertThat(AnalysisImageLoader.subsamplingFor(4095, 2048)).isEqualTo(1);
            assertThat(AnalysisImageLoader.subsamplingFor(2049, 2048)).isEqualTo(1);

            for (int longSide = 2049; longSide <= 40_000; longSide++) {
                int s = AnalysisImageLoader.subsamplingFor(longSide, 2048);
                int decoded = (longSide + s - 1) / s;
                assertThat(decoded).as("longSide=%d, s=%d", longSide, s).isLessThan(4096).isGreaterThanOrEqualTo(2048);
            }
        }

        @Test
        @DisplayName("긴 변 5000px 는 절반(2500px)으로 서브샘플링해 디코딩한다")
        void decodesFiveThousandWithSubsampling() throws IOException {
            byte[] jpeg = encode(drawImage(5000, 500, BufferedImage.TYPE_INT_RGB), "jpg");

            BufferedImage decoded = AnalysisImageLoader.decodeForLongSide(jpeg, 2048);

            assertThat(decoded.getWidth()).isEqualTo(2500);
            assertThat(decoded.getHeight()).isEqualTo(250);
        }

        @Test
        @DisplayName("긴 변 8000px 는 4000px 로 서브샘플링해 디코딩하고, 최종 결과는 2048 로 줄어든다")
        void decodesEightThousandWithSubsampling() throws IOException {
            byte[] jpeg = encode(drawImage(800, 8000, BufferedImage.TYPE_INT_RGB), "jpg");

            BufferedImage decoded = AnalysisImageLoader.decodeForLongSide(jpeg, 2048);
            assertThat(decoded.getHeight()).isEqualTo(4000);
            assertThat(decoded.getWidth()).isEqualTo(400);

            stubObject("image/tall8000.jpg", jpeg);
            BufferedImage sent = decode(bytesOf(loader.loadAsDataUrls(List.of(URL_PREFIX + "image/tall8000.jpg")).get(0)));
            assertThat(sent.getHeight()).isEqualTo(2048);
            assertThat(sent.getWidth()).isEqualTo(205);
        }

        @Test
        @DisplayName("헤더의 가로x세로가 4,000만 px 를 넘으면 디코딩하지 않고 IMAGE_TOO_LARGE")
        void rejectsTooManyPixelsWithoutDecoding() {
            byte[] bomb = withJpegDimensions(encode(drawImage(64, 64, BufferedImage.TYPE_INT_RGB), "jpg"), 8000, 6000);
            stubObject("image/bomb.jpg", bomb);

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> loader.loadAsDataUrls(List.of(URL_PREFIX + "image/bomb.jpg")));

            assertReason(thrown, AnalysisFailureReason.IMAGE_TOO_LARGE);
            assertThatThrownBy(() -> AnalysisImageLoader.decodeForLongSide(bomb, 2048))
                    .isInstanceOf(NonRetryableAnalysisException.class);
        }

        @Test
        @DisplayName("PNG 도 헤더 픽셀 수로 거부한다")
        void rejectsTooManyPixelsPng() {
            byte[] png = encode(drawImage(64, 64, BufferedImage.TYPE_INT_RGB), "png");
            // IHDR 은 8바이트 시그니처 + 길이(4) + "IHDR"(4) 뒤에 가로(4), 세로(4)
            byte[] patched = png.clone();
            java.nio.ByteBuffer.wrap(patched, 16, 8).putInt(10_000).putInt(5_000);
            stubObject("image/bomb.png", patched);

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> loader.loadAsDataUrls(List.of(URL_PREFIX + "image/bomb.png")));

            assertReason(thrown, AnalysisFailureReason.IMAGE_TOO_LARGE);
        }

        @Test
        @DisplayName("정확히 4,000만 px 는 픽셀 상한으로 거부하지 않는다")
        void acceptsExactlyPixelLimit() {
            byte[] edge = withJpegDimensions(encode(drawImage(64, 64, BufferedImage.TYPE_INT_RGB), "jpg"), 8000, 5000);
            stubObject("image/edge.jpg", edge);

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> loader.loadAsDataUrls(List.of(URL_PREFIX + "image/edge.jpg")));

            assertThat(thrown).as("본문이 깨져 있어도 줄이기를 포기하고 원본을 보낼 뿐 IMAGE_TOO_LARGE 는 아니다").isNull();
        }
    }

    @Nested
    @DisplayName("요청 총용량")
    class TotalSize {

        @Test
        @DisplayName("기본 총용량 상한은 30MB 다")
        void defaultTotalLimitIsThirtyMegabytes() {
            assertThat(AnalysisImageLoader.DEFAULT_MAX_TOTAL_BYTES).isEqualTo(30L * 1024 * 1024);
        }

        @Test
        @DisplayName("줄이지 않고 보내는 WEBP 두 장(16MB+16MB)이 30MB 를 넘으면 IMAGE_TOO_LARGE")
        void rejectsTotalOverDefaultLimit() {
            byte[] big = new byte[16 * 1024 * 1024];
            System.arraycopy(webpBytes(), 0, big, 0, 16);
            stubObject("image/1.webp", big);
            stubObject("image/2.webp", big);

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> loader.loadAsDataUrls(
                    List.of(URL_PREFIX + "image/1.webp", URL_PREFIX + "image/2.webp")));

            assertReason(thrown, AnalysisFailureReason.IMAGE_TOO_LARGE);
        }

        @Test
        @DisplayName("합계는 보낼 바이트로 센다. 상한과 같으면 통과하고 넘으면 거부한다")
        void countsSentBytesAgainstConfiguredLimit() {
            byte[] webp = webpBytes();
            AnalysisImageLoader exact = new AnalysisImageLoader(amazonS3Client, BUCKET, 5, webp.length * 2L, AnalysisImageLoader.DEFAULT_MAX_ANSWER_IMAGES);

            stubObject("image/1.webp", webp);
            stubObject("image/2.webp", webp);
            assertThat(exact.loadAsDataUrls(List.of(URL_PREFIX + "image/1.webp", URL_PREFIX + "image/2.webp"))).hasSize(2);

            // S3 스트림은 한 번 읽으면 끝나므로 다시 스텁한다
            stubObject("image/1.webp", webp);
            stubObject("image/2.webp", webp);
            stubObject("image/3.webp", webp);
            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> exact.loadAsDataUrls(List.of(
                    URL_PREFIX + "image/1.webp", URL_PREFIX + "image/2.webp", URL_PREFIX + "image/3.webp")));
            assertReason(thrown, AnalysisFailureReason.IMAGE_TOO_LARGE);
        }

        @Test
        @DisplayName("크게 찍힌 JPEG 는 줄인 뒤의 크기로 세므로 원본이 상한을 넘어도 통과한다")
        void resizedImagesCountAfterShrinking() {
            byte[] jpeg = encode(drawImage(4000, 3000, BufferedImage.TYPE_INT_RGB), "jpg");
            stubObject("image/big.jpg", jpeg);
            AnalysisImageLoader tight = new AnalysisImageLoader(amazonS3Client, BUCKET, 5, jpeg.length - 1L, AnalysisImageLoader.DEFAULT_MAX_ANSWER_IMAGES);

            assertThat(tight.loadAsDataUrls(List.of(URL_PREFIX + "image/big.jpg"))).hasSize(1);
        }
    }

    @Nested
    @DisplayName("해설 이미지(2단계)")
    class AnswerImages {

        private String url(String key) {
            return URL_PREFIX + key;
        }

        private byte[] webpOfSize(int size) {
            byte[] bytes = new byte[size];
            System.arraycopy(webpBytes(), 0, bytes, 0, 16);
            return bytes;
        }

        @Test
        @DisplayName("문제 이미지와 해설 이미지를 나눠 담고, 각자 넘긴 순서를 지킨다")
        void separatesProblemAndAnswerImages() {
            stubObject("image/p1.webp", webpOfSize(40));
            stubObject("image/p2.webp", webpOfSize(41));
            stubObject("image/a1.webp", webpOfSize(42));

            AnalysisImageLoader.LoadedImages loaded = loader.loadForAnalysis(
                    List.of(url("image/p1.webp"), url("image/p2.webp")), List.of(url("image/a1.webp")));

            assertThat(loaded.problemImages()).hasSize(2);
            assertThat(bytesOf(loaded.problemImages().get(0))).hasSize(40);
            assertThat(bytesOf(loaded.problemImages().get(1))).hasSize(41);
            assertThat(loaded.answerImages()).hasSize(1);
            assertThat(bytesOf(loaded.answerImages().get(0))).hasSize(42);
            assertThat(loaded.answerImages().get(0)).startsWith("data:image/webp;base64,");
        }

        @Test
        @DisplayName("해설 이미지가 없거나 null 이어도 문제 이미지만으로 동작한다")
        void worksWithoutAnswerImages() {
            stubObject("image/p1.webp", webpBytes());
            assertThat(loader.loadForAnalysis(List.of(url("image/p1.webp")), null).answerImages()).isEmpty();

            stubObject("image/p1.webp", webpBytes());
            assertThat(loader.loadForAnalysis(List.of(url("image/p1.webp")), List.of()).answerImages()).isEmpty();
        }

        @Test
        @DisplayName("loadAsDataUrls 는 문제 이미지만 읽는 예전 동작 그대로다")
        void loadAsDataUrlsReadsOnlyProblemImages() {
            stubObject("image/p1.webp", webpBytes());

            assertThat(loader.loadAsDataUrls(List.of(url("image/p1.webp")))).hasSize(1);
        }

        @Test
        @DisplayName("해설 이미지가 상한(기본 3장)을 넘으면 실패시키지 않고 앞 3장만 쓴다. 4번째는 S3 에서 읽지도 않는다")
        void usesFirstThreeAnswerImages() {
            stubObject("image/p1.webp", webpBytes());
            for (int i = 1; i <= 4; i++) {
                stubObject("image/a" + i + ".webp", webpOfSize(32 + i));
            }

            AnalysisImageLoader.LoadedImages loaded = loader.loadForAnalysis(List.of(url("image/p1.webp")),
                    List.of(url("image/a1.webp"), url("image/a2.webp"), url("image/a3.webp"), url("image/a4.webp")));

            assertThat(AnalysisImageLoader.DEFAULT_MAX_ANSWER_IMAGES).isEqualTo(3);
            assertThat(loaded.answerImages()).hasSize(3);
            assertThat(loaded.answerImages().stream().map(dataUrl -> bytesOf(dataUrl).length).toList())
                    .containsExactly(33, 34, 35);
            verify(amazonS3Client, never()).getObject(BUCKET, "image/a4.webp");
        }

        @Test
        @DisplayName("해설 이미지 상한은 설정값을 따른다")
        void answerLimitIsConfigurable() {
            AnalysisImageLoader single = new AnalysisImageLoader(amazonS3Client, BUCKET, 5,
                    AnalysisImageLoader.DEFAULT_MAX_TOTAL_BYTES, 1);
            stubObject("image/p1.webp", webpBytes());
            stubObject("image/a1.webp", webpBytes());

            assertThat(single.loadForAnalysis(List.of(url("image/p1.webp")),
                    List.of(url("image/a1.webp"), url("image/a2.webp"))).answerImages()).hasSize(1);
            verify(amazonS3Client, never()).getObject(BUCKET, "image/a2.webp");
        }

        @Test
        @DisplayName("해설 이미지 장수는 문제 이미지 상한(5장)에 세지 않는다. 문제 5장 + 해설 3장은 통과한다")
        void answerImagesDoNotCountTowardProblemLimit() {
            List<String> problems = new java.util.ArrayList<>();
            for (int i = 0; i < 5; i++) {
                stubObject("image/p" + i + ".webp", webpBytes());
                problems.add(url("image/p" + i + ".webp"));
            }
            List<String> answers = new java.util.ArrayList<>();
            for (int i = 0; i < 3; i++) {
                stubObject("image/a" + i + ".webp", webpBytes());
                answers.add(url("image/a" + i + ".webp"));
            }

            AnalysisImageLoader.LoadedImages loaded = loader.loadForAnalysis(problems, answers);

            assertThat(loaded.problemImages()).hasSize(5);
            assertThat(loaded.answerImages()).hasSize(3);
        }

        @Test
        @DisplayName("문제 이미지가 상한을 넘으면 해설 이미지가 있어도 S3 를 읽기 전에 TOO_MANY_IMAGES")
        void problemLimitStillApplies() {
            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> loader.loadForAnalysis(
                    Collections.nCopies(6, url("image/p.webp")), List.of(url("image/a.webp"))));

            assertReason(thrown, AnalysisFailureReason.TOO_MANY_IMAGES);
            verifyNoInteractions(amazonS3Client);
        }

        @Test
        @DisplayName("해설 이미지만 있고 문제 이미지가 없으면 UNREADABLE_IMAGE 이고 S3 를 읽지 않는다")
        void answerOnlyIsUnreadable() {
            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> loader.loadForAnalysis(List.of(), List.of(url("image/a.webp"))));

            assertReason(thrown, AnalysisFailureReason.UNREADABLE_IMAGE);
            verifyNoInteractions(amazonS3Client);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "https://evil.example.com/image/a.webp",
                "https://test-ono-bucket.s3.ap-northeast-2.amazonaws.com/private/secret.webp",
                "https://test-ono-bucket.s3.ap-northeast-2.amazonaws.com/image/../private/a.webp"
        })
        @DisplayName("해설 이미지에도 key 제한을 그대로 적용한다. 걸린 해설은 S3 를 읽지 않고 빼며, 분석은 계속한다")
        void appliesKeyRestrictionToAnswerImages(String answerUrl) {
            stubObject("image/p1.webp", webpBytes());

            AnalysisImageLoader.LoadedImages loaded =
                    loader.loadForAnalysis(List.of(url("image/p1.webp")), List.of(answerUrl));

            assertThat(loaded.problemImages()).hasSize(1);
            assertThat(loaded.answerImages()).isEmpty();
            verify(amazonS3Client, never()).getObject(BUCKET, "private/secret.webp");
            verify(amazonS3Client, never()).getObject(org.mockito.ArgumentMatchers.eq(BUCKET),
                    org.mockito.ArgumentMatchers.contains("private"));
        }

        @Test
        @DisplayName("해설 이미지가 HEIC 처럼 지원하지 않는 형식이면 그 장만 빼고 다른 해설은 보낸다")
        void skipsUnsupportedAnswerImage() {
            stubObject("image/p1.webp", webpBytes());
            stubObject("image/a1.heic", heifBytes("heic"));
            stubObject("image/a2.webp", webpOfSize(50));

            AnalysisImageLoader.LoadedImages loaded = loader.loadForAnalysis(List.of(url("image/p1.webp")),
                    List.of(url("image/a1.heic"), url("image/a2.webp")));

            assertThat(loaded.answerImages()).hasSize(1);
            assertThat(bytesOf(loaded.answerImages().get(0))).hasSize(50);
        }

        @Test
        @DisplayName("같은 HEIC 가 문제 이미지면 지금처럼 UNSUPPORTED_IMAGE 로 실패한다")
        void unsupportedProblemImageStillFails() {
            stubObject("image/p1.heic", heifBytes("heic"));

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> loader.loadForAnalysis(
                    List.of(url("image/p1.heic")), List.of()));

            assertReason(thrown, AnalysisFailureReason.UNSUPPORTED_IMAGE);
        }

        @Test
        @DisplayName("해설 이미지가 S3 에 없으면(404) 그 장만 뺀다")
        void skipsMissingAnswerImage() {
            stubObject("image/p1.webp", webpBytes());
            AmazonS3Exception notFound = new AmazonS3Exception("Not Found");
            notFound.setStatusCode(404);
            notFound.setErrorCode("NoSuchKey");
            when(amazonS3Client.getObject(BUCKET, "image/gone.webp")).thenThrow(notFound);

            assertThat(loader.loadForAnalysis(List.of(url("image/p1.webp")), List.of(url("image/gone.webp")))
                    .answerImages()).isEmpty();
        }

        @Test
        @DisplayName("해설 이미지의 S3 일시 오류는 문제 이미지와 같이 재시도 대상으로 올린다")
        void answerImageTransientErrorIsRetryable() {
            stubObject("image/p1.webp", webpBytes());
            AmazonS3Exception serverError = new AmazonS3Exception("Internal Error");
            serverError.setStatusCode(500);
            when(amazonS3Client.getObject(BUCKET, "image/a1.webp")).thenThrow(serverError);

            assertThatThrownBy(() -> loader.loadForAnalysis(List.of(url("image/p1.webp")), List.of(url("image/a1.webp"))))
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(NonRetryableAnalysisException.class);
        }

        @Test
        @DisplayName("총용량 상한은 해설 이미지까지 합쳐 센다. 넘게 만드는 해설은 빼고, 남은 용량에 맞는 다음 해설은 넣는다")
        void totalLimitIncludesAnswerImages() {
            // 상한 110바이트: 문제 40 + 해설 50 = 90 은 통과, 여기에 해설 30 을 더하면 120 이라 뺀다, 해설 16 은 106 이라 넣는다
            AnalysisImageLoader limited = new AnalysisImageLoader(amazonS3Client, BUCKET, 5, 110L, 5);
            stubObject("image/p1.webp", webpOfSize(40));
            stubObject("image/a1.webp", webpOfSize(50));
            stubObject("image/a2.webp", webpOfSize(30));
            stubObject("image/a3.webp", webpOfSize(16));

            AnalysisImageLoader.LoadedImages loaded = limited.loadForAnalysis(List.of(url("image/p1.webp")),
                    List.of(url("image/a1.webp"), url("image/a2.webp"), url("image/a3.webp")));

            assertThat(loaded.problemImages()).hasSize(1);
            assertThat(loaded.answerImages().stream().map(dataUrl -> bytesOf(dataUrl).length).toList())
                    .as("합계가 상한을 넘지 않게 담는다")
                    .containsExactly(50, 16);
            int total = 40 + loaded.answerImages().stream().mapToInt(dataUrl -> bytesOf(dataUrl).length).sum();
            assertThat(total).isEqualTo(106).isLessThanOrEqualTo(110);
        }

        @Test
        @DisplayName("문제 이미지만으로 총용량을 넘으면 해설이 있어도 지금처럼 IMAGE_TOO_LARGE 로 실패한다")
        void problemImagesOverTotalStillFail() {
            AnalysisImageLoader limited = new AnalysisImageLoader(amazonS3Client, BUCKET, 5, 50L, 3);
            stubObject("image/p1.webp", webpOfSize(40));
            stubObject("image/p2.webp", webpOfSize(40));

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> limited.loadForAnalysis(
                    List.of(url("image/p1.webp"), url("image/p2.webp")), List.of(url("image/a1.webp"))));

            assertReason(thrown, AnalysisFailureReason.IMAGE_TOO_LARGE);
            verify(amazonS3Client, never()).getObject(BUCKET, "image/a1.webp");
        }

        @Test
        @DisplayName("기본 총용량 30MB 를 해설 이미지가 넘기게 하면 그 해설만 뺀다(문제 16MB + 해설 16MB)")
        void defaultTotalLimitIncludesAnswerImages() {
            stubObject("image/p1.webp", webpOfSize(16 * 1024 * 1024));
            stubObject("image/a1.webp", webpOfSize(16 * 1024 * 1024));

            AnalysisImageLoader.LoadedImages loaded = loader.loadForAnalysis(
                    List.of(url("image/p1.webp")), List.of(url("image/a1.webp")));

            assertThat(loaded.problemImages()).hasSize(1);
            assertThat(loaded.answerImages()).isEmpty();
        }
    }
}
