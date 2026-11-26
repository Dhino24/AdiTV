package com.aditv.data

import com.aditv.data.weather.WeatherRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherCodesTest {
    @Test fun wmoCodes() {
        assertEquals("Ciel dégagé", WeatherRepository.describe(0).first)
        assertEquals("🌙", WeatherRepository.describe(0, day = false).second)
        assertEquals("Pluie", WeatherRepository.describe(63).first)
        assertEquals("Orage", WeatherRepository.describe(95).first)
    }
}
