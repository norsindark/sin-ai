package com.sinai.ai.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    public RestClient whisperRestClient(RestClient.Builder builder, AiProperties properties) {
        return build(builder, properties.whisper().baseUrl(), properties.whisper().timeout());
    }

    @Bean
    public RestClient ollamaRestClient(RestClient.Builder builder, AiProperties properties) {
        return build(builder, properties.ollama().baseUrl(), properties.ollama().timeout());
    }

    private RestClient build(RestClient.Builder builder, String baseUrl, Duration readTimeout) {
        var httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
