package com.haven.kegent.core;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;

/** Second-precision input, accepting older saved minute-precision settings. */
public final class RunDateTime {
    private static final DateTimeFormatter INPUT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm[:ss]", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter OUTPUT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT);

    private RunDateTime() {}

    public static LocalDateTime parse(String value) {
        try {
            return LocalDateTime.parse(value.trim(), INPUT);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("时间格式应为 年-月-日 时:分:秒，例如 2026-10-18 19:02:30");
        }
    }

    public static String format(LocalDateTime time) {
        return OUTPUT.format(time.withNano(0));
    }
}
