package com.agnes.anticheat.ai;

import java.util.List;

public record AiVerdict(boolean suspected, List<String> categories, double confidence, String reason) {

    public static AiVerdict none() {
        return new AiVerdict(false, List.of(), 0.0, "AI 未返回有效判定结果");
    }
}
