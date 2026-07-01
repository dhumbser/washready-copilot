package com.washready.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class FechasUtil {

    public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    private FechasUtil() {}

    public static Instant toInstant(LocalDateTime ldt) {
        return ldt == null ? null : ldt.atZone(ZONE).toInstant();
    }
}
