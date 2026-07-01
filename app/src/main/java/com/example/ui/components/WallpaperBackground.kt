package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun WallpaperBackground(wallpaperName: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        when (wallpaperName) {
            "cosmic_dark" -> {
                // Deep dark space with glowing cosmic ambient light
                drawRect(color = Color(0xFF0F101A))
                // Top-right glowing indigo radial circle
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x334D46B4), Color.Transparent),
                        center = Offset(width * 0.8f, height * 0.2f),
                        radius = width * 0.8f
                    ),
                    center = Offset(width * 0.8f, height * 0.2f),
                    radius = width * 0.8f
                )
                // Bottom-left soft violet radial circle
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x228B5CF6), Color.Transparent),
                        center = Offset(width * 0.1f, height * 0.8f),
                        radius = width * 0.9f
                    ),
                    center = Offset(width * 0.1f, height * 0.8f),
                    radius = width * 0.9f
                )
            }
            "pastel_sunset" -> {
                // Soft gradient from peach to calming lavender
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFFD1B3), Color(0xFFE8DBFC), Color(0xFFB3C5FF)),
                        start = Offset(0f, 0f),
                        end = Offset(width, height)
                    )
                )
            }
            "emerald_forest" -> {
                // Sophisticated dark forest green with a warm upper light
                drawRect(color = Color(0xFF0A1510))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x2E10B981), Color.Transparent),
                        center = Offset(width * 0.5f, -height * 0.1f),
                        radius = height * 0.6f
                    ),
                    center = Offset(width * 0.5f, -height * 0.1f),
                    radius = height * 0.6f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x1F059669), Color.Transparent),
                        center = Offset(width * 0.9f, height * 0.8f),
                        radius = width * 0.7f
                    ),
                    center = Offset(width * 0.9f, height * 0.8f),
                    radius = width * 0.7f
                )
            }
            "neon_night" -> {
                // Deep cyberpunk dark obsidian with sharp neon glow
                drawRect(color = Color(0xFF080711))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x22EC4899), Color.Transparent),
                        center = Offset(width * 0.2f, height * 0.3f),
                        radius = width * 0.6f
                    ),
                    center = Offset(width * 0.2f, height * 0.3f),
                    radius = width * 0.6f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x2206B6D4), Color.Transparent),
                        center = Offset(width * 0.8f, height * 0.7f),
                        radius = width * 0.6f
                    ),
                    center = Offset(width * 0.8f, height * 0.7f),
                    radius = width * 0.6f
                )
            }
            else -> {
                // Default: Dark slate minimalist background
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A)),
                        start = Offset(0f, 0f),
                        end = Offset(width, height)
                    )
                )
            }
        }
    }
}
