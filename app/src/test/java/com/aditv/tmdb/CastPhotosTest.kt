package com.aditv.tmdb

import com.aditv.data.tmdb.needsPhotos
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastPhotosTest {
    @Test fun oldCastFormatIsRefetched() {
        assertTrue("Tom Holland|Peter Parker\nZendaya|MJ".needsPhotos())
        assertTrue("Zendaya".needsPhotos())
    }
    @Test fun newCastFormatIsKept() {
        assertFalse("Tom Holland|Peter Parker|/a.jpg\nZendaya||".needsPhotos())
        assertFalse(null.needsPhotos())
    }
}
