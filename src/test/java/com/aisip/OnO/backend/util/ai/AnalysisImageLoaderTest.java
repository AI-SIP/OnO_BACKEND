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
        loader = new AnalysisImageLoader(amazonS3Client, BUCKET, 5);
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
            stubObject("doc.pdf", "%PDF-1.7 fake".getBytes(StandardCharsets.US_ASCII));
            stubObject("empty.jpg", new byte[0]);
            byte[] wav = webpBytes();
            System.arraycopy("WAVE".getBytes(StandardCharsets.US_ASCII), 0, wav, 8, 4);
            stubObject("sound.webp", wav);

            for (String key : List.of("doc.pdf", "empty.jpg", "sound.webp")) {
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
        @DisplayName("메타데이터가 20MB 를 넘으면 본문을 읽지 않고 UNSUPPORTED_IMAGE, 스트림은 닫는다")
        void rejectsTooLargeByMetadata() {
            TrackingInputStream stream = stubObject("image/huge.jpg",
                    new ByteArrayInputStream(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
                    AnalysisImageLoader.MAX_OBJECT_BYTES + 1);

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> loader.loadAsDataUrls(List.of(URL_PREFIX + "image/huge.jpg")));

            assertReason(thrown, AnalysisFailureReason.UNSUPPORTED_IMAGE);
            assertThat(stream.closed).isTrue();
        }

        @Test
        @DisplayName("메타데이터에 크기가 없어도 실제로 20MB 를 넘게 읽히면 UNSUPPORTED_IMAGE")
        void rejectsTooLargeByActualBytes() {
            byte[] big = new byte[(int) AnalysisImageLoader.MAX_OBJECT_BYTES + 1];
            big[0] = (byte) 0xFF;
            big[1] = (byte) 0xD8;
            big[2] = (byte) 0xFF;
            TrackingInputStream stream = stubObject("image/huge.jpg", new ByteArrayInputStream(big), null);

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> loader.loadAsDataUrls(List.of(URL_PREFIX + "image/huge.jpg")));

            assertReason(thrown, AnalysisFailureReason.UNSUPPORTED_IMAGE);
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
        @DisplayName("EXIF 회전 정보가 있는 큰 JPEG 는 다시 인코딩하면 회전이 사라지므로 원본을 보낸다")
        void keepsOriginalWhenExifRotated() {
            byte[] rotated = withExifOrientation(encode(drawImage(4000, 3000, BufferedImage.TYPE_INT_RGB), "jpg"), 6);
            stubObject("image/rotated.jpg", rotated);

            assertThat(AnalysisImageLoader.hasNonDefaultExifOrientation(rotated)).isTrue();
            assertThat(bytesOf(loader.loadAsDataUrls(List.of(URL_PREFIX + "image/rotated.jpg")).get(0))).isEqualTo(rotated);
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
}
