package com.pmfby.evidence.core.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApiDtoMapperTest {

    @Test
    fun `parses a full farmer JSON map`() {
        val json = mapOf(
            "id" to "farmer-123",
            "farmer_name" to "Ramesh Kumar",
            "farmer_mobile" to "9988776655",
            "village_name" to "Rohtak Khas",
            "state_id" to "HR",
            "district_id" to "ROHTAK",
            "assigned_user_id" to "surveyor-9",
        )
        val farmer = ApiDtoMapper.farmerFromJson(json)
        assertEquals("farmer-123", farmer.id)
        assertEquals("Ramesh Kumar", farmer.farmerName)
        assertEquals("9988776655", farmer.farmerMobile)
        assertEquals("surveyor-9", farmer.assignedUserId)
    }

    @Test
    fun `farmer_mobile null is a valid unset state, not an error`() {
        val json = mapOf("id" to "farmer-1", "farmer_name" to "Sita", "farmer_mobile" to null)
        val farmer = ApiDtoMapper.farmerFromJson(json)
        assertNull(farmer.farmerMobile)
    }

    @Test
    fun `missing required id throws DtoParseException rather than producing a broken DTO`() {
        val json = mapOf("farmer_name" to "Sita")
        assertThrows(DtoParseException::class.java) { ApiDtoMapper.farmerFromJson(json) }
    }

    @Test
    fun `survey case numeric fields parse from both Int and String server shapes`() {
        val asInt = ApiDtoMapper.surveyCaseFromJson(
            mapOf("id" to "c1", "farmer_id" to "f1", "attempt_count" to 3, "forceful_survey_flag" to true),
        )
        assertEquals(3, asInt.attemptCount)
        assertTrue(asInt.forcefulSurveyFlag)

        val asString = ApiDtoMapper.surveyCaseFromJson(
            mapOf("id" to "c1", "farmer_id" to "f1", "attempt_count" to "3", "forceful_survey_flag" to "false"),
        )
        assertEquals(3, asString.attemptCount)
        assertEquals(false, asString.forcefulSurveyFlag)
    }

    @Test
    fun `call event geo fields parse numeric strings and absent geo is null, not zero`() {
        val withGeo = ApiDtoMapper.callEventFromJson(
            mapOf("id" to "e1", "case_id" to "c1", "geo_lat" to "28.9931", "geo_lon" to "76.5845", "geo_accuracy_m" to 12.5),
        )
        assertEquals(28.9931, withGeo.geoLat)
        assertEquals(76.5845, withGeo.geoLon)
        assertEquals(12.5, withGeo.geoAccuracyM)

        val withoutGeo = ApiDtoMapper.callEventFromJson(mapOf("id" to "e2", "case_id" to "c1"))
        assertNull(withoutGeo.geoLat)
        assertNull(withoutGeo.geoLon)
    }

    @Test
    fun `farmersFromJsonList maps every element in order`() {
        val list = listOf(
            mapOf("id" to "f1", "farmer_name" to "A"),
            mapOf("id" to "f2", "farmer_name" to "B"),
        )
        val mapped = ApiDtoMapper.farmersFromJsonList(list)
        assertEquals(listOf("f1", "f2"), mapped.map { it.id })
    }
}
