package de.ingvoss.fotosortierer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Eigene Linien-Symbole (24×24). Sie ersetzen die große Bibliothek material-icons-extended, die das
 * Debug-Bauen verlangsamen würde; alle Symbole werden über `Icon(tint = …)` eingefärbt.
 */
object AppIcons {
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()

    private fun ImageVector.Builder.line(width: Float = 2f, block: PathBuilder.() -> Unit) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block
        )
    }

    private fun ImageVector.Builder.solid(block: PathBuilder.() -> Unit) {
        path(fill = SolidColor(Color.Black), pathBuilder = block)
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
        close()
    }

    val Check: ImageVector by lazy {
        icon("Check") { line(2.5f) { moveTo(4f, 12.5f); lineTo(9.5f, 18f); lineTo(20f, 6.5f) } }
    }

    val Trash: ImageVector by lazy {
        icon("Trash") {
            line {
                moveTo(4f, 7f); lineTo(20f, 7f)
                moveTo(9f, 7f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 7f)
                moveTo(6f, 7f); lineTo(7f, 20f); lineTo(17f, 20f); lineTo(18f, 7f)
                moveTo(10f, 11f); lineTo(10f, 16f)
                moveTo(14f, 11f); lineTo(14f, 16f)
            }
        }
    }

    val Clock: ImageVector by lazy {
        icon("Clock") {
            line {
                circle(12f, 12f, 9f)
                moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f)
            }
        }
    }

    val Undo: ImageVector by lazy {
        icon("Undo") {
            line {
                moveTo(9f, 6f); lineTo(4f, 11f); lineTo(9f, 16f)
                moveTo(4f, 11f); lineTo(14f, 11f)
                curveTo(17.3f, 11f, 20f, 13.7f, 20f, 17f)
            }
        }
    }

    val Home: ImageVector by lazy {
        icon("Home") {
            line {
                moveTo(3.5f, 11f); lineTo(12f, 4f); lineTo(20.5f, 11f)
                moveTo(6f, 9.5f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 9.5f)
                moveTo(10f, 20f); lineTo(10f, 14f); lineTo(14f, 14f); lineTo(14f, 20f)
            }
        }
    }

    val Calendar: ImageVector by lazy {
        icon("Calendar") {
            line {
                moveTo(4f, 6f); lineTo(20f, 6f); lineTo(20f, 20f); lineTo(4f, 20f); close()
                moveTo(4f, 10f); lineTo(20f, 10f)
                moveTo(8f, 3f); lineTo(8f, 7f)
                moveTo(16f, 3f); lineTo(16f, 7f)
            }
        }
    }

    val Pin: ImageVector by lazy {
        icon("Pin") {
            line {
                moveTo(12f, 21f)
                curveTo(12f, 21f, 5f, 14.5f, 5f, 9.5f)
                arcTo(7f, 7f, 0f, false, true, 19f, 9.5f)
                curveTo(19f, 14.5f, 12f, 21f, 12f, 21f)
                close()
                circle(12f, 9.5f, 2.5f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        icon("Settings") {
            line {
                moveTo(4f, 7f); lineTo(20f, 7f)
                moveTo(4f, 12f); lineTo(20f, 12f)
                moveTo(4f, 17f); lineTo(20f, 17f)
                circle(9f, 7f, 2f)
                circle(15f, 12f, 2f)
                circle(8f, 17f, 2f)
            }
        }
    }

    val VolumeOn: ImageVector by lazy {
        icon("VolumeOn") {
            line {
                moveTo(4f, 9f); lineTo(8f, 9f); lineTo(13f, 5f); lineTo(13f, 19f); lineTo(8f, 15f); lineTo(4f, 15f); close()
                moveTo(16f, 9f); curveTo(17.5f, 10.5f, 17.5f, 13.5f, 16f, 15f)
                moveTo(18.5f, 6.5f); curveTo(21.5f, 9.5f, 21.5f, 14.5f, 18.5f, 17.5f)
            }
        }
    }

    val VolumeOff: ImageVector by lazy {
        icon("VolumeOff") {
            line {
                moveTo(4f, 9f); lineTo(8f, 9f); lineTo(13f, 5f); lineTo(13f, 19f); lineTo(8f, 15f); lineTo(4f, 15f); close()
                moveTo(16.5f, 9.5f); lineTo(21f, 14.5f)
                moveTo(21f, 9.5f); lineTo(16.5f, 14.5f)
            }
        }
    }

    val Play: ImageVector by lazy {
        icon("Play") { solid { moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close() } }
    }

    val Pause: ImageVector by lazy {
        icon("Pause") {
            line(3f) {
                moveTo(8f, 5f); lineTo(8f, 19f)
                moveTo(16f, 5f); lineTo(16f, 19f)
            }
        }
    }

    val Back: ImageVector by lazy {
        icon("Back") {
            line { moveTo(20f, 12f); lineTo(5f, 12f); moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f) }
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") { line { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) } }
    }

    val Chevron: ImageVector by lazy {
        icon("Chevron") { line { moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f) } }
    }

    val Photo: ImageVector by lazy {
        icon("Photo") {
            line {
                moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 19f); lineTo(4f, 19f); close()
                moveTo(4f, 16f); lineTo(9f, 11f); lineTo(13f, 15f); lineTo(16f, 12f); lineTo(20f, 16f)
                circle(15.5f, 8.5f, 1.2f)
            }
        }
    }

    val Video: ImageVector by lazy {
        icon("Video") {
            line {
                moveTo(3f, 7f); lineTo(15f, 7f); lineTo(15f, 17f); lineTo(3f, 17f); close()
                moveTo(15f, 11f); lineTo(21f, 8f); lineTo(21f, 16f); lineTo(15f, 13f)
            }
        }
    }

    val SelectAll: ImageVector by lazy {
        icon("SelectAll") {
            line {
                moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); lineTo(4f, 20f); close()
                moveTo(8f, 12f); lineTo(11f, 15f); lineTo(16.5f, 9f)
            }
        }
    }

    val Info: ImageVector by lazy {
        icon("Info") {
            line {
                circle(12f, 12f, 9f)
                moveTo(12f, 11f); lineTo(12f, 17f)
                moveTo(12f, 7.5f); lineTo(12f, 7.6f)
            }
        }
    }

    val Sort: ImageVector by lazy {
        icon("Sort") {
            line {
                moveTo(7f, 4f); lineTo(7f, 20f); moveTo(3.5f, 16.5f); lineTo(7f, 20f); lineTo(10.5f, 16.5f)
                moveTo(17f, 20f); lineTo(17f, 4f); moveTo(13.5f, 7.5f); lineTo(17f, 4f); lineTo(20.5f, 7.5f)
            }
        }
    }

    private fun PathBuilder.star() {
        moveTo(12f, 3f); lineTo(14.7f, 9f); lineTo(21.2f, 9.6f); lineTo(16.3f, 13.9f); lineTo(17.8f, 20.3f)
        lineTo(12f, 17f); lineTo(6.2f, 20.3f); lineTo(7.7f, 13.9f); lineTo(2.8f, 9.6f); lineTo(9.3f, 9f); close()
    }

    private fun PathBuilder.eye() {
        moveTo(2.5f, 12f)
        curveTo(5f, 7.5f, 8.5f, 6f, 12f, 6f)
        curveTo(15.5f, 6f, 19f, 7.5f, 21.5f, 12f)
        curveTo(19f, 16.5f, 15.5f, 18f, 12f, 18f)
        curveTo(8.5f, 18f, 5f, 16.5f, 2.5f, 12f)
        close()
        circle(12f, 12f, 3f)
    }

    val Star: ImageVector by lazy { icon("Star") { line { star() } } }

    val StarFilled: ImageVector by lazy { icon("StarFilled") { solid { star() } } }

    val Eye: ImageVector by lazy { icon("Eye") { line { eye() } } }

    val EyeOff: ImageVector by lazy {
        icon("EyeOff") { line { eye(); moveTo(4f, 4f); lineTo(20f, 20f) } }
    }

    val Folder: ImageVector by lazy {
        icon("Folder") {
            line {
                moveTo(3f, 6f); lineTo(10f, 6f); lineTo(12f, 8.5f); lineTo(21f, 8.5f)
                lineTo(21f, 19f); lineTo(3f, 19f); close()
            }
        }
    }

    val Open: ImageVector by lazy {
        icon("Open") {
            line {
                moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f)
                moveTo(20f, 4f); lineTo(11f, 13f)
                moveTo(18f, 14f); lineTo(18f, 20f); lineTo(4f, 20f); lineTo(4f, 6f); lineTo(10f, 6f)
            }
        }
    }
}
