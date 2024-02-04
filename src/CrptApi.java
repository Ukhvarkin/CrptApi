import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final Semaphore semaphore;
    private final long requestIntervalMillis;
    private volatile long lastRequestTime;

    private final ObjectMapper objectMapper;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.semaphore = new Semaphore(requestLimit);
        this.requestIntervalMillis = timeUnit.toMillis(1);
        this.lastRequestTime = System.currentTimeMillis();
        this.objectMapper = new ObjectMapper();
    }

    public void create(Object document, String signature) {
        try {
            semaphore.acquire();

            long currentTime = System.currentTimeMillis();
            long timeDiff = currentTime - lastRequestTime;
            if (timeDiff > requestIntervalMillis) {
                semaphore.drainPermits();
                lastRequestTime = currentTime;
            }
            String url = "https://ismp.crpt.ru/api/v3/lk/documents/create";
            String requestBody = objectMapper.writeValueAsString(document);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpClient httpClient = HttpClient.newHttpClient();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int responseCode = response.statusCode();

            System.out.println("Документ создан: " + document + ", Signature: " + signature);
        } catch (JsonProcessingException e) {
            System.out.println("Произошла ошибка при сериализации объекта в JSON: " + e.getMessage());
        } catch (IOException | URISyntaxException | InterruptedException e) {
            System.out.println("Произошла ошибка при создании документа: " + e.getMessage());
        } finally {
            semaphore.release();
        }
    }

    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5);

        new Thread(() -> api.create("Doc 1", "Signature 1")).start();
        new Thread(() -> api.create("Doc 2", "Signature 2")).start();
        new Thread(() -> api.create("Doc 3", "Signature 3")).start();
        new Thread(() -> api.create("Doc 4", "Signature 4")).start();
        new Thread(() -> api.create("Doc 5", "Signature 5")).start();
    }
}
