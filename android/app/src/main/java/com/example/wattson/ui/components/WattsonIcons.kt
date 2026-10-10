package com.example.wattson.ui.components

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

object WattsonIcons {
    val Bolt: ImageVector by lazy {
        materialIcon(name = "Bolt") {
            materialPath {
                moveTo(11.0f, 21.0f)
                horizontalLineToRelative(-1.0f)
                lineToRelative(1.0f, -7.0f)
                horizontalLineTo(7.5f)
                curveToRelative(-0.58f, 0.0f, -0.57f, -0.32f, -0.38f, -0.66f)
                curveToRelative(0.19f, -0.34f, 0.05f, -0.08f, 0.07f, -0.12f)
                curveTo(8.48f, 10.94f, 10.42f, 7.54f, 13.0f, 3.0f)
                horizontalLineToRelative(1.0f)
                lineToRelative(-1.0f, 7.0f)
                horizontalLineToRelative(3.5f)
                curveToRelative(0.49f, 0.0f, 0.56f, 0.33f, 0.47f, 0.51f)
                lineToRelative(-0.07f, 0.15f)
                curveTo(12.96f, 17.58f, 11.0f, 21.0f, 11.0f, 21.0f)
                close()
            }
        }
    }

    val EvStation: ImageVector by lazy {
        materialIcon(name = "EvStation") {
            materialPath {
                moveTo(19.77f, 7.23f)
                lineToRelative(0.01f, -0.01f)
                lineToRelative(-3.72f, -3.72f)
                lineTo(15.0f, 4.56f)
                lineToRelative(2.11f, 2.11f)
                curveTo(16.16f, 7.0f, 15.0f, 8.24f, 15.0f, 10.0f)
                verticalLineToRelative(7.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-5.0f)
                horizontalLineToRelative(1.0f)
                verticalLineToRelative(5.0f)
                horizontalLineToRelative(1.5f)
                verticalLineTo(8.5f)
                curveTo(21.5f, 7.95f, 20.25f, 7.37f, 19.77f, 7.23f)
                close()
                moveTo(12.0f, 5.0f)
                horizontalLineTo(6.0f)
                curveTo(4.9f, 5.0f, 4.0f, 5.9f, 4.0f, 7.0f)
                verticalLineToRelative(12.0f)
                horizontalLineToRelative(10.0f)
                verticalLineTo(7.0f)
                curveTo(14.0f, 5.9f, 13.1f, 5.0f, 12.0f, 5.0f)
                close()
                moveTo(10.5f, 13.5f)
                horizontalLineToRelative(-2.0f)
                lineToRelative(1.5f, -3.0f)
                horizontalLineTo(8.0f)
                verticalLineTo(7.5f)
                horizontalLineToRelative(3.0f)
                lineToRelative(-1.5f, 3.0f)
                horizontalLineToRelative(1.0f)
                verticalLineTo(13.5f)
                close()
            }
        }
    }

    val Route: ImageVector by lazy {
        materialIcon(name = "Route") {
            materialPath {
                moveTo(19.0f, 15.18f)
                verticalLineTo(7.0f)
                curveToRelative(0.0f, -2.21f, -1.79f, -4.0f, -4.0f, -4.0f)
                reflectiveCurveToRelative(-4.0f, 1.79f, -4.0f, 4.0f)
                verticalLineToRelative(10.0f)
                curveToRelative(0.0f, 1.1f, -0.9f, 2.0f, -2.0f, 2.0f)
                reflectiveCurveToRelative(-2.0f, -0.9f, -2.0f, -2.0f)
                verticalLineTo(8.82f)
                curveTo(8.16f, 8.4f, 9.0f, 7.3f, 9.0f, 6.0f)
                curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                reflectiveCurveTo(3.0f, 4.34f, 3.0f, 6.0f)
                curveToRelative(0.0f, 1.3f, 0.84f, 2.4f, 2.0f, 2.82f)
                verticalLineTo(17.0f)
                curveToRelative(0.0f, 2.21f, 1.79f, 4.0f, 4.0f, 4.0f)
                reflectiveCurveToRelative(4.0f, -1.79f, 4.0f, -4.0f)
                verticalLineTo(7.0f)
                curveToRelative(0.0f, -1.1f, 0.9f, -2.0f, 2.0f, -2.0f)
                reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                verticalLineToRelative(8.18f)
                curveToRelative(-1.16f, 0.41f, -2.0f, 1.51f, -2.0f, 2.82f)
                curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
                reflectiveCurveToRelative(3.0f, -1.34f, 3.0f, -3.0f)
                curveTo(21.0f, 16.7f, 20.16f, 15.6f, 19.0f, 15.18f)
                close()
            }
        }
    }

    val Savings: ImageVector by lazy {
        materialIcon(name = "Savings") {
            materialPath {
                moveTo(15.0f, 10.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
                reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                reflectiveCurveTo(15.0f, 10.55f, 15.0f, 10.0f)
                close()
                moveTo(21.0f, 9.24f)
                verticalLineToRelative(5.51f)
                curveToRelative(0.0f, 2.26f, -1.75f, 4.14f, -4.0f, 4.24f)
                verticalLineTo(21.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineTo(9.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(7.0f)
                verticalLineToRelative(-2.06f)
                curveToRelative(-2.34f, -0.38f, -4.0f, -2.41f, -4.0f, -4.69f)
                verticalLineTo(11.0f)
                curveToRelative(0.0f, -1.66f, 1.34f, -3.0f, 3.0f, -3.0f)
                horizontalLineToRelative(1.0f)
                verticalLineTo(6.0f)
                curveToRelative(0.0f, -1.66f, 1.34f, -3.0f, 3.0f, -3.0f)
                horizontalLineToRelative(4.0f)
                curveToRelative(1.66f, 0.0f, 3.0f, 1.34f, 3.0f, 3.0f)
                verticalLineToRelative(1.1f)
                curveTo(19.34f, 7.55f, 21.0f, 8.24f, 21.0f, 9.24f)
                close()
                moveTo(19.0f, 11.0f)
                curveToRelative(0.0f, -0.7f, -1.19f, -1.26f, -2.74f, -1.44f)
                lineTo(15.0f, 9.42f)
                verticalLineTo(6.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-4.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(6.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(3.25f)
                curveToRelative(0.0f, 1.52f, 1.23f, 2.75f, 2.75f, 2.75f)
                horizontalLineTo(17.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(11.0f)
                close()
            }
        }
    }

    val Bedtime: ImageVector by lazy {
        materialIcon(name = "Bedtime") {
            materialPath {
                moveTo(12.3f, 2.0f)
                curveToRelative(0.43f, 0.0f, 0.81f, 0.27f, 0.95f, 0.68f)
                curveToRelative(0.15f, 0.41f, 0.01f, 0.86f, -0.32f, 1.13f)
                curveTo(10.68f, 5.64f, 9.5f, 8.68f, 9.5f, 12.0f)
                curveToRelative(0.0f, 4.41f, 3.59f, 8.0f, 8.0f, 8.0f)
                curveToRelative(0.79f, 0.0f, 1.57f, -0.12f, 2.32f, -0.35f)
                curveToRelative(0.41f, -0.13f, 0.87f, -0.01f, 1.15f, 0.31f)
                curveToRelative(0.28f, 0.32f, 0.34f, 0.77f, 0.16f, 1.16f)
                curveTo(19.23f, 22.84f, 15.86f, 24.0f, 12.0f, 24.0f)
                curveTo(5.37f, 24.0f, 0.0f, 18.63f, 0.0f, 12.0f)
                curveTo(0.0f, 6.48f, 3.73f, 1.83f, 9.04f, 0.44f)
                curveTo(10.11f, 0.16f, 11.22f, 2.0f, 12.3f, 2.0f)
                close()
            }
        }
    }

    val Speed: ImageVector by lazy {
        materialIcon(name = "Speed") {
            materialPath {
                moveTo(20.38f, 8.57f)
                lineToRelative(-1.23f, 1.85f)
                curveToRelative(0.53f, 1.05f, 0.85f, 2.24f, 0.85f, 3.58f)
                curveToRelative(0.0f, 4.42f, -3.58f, 8.0f, -8.0f, 8.0f)
                reflectiveCurveToRelative(-8.0f, -3.58f, -8.0f, -8.0f)
                curveToRelative(0.0f, -4.08f, 3.05f, -7.44f, 7.0f, -7.93f)
                verticalLineToRelative(2.02f)
                curveTo(7.99f, 8.59f, 6.0f, 11.06f, 6.0f, 14.0f)
                curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
                reflectiveCurveToRelative(6.0f, -2.69f, 6.0f, -6.0f)
                curveToRelative(0.0f, -1.04f, -0.27f, -2.02f, -0.74f, -2.87f)
                lineToRelative(-3.04f, 4.56f)
                curveToRelative(-0.16f, 0.24f, -0.42f, 0.38f, -0.71f, 0.38f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                curveToRelative(0.0f, -0.23f, 0.08f, -0.44f, 0.21f, -0.61f)
                lineToRelative(3.5f, -5.25f)
                curveToRelative(0.24f, -0.36f, 0.74f, -0.45f, 1.1f, -0.21f)
                curveTo(19.24f, 9.61f, 20.38f, 8.57f, 20.38f, 8.57f)
                close()
            }
        }
    }

    val BatteryCharging: ImageVector by lazy {
        materialIcon(name = "BatteryCharging") {
            materialPath {
                moveTo(15.67f, 4.0f)
                horizontalLineTo(14.0f)
                verticalLineTo(2.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(8.33f)
                curveTo(7.6f, 4.0f, 7.0f, 4.6f, 7.0f, 5.33f)
                verticalLineToRelative(15.33f)
                curveTo(7.0f, 21.4f, 7.6f, 22.0f, 8.33f, 22.0f)
                horizontalLineToRelative(7.33f)
                curveToRelative(0.74f, 0.0f, 1.34f, -0.6f, 1.34f, -1.33f)
                verticalLineTo(5.33f)
                curveTo(17.0f, 4.6f, 16.4f, 4.0f, 15.67f, 4.0f)
                close()
                moveTo(11.0f, 18.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineTo(9.0f)
                lineToRelative(4.0f, -7.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(2.0f)
                lineToRelative(-4.0f, 7.0f)
                close()
            }
        }
    }

    val PowerOff: ImageVector by lazy {
        materialIcon(name = "PowerOff") {
            materialPath {
                moveTo(18.0f, 14.41f)
                verticalLineTo(9.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-1.0f)
                verticalLineTo(3.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(5.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineTo(3.0f)
                horizontalLineTo(8.0f)
                verticalLineToRelative(2.18f)
                lineTo(4.41f, 1.59f)
                lineTo(3.0f, 3.0f)
                lineToRelative(18.0f, 18.0f)
                lineToRelative(1.41f, -1.41f)
                lineTo(18.0f, 14.41f)
                close()
                moveTo(6.0f, 9.0f)
                verticalLineToRelative(5.41f)
                curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
                curveToRelative(1.17f, 0.0f, 2.25f, -0.34f, 3.17f, -0.92f)
                lineTo(7.08f, 9.0f)
                horizontalLineTo(6.0f)
                close()
            }
        }
    }

    val AddRoad: ImageVector by lazy {
        materialIcon(name = "AddRoad") {
            materialPath {
                moveTo(20.0f, 18.0f)
                verticalLineToRelative(-3.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(-3.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-3.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-3.0f)
                close()
                moveTo(18.0f, 4.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(9.0f)
                horizontalLineToRelative(-2.0f)
                close()
                moveTo(4.0f, 4.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(16.0f)
                horizontalLineTo(4.0f)
                close()
                moveTo(11.0f, 4.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(-2.0f)
                close()
                moveTo(11.0f, 10.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(-2.0f)
                close()
                moveTo(11.0f, 16.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(-2.0f)
                close()
            }
        }
    }
}
