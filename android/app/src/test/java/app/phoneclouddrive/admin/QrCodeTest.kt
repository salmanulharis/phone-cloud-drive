package app.phoneclouddrive.admin

import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class QrCodeTest {
    /** Renders the matrix with a quiet zone and decodes it back, like a phone camera would. */
    private fun roundTrip(text: String): String {
        val m = assertNotNullAndGet(qrMatrix(text))
        val scale = 4
        val quiet = 4
        val side = (m.width + 2 * quiet) * scale
        val pixels = ByteArray(side * side) { 0xFF.toByte() }
        for (y in 0 until m.height) for (x in 0 until m.width) if (m[x, y]) {
            for (dy in 0 until scale) for (dx in 0 until scale) {
                pixels[((y + quiet) * scale + dy) * side + (x + quiet) * scale + dx] = 0
            }
        }
        val source = object : LuminanceSource(side, side) {
            override fun getRow(y: Int, row: ByteArray?): ByteArray = pixels.copyOfRange(y * side, (y + 1) * side)
            override fun getMatrix(): ByteArray = pixels
        }
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    private fun <T> assertNotNullAndGet(v: T?): T {
        assertNotNull(v)
        return v!!
    }

    @Test
    fun encodesDriveAddresses() {
        for (url in listOf("https://drive.example.com", "http://192.168.1.20:8080", "https://dav.my-long-domain-name.example.org")) {
            assertEquals(url, roundTrip(url))
        }
    }
}
