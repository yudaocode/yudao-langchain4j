package dev.langchain4j.model.scoring.onnx;

import ai.onnxruntime.OrtSession;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.data.Percentage.withPercentage;

class OnnxScoringModelIT {

    @TempDir
    private static Path tempDir;

    private static OnnxScoringModel model;

    private static Path modelPath;
    private static Path tokenizerPath;

    private static final String FIXTURE_BASE =
            "https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/a09144355adeed5f58c8ed011d209bf8ee5a1fec/";

    @BeforeAll
    static void initModel() throws IOException, NoSuchAlgorithmException {
        modelPath = fixture("onnx/model_quantized.onnx", "e9d8ebf845c413e981c175bfe49a3bfa9b3dcce2a3ba54875ee5df5a58639fbe");
        tokenizerPath = fixture("tokenizer.json", "d241a60d5e8f04cc1b2b3e9ef7a4921b27bf526d9f6050ab90f9267a1f9e5c66");
        model = new OnnxScoringModel(modelPath.toString(), new OrtSession.SessionOptions(), tokenizerPath.toString(), 512, false);
    }

    @AfterAll
    static void closeModel() {
        if (model != null) {
            model.close();
        }
    }

    private static Path fixture(String path, String expectedSha256) throws IOException, NoSuchAlgorithmException {
        Path file = Paths.get(path).getFileName();
        String localDirectory = System.getProperty("langchain4j.test.scoring-fixture-dir");
        Path fixture = localDirectory == null ? tempDir.resolve(file) : Paths.get(localDirectory).resolve(file);
        if (localDirectory == null) {
            try (InputStream in = new URL(FIXTURE_BASE + path + "?download=true").openStream()) {
                Files.copy(in, fixture, REPLACE_EXISTING);
            }
        }
        byte[] checksum = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(fixture));
        assertThat(String.format("%064x", new BigInteger(1, checksum))).as(path).isEqualTo(expectedSha256);
        return fixture;
    }

    private OnnxScoringModel createModel() {
        return new OnnxScoringModel(modelPath.toString(), new OrtSession.SessionOptions(), tokenizerPath.toString(), 512, false);
    }

    @Test
    void should_score_multiple_segments_with_all_parameters() {
        List<TextSegment> segments = new ArrayList<>();
        segments.add(TextSegment.from("Berlin has a population of 3,520,031 registered inhabitants in an area of 891.82 square kilometers."));
        segments.add(TextSegment.from("New York City is famous for the Metropolitan Museum of Art."));

        String query = "How many people live in Berlin?";

        // when
        Response<List<Double>> response = model.scoreAll(segments, query);

        // then
        List<Double> scores = response.content();
        assertThat(scores).hasSize(2);

        // python output results on https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2: [ 8.663132667541504, -11.245542526245117 ]
        assertThat(scores.get(0)).isCloseTo(8.663132667541504, withPercentage(0.1));
        assertThat(scores.get(1)).isCloseTo(-11.245542526245117, withPercentage(0.1));

        assertThat(response.tokenUsage().totalTokenCount()).isGreaterThan(0);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_implement_autocloseable() {
        assertThat(model).isInstanceOf(AutoCloseable.class);
    }

    @Test
    void should_close_without_exception() {
        OnnxScoringModel closeable = createModel();
        assertThatNoException().isThrownBy(closeable::close);
    }

    @Test
    void should_be_usable_with_try_with_resources() {
        List<TextSegment> segments = new ArrayList<>();
        segments.add(TextSegment.from("Berlin has a population of 3,520,031 registered inhabitants in an area of 891.82 square kilometers."));

        try (OnnxScoringModel m = createModel()) {
            Response<List<Double>> response = m.scoreAll(segments, "How many people live in Berlin?");

            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0)).isCloseTo(8.663132667541504, withPercentage(1));
        }
    }

    @Test
    void should_allow_calling_close_multiple_times() {
        OnnxScoringModel closeable = createModel();
        assertThatNoException().isThrownBy(() -> {
            closeable.close();
            closeable.close();
        });
    }
}