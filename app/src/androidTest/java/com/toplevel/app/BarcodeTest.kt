package com.toplevel.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.toplevel.app.data.FoodScan
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The on-device barcode reader (ML Kit) on a drawn EAN-13. Runs on the normal debug build and,
 * in CI, on the shrunk "qa" build too, where R8 once broke the scanner.
 */
@RunWith(AndroidJUnit4::class)
class BarcodeTest {

    @Test fun readsEan13OnDevice() = runBlocking {
        val code = "4006381333931"
        assertEquals(code, FoodScan.readBarcode(ean13(code)))
    }

    private fun ean13(code: String): Bitmap {
        val l = listOf("0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011")
        val g = listOf("0100111", "0110011", "0011011", "0100001", "0011101", "0111001", "0000101", "0010001", "0001001", "0010111")
        val r = l.map { p -> p.map { if (it == '0') '1' else '0' }.joinToString("") }
        val parity = listOf("LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL")
        val d = code.map { it - '0' }
        val bits = buildString {
            append("101")
            for (i in 1..6) append(if (parity[d[0]][i - 1] == 'L') l[d[i]] else g[d[i]])
            append("01010")
            for (i in 7..12) append(r[d[i]])
            append("101")
        }
        val module = 6; val quiet = 15 * module
        val bmp = Bitmap.createBitmap(bits.length * module + 2 * quiet, 360, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.WHITE)
        val black = Paint().apply { color = Color.BLACK }
        bits.forEachIndexed { i, b ->
            if (b == '1') c.drawRect((quiet + i * module).toFloat(), 30f, (quiet + (i + 1) * module).toFloat(), 330f, black)
        }
        return bmp
    }
}
