package com.vnlauncher.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContentFilterTest {

    private final ContentFilter filter = ContentFilter.withDefaults();

    @Test
    void safeAndSuggestiveImagesAreNotSensitive() {
        assertFalse(filter.isSensitive(0.0, 0.0));
        assertFalse(filter.isSensitive(1.0, 1.0), "El umbral es 'mayor que', no 'mayor o igual'");
    }

    @Test
    void highSexualOrViolenceIsSensitive() {
        assertTrue(filter.isSensitive(1.4, 0.0));
        assertTrue(filter.isSensitive(0.0, 2.0));
    }

    @Test
    void missingRatingsAreNotSensitive() {
        assertFalse(filter.isSensitive(null, null));
    }
}
