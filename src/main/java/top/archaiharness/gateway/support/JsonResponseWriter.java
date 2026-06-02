package top.archaiharness.gateway.support;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * 统一的 JSON 错误响应写入工具。
 *
 * <p>WebFlux 中无 Spring MVC 的 {@code @ExceptionHandler}/{@code ResponseEntity}
 * 便利,提供此工具避免每处错误响应都手写 buffer。
 */
public final class JsonResponseWriter {

    private JsonResponseWriter() {
    }

    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        String json = "{\"code\":" + status.value()
                + ",\"message\":\"" + escape(message) + "\"}";
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
