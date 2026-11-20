package com.vnlauncher.service;

import com.vnlauncher.model.BlurLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContentFilterTest {

    @Test
    void defaultBlursOnlyExplicitImages() {
        ContentFilter filter = ContentFilter.withDefaults();
        assertEquals(BlurLevel.EXPLICIT, filter.level());
        assertFalse(filter.isSensitive(1.21, 0.0), "Media 1.21 = sugerente (se redondea a 1)");
        assertTrue(filter.isSensitive(1.93, 0.0), "Media 1.93 = explícita");
        assertTrue(filter.isSensitive(0.0, 1.5), "Violencia brutal también");
    }

    @Test
    void suggestiveLevelAlsoBlursSuggestiveImages() {
        ContentFilter filter = new ContentFilter(BlurLevel.SUGGESTIVE);
        assertTrue(filter.isSensitive(0.88, 0.0));
        assertFalse(filter.isSensitive(0.2, 0.0), "Casi todos la votan como segura");
    }

    @Test
    void noneNeverBlursAndLevelCanChangeAtRuntime() {
        ContentFilter filter = ContentFilter.withDefaults();
        filter.setLevel(BlurLevel.NONE);
        assertFalse(filter.isSensitive(2.0, 2.0));
    }

    @Test
    void missingRatingsAreNotSensitive() {
        assertFalse(new ContentFilter(BlurLevel.SUGGESTIVE).isSensitive(null, null));
    }
}
