package com.silvaramon.cylinders_ctrl

import com.silvaramon.cylinders_ctrl.data.CylinderOcrData
import com.silvaramon.cylinders_ctrl.data.CylinderOcrParser
import com.silvaramon.cylinders_ctrl.data.OcrParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CylinderOcrParserTest {

    @Test
    fun parsesValuesOnSameLine() {
        val result = CylinderOcrParser.parse("EMPRESA X\nCAP_M3: 10,0\nN/S: ab12345")
        assertEquals(OcrParseResult.Success(CylinderOcrData("10.0", "AB12345")), result)
    }

    @Test
    fun parsesValuesOnNextLine() {
        val result = CylinderOcrParser.parse("CAP M3\n7.5\nN / S\n998877")
        assertEquals(OcrParseResult.Success(CylinderOcrData("7.5", "998877")), result)
    }

    @Test
    fun errorWhenSerialMissing() {
        val result = CylinderOcrParser.parse("CAP_M3 10")
        assertTrue(result is OcrParseResult.Error)
        assertTrue((result as OcrParseResult.Error).message.contains("N/S"))
    }

    @Test
    fun errorWhenNothingFound() {
        val result = CylinderOcrParser.parse("texto qualquer")
        assertTrue((result as OcrParseResult.Error).message.contains("CAP_M3 e N/S"))
    }
}
