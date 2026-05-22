package com.techcobber.smarttrader.v1.strategy;

import java.util.List;
import java.util.Objects;

import com.techcobber.smarttrader.v1.strategy.CandlePatternDetector.DetectedPattern;

public final class PatternUtils {

    private PatternUtils() {}

    private static final String[] STRONG_PATTERNS = new String[] {
            "ENGULFING", "MORNING_STAR", "EVENING_STAR", "THREE_WHITE", "THREE_BLACK", "MARUBOZU"
    };

    public static boolean hasStrongPattern(List<DetectedPattern> patterns) {
        if (patterns == null || patterns.isEmpty()) return false;
        return patterns.stream().filter(Objects::nonNull).map(DetectedPattern::getName)
                .anyMatch(PatternUtils::nameHasStrongPattern);
    }

    public static boolean hasStrongPatternByNames(List<String> patternNames) {
        if (patternNames == null || patternNames.isEmpty()) return false;
        return patternNames.stream().filter(Objects::nonNull)
                .anyMatch(PatternUtils::nameHasStrongPattern);
    }

    private static boolean nameHasStrongPattern(String name) {
        if (name == null) return false;
        for (String p : STRONG_PATTERNS) {
            if (name.contains(p)) return true;
        }
        return false;
    }
}
