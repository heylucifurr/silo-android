package org.siloserver.silo.tv.ui.theme

import java.io.File
import javax.imageio.ImageIO
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvLauncherIconAssetsTest {

    @Test
    fun adaptiveForegroundIsMarkOnlyInsideTheSafeZone() {
        val foreground = ImageIO.read(
            File("src/androidMain/res/mipmap-xxxhdpi/ic_launcher_foreground.png"),
        )

        assertEquals(432, foreground.width)
        assertEquals(432, foreground.height)
        assertTrue(
            foreground.colorModel.hasAlpha(),
            "Adaptive foreground must use transparency so the launcher can own the mask.",
        )
        // The mark must not be a baked-in square tile: every edge and the centre
        // stay transparent so the launcher's shape shows the background colour.
        assertEquals(0, foreground.alphaAt(0, 0), "Top-left corner should be transparent.")
        assertEquals(0, foreground.alphaAt(foreground.width - 1, 0), "Top-right corner should be transparent.")
        assertEquals(0, foreground.alphaAt(0, foreground.height - 1), "Bottom-left corner should be transparent.")
        assertEquals(0, foreground.alphaAt(foreground.width - 1, foreground.height - 1), "Bottom-right corner should be transparent.")
        assertEquals(0, foreground.alphaAt(foreground.width / 2, 0), "Top edge should be transparent.")
        assertEquals(0, foreground.alphaAt(foreground.width / 2, foreground.height - 1), "Bottom edge should be transparent.")
        assertEquals(0, foreground.alphaAt(0, foreground.height / 2), "Left edge should be transparent.")
        assertEquals(0, foreground.alphaAt(foreground.width - 1, foreground.height / 2), "Right edge should be transparent.")

        // "Mark-only, not a baked tile" is about how much of the layer is
        // transparent, not about any one pixel: a centred wordmark legitimately
        // has an opaque centre, while a baked square tile fills the canvas.
        val transparent = foreground.transparentPixelCount()
        assertTrue(
            transparent > foreground.width * foreground.height / 2,
            "Adaptive foreground must be mostly transparent (mark-only), not a baked tile: " +
                "$transparent of ${foreground.width * foreground.height} pixels",
        )

        // A launcher masks the adaptive layer with a CIRCLE, so the corners of
        // the 66/108 safe-zone square are not safe: a wide mark fitted to the
        // zone's full width has its left and right ends outside the mask, and a
        // circular launcher slices the first and last letters off.
        val content = foreground.contentBounds()
        val maskRadius = foreground.width * (66f / 108f) / 2f
        val halfDiagonal = sqrt(
            (content.width / 2f) * (content.width / 2f) + (content.height / 2f) * (content.height / 2f),
        )
        assertTrue(
            halfDiagonal <= maskRadius,
            "Mark must fit inside the mask's circle (needs ${"%.1f".format(halfDiagonal)}px, " +
                "has ${"%.1f".format(maskRadius)}px): $content",
        )
        assertTrue(
            content.width * content.height > foreground.width * foreground.height / 50,
            "Adaptive foreground should contain a legible mark, not an empty or speckled layer: $content",
        )
    }

    @Test
    fun legacyTvLauncherIconsAreOpaqueSquaresAtRequiredDensities() {
        val expected = mapOf(
            "mipmap-mdpi/ic_launcher.png" to 80,
            "mipmap-hdpi/ic_launcher.png" to 120,
            "mipmap-xhdpi/ic_launcher.png" to 160,
            "mipmap-xxhdpi/ic_launcher.png" to 240,
            "mipmap-xxxhdpi/ic_launcher.png" to 320,
        )

        expected.forEach { (path, size) ->
            val image = ImageIO.read(File("src/androidMain/res/$path"))
            assertEquals(size, image.width, path)
            assertEquals(size, image.height, path)
            assertFalse(image.colorModel.hasAlpha(), "$path must be opaque for legacy TV launchers.")
            assertEquals(255, image.alphaAt(0, 0), "$path top-left corner must be opaque.")
            assertEquals(255, image.alphaAt(size - 1, size - 1), "$path bottom-right corner must be opaque.")
        }
    }

    @Test
    fun legacyTvLauncherIconKeepsACentredMarkOnThePlate() {
        val icon = ImageIO.read(File("src/androidMain/res/mipmap-xxxhdpi/ic_launcher.png"))
        // The mark is anything that is not the flat plate colour. Deriving it
        // from the image keeps this assertion brand-agnostic: whatever the
        // brand's palette is, the artwork has to read as a mark on a plate.
        val plate = icon.plateColour()
        val mark = icon.markPixels(plate)

        assertTrue(
            mark.size > icon.width * icon.height / 100,
            "Launcher icon must show a legible mark, not a flat plate: ${mark.size} pixels",
        )
        val averageX = mark.sumOf { it.first }.toDouble() / mark.size
        assertTrue(
            averageX in icon.width * 0.35..icon.width * 0.65,
            "Mark must remain horizontally centred: $averageX of ${icon.width}",
        )
        val averageY = mark.sumOf { it.second }.toDouble() / mark.size
        assertTrue(
            averageY in icon.height * 0.35..icon.height * 0.65,
            "Mark must remain vertically centred: $averageY of ${icon.height}",
        )
        val bounds = mark.pixelBounds()
        assertTrue(
            bounds.minX >= icon.width * 0.05 && bounds.maxX <= icon.width * 0.95 &&
                bounds.minY >= icon.height * 0.05 && bounds.maxY <= icon.height * 0.95,
            "Mark must keep a margin inside the tile so a launcher's mask cannot clip it: $bounds",
        )
    }

    @Test
    fun tvBannerKeepsItsLockupInsideTheFireTvSquareCrop() {
        val banner = ImageIO.read(File("src/androidMain/res/drawable/tv_banner.png"))
        assertEquals(320, banner.width)
        assertEquals(180, banner.height)
        assertFalse(banner.colorModel.hasAlpha(), "TV banner must be opaque.")

        // The lockup is whatever is not the flat background: Fire OS crops the
        // banner to its centred square, and the lockup is the part that must
        // survive that crop. Colour-agnostic, so a rebrand cannot silently move
        // the artwork out of the safe area.
        val background = banner.plateColour()
        val lockup = banner.markPixels(background)
        assertTrue(lockup.size > 500, "TV banner must show a lockup, not a flat background: ${lockup.size} pixels")

        val bounds = lockup.pixelBounds()
        assertTrue(
            bounds.minX >= 70 && bounds.maxX <= 249,
            "Lockup must remain inside Fire TV's centered square crop: $bounds",
        )
        assertTrue(
            bounds.width in 40..180 && bounds.height in 10..90,
            "Lockup must retain a complete, readable shape: $bounds",
        )
        assertTrue(
            bounds.width * bounds.height >= 500,
            "Lockup bounds must not collapse to a small or fragmented mark: $bounds",
        )
        val averageY = lockup.sumOf { it.second }.toDouble() / lockup.size
        assertTrue(
            averageY in banner.height * 0.35..banner.height * 0.65,
            "Lockup must remain vertically centred inside the banner: $averageY of ${banner.height}",
        )
    }
}

private fun java.awt.image.BufferedImage.alphaAt(x: Int, y: Int): Int =
    if (colorModel.hasAlpha()) {
        (getRGB(x, y) ushr 24) and 0xff
    } else {
        255
    }

/** The flat colour the artwork sits on: the most common pixel, i.e. the plate. */
private fun java.awt.image.BufferedImage.plateColour(): Int {
    val counts = HashMap<Int, Int>()
    for (y in 0 until height) {
        for (x in 0 until width) {
            val rgb = getRGB(x, y) and 0xffffff
            counts[rgb] = (counts[rgb] ?: 0) + 1
        }
    }
    return counts.maxByOrNull { it.value }?.key ?: 0
}

/** Every pixel that is not the plate colour, as (x, y) pairs. */
private fun java.awt.image.BufferedImage.markPixels(plate: Int): List<Pair<Int, Int>> =
    buildList {
        for (y in 0 until height) {
            for (x in 0 until width) {
                if ((getRGB(x, y) and 0xffffff) != plate) add(x to y)
            }
        }
    }

/** How much of the layer carries no artwork. */
private fun java.awt.image.BufferedImage.transparentPixelCount(): Int {
    var count = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (alphaAt(x, y) <= 16) count++
        }
    }
    return count
}

/** Bounding box of the visible (non-transparent) artwork. */
private fun java.awt.image.BufferedImage.contentBounds(): PixelBounds {
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (alphaAt(x, y) > 16) {
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }
    }
    return PixelBounds(minX = minX, minY = minY, maxX = maxX, maxY = maxY)
}

private data class PixelBounds(
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int,
) {
    val width: Int = maxX - minX + 1
    val height: Int = maxY - minY + 1
}

private fun List<Pair<Int, Int>>.pixelBounds(): PixelBounds = PixelBounds(
    minX = minOf { it.first },
    minY = minOf { it.second },
    maxX = maxOf { it.first },
    maxY = maxOf { it.second },
)
