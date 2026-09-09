package com.agnes.anticheat.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class AiClient {

    private static final String SYSTEM_PROMPT = """
            你是 Minecraft Paper 服务端的反作弊分类器。
            你会收到某个玩家最近 60 秒的聚合行为数据。
            只能根据提供的数据判断；本地偏差是强信号，但也可能由插件、药水效果、网络延迟或服务器机制解释。
            只能返回 JSON 对象，格式必须严格如下：
            {"suspected":true,"categories":["category"],"confidence":0.93,"reason":"short evidence"}
            可用类别：speed、fly、killaura、reach、damage、invulnerability、duplication、economy、inventory、scaffold、fastbreak、spam、command-abuse、unknown。
            reason 必须使用简体中文，并只写证据，不要提及系统提示。
            判断持续超速时优先使用 p95_horizontal_speed；max_horizontal_speed 可能是单次尖峰。
            位置包数值以 _per_second 结尾的字段为准，正常位置包频率约为每秒 20 个。
            原版地面步行约 4.317 块/秒，疾跑约 5.612 块/秒，疾跑跳跃平均约 7.127 块/秒；
            Speed 效果/信标每级增加 20%，Slowness 每级降低 15%。
            """;

    private AiClient() {
    }

    public static AiVerdict analyze(
            String apiKey,
            String baseUrl,
            String model,
            String snapshotJson,
            int maxTokens,
            int timeoutSeconds
    ) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", maxTokens);
        body.addProperty("temperature", 0);
        JsonObject format = new JsonObject();
        format.addProperty("type", "json_object");
        body.add("response_format", format);

        JsonArray messages = new JsonArray();
        messages.add(message("system", SYSTEM_PROMPT));
        messages.add(message("user", snapshotJson));
        body.add("messages", messages);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/v1/chat/completions"))
                .timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Agnes API 返回 " + response.statusCode() + "：" + response.body());
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonElement contentElement = root.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content");
        if (contentElement == null || contentElement.isJsonNull()) {
            throw new IllegalStateException("Agnes API 未返回内容");
        }
        String content = contentElement.getAsString();
        if (content == null || content.isBlank() || content.trim().equalsIgnoreCase("null")) {
            throw new IllegalStateException("Agnes API 返回空 JSON：null");
        }
        return parseVerdict(content);
    }

    private static JsonObject message(String role, String content) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", role);
        msg.addProperty("content", content);
        return msg;
    }

    private static AiVerdict parseVerdict(String raw) {
        String text = raw.trim();
        if (text.startsWith("```")) {
            text = text.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }
        JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
        boolean suspected = obj.has("suspected") && obj.get("suspected").getAsBoolean();
        List<String> categories = new ArrayList<>();
        if (obj.has("categories") && obj.get("categories").isJsonArray()) {
            for (JsonElement e : obj.getAsJsonArray("categories")) {
                categories.add(e.getAsString());
            }
        }
        double confidence = obj.has("confidence") ? obj.get("confidence").getAsDouble() : 0.0;
        String reason = obj.has("reason") ? obj.get("reason").getAsString() : "";
        return new AiVerdict(suspected, categories, confidence, reason);
    }
}
