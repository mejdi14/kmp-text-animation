package com.mejdi14.kmp_text_animation.showcase.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mejdi14.kmp_text_animation.showcase.ShowcaseColors
import io.github.mejdi14.textmotion.TextAnimationController
import kotlin.math.roundToInt

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, color = ShowcaseColors.TextSecondary, fontSize = 12.sp, modifier = modifier)
}

@Composable
fun TextControl(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ShowcaseColors.Accent,
            unfocusedBorderColor = ShowcaseColors.Border,
            focusedLabelColor = ShowcaseColors.Accent,
            unfocusedLabelColor = ShowcaseColors.TextSecondary,
            focusedTextColor = ShowcaseColors.TextPrimary,
            unfocusedTextColor = ShowcaseColors.TextPrimary,
            cursorColor = ShowcaseColors.Accent,
        ),
    )
}

@Composable
fun SliderControl(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueFormat: (Float) -> String = { (kotlin.math.round(it * 100f) / 100f).toString() },
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(label)
            Text(valueFormat(value), color = ShowcaseColors.TextPrimary, fontSize = 12.sp)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = ShowcaseColors.Accent,
                activeTrackColor = ShowcaseColors.Accent,
                inactiveTrackColor = ShowcaseColors.Border,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun IntSliderControl(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    valueFormat: (Int) -> String = { it.toString() },
) {
    SliderControl(
        label = label,
        value = value.toFloat(),
        onValueChange = { onValueChange(it.roundToInt()) },
        range = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first - 1).coerceAtLeast(0).coerceAtMost(100),
        valueFormat = { valueFormat(it.roundToInt()) },
    )
}

@Composable
fun DurationControl(
    label: String = "Duration",
    valueMillis: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange = 100..5000,
) {
    SliderControl(
        label = label,
        value = valueMillis.toFloat(),
        onValueChange = { onValueChange((it / 50f).roundToInt() * 50) },
        range = range.first.toFloat()..range.last.toFloat(),
        valueFormat = { "${it.roundToInt()} ms" },
    )
}

val DefaultColorPalette: List<Color> = listOf(
    Color(0xFFFAFAFA), Color(0xFF8B5CF6), Color(0xFF3B82F6), Color(0xFF06B6D4),
    Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFFEC4899),
)

@Composable
fun ColorControl(
    label: String,
    selected: Color,
    onSelect: (Color) -> Unit,
    palette: List<Color> = DefaultColorPalette,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(label)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            palette.forEach { c ->
                val isSelected = c == selected
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(
                            BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) ShowcaseColors.TextPrimary else ShowcaseColors.Border),
                            CircleShape,
                        )
                        .clickable { onSelect(c) },
                )
            }
        }
    }
}

val GradientPalettes: List<List<Color>> = listOf(
    listOf(Color(0xFF8B5CF6), Color(0xFF3B82F6), Color(0xFF06B6D4)),
    listOf(Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFFEC4899)),
    listOf(Color(0xFF10B981), Color(0xFF06B6D4), Color(0xFF3B82F6)),
    listOf(Color(0xFFEC4899), Color(0xFF8B5CF6), Color(0xFF3B82F6)),
    listOf(Color(0xFFFAFAFA), Color(0xFF8B5CF6), Color(0xFFFAFAFA)),
)

@Composable
fun ColorsControl(
    label: String,
    selected: List<Color>,
    onSelect: (List<Color>) -> Unit,
    palettes: List<List<Color>> = GradientPalettes,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(label)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            palettes.forEach { colors ->
                val isSelected = colors == selected
                val shape = RoundedCornerShape(50)
                Box(
                    Modifier
                        .size(width = 64.dp, height = 28.dp)
                        .clip(shape)
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(colors))
                        .border(
                            BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) ShowcaseColors.TextPrimary else ShowcaseColors.Border),
                            shape,
                        )
                        .clickable { onSelect(colors) },
                )
            }
        }
    }
}

@Composable
fun SwitchControl(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Label(label)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = ShowcaseColors.Accent,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = ShowcaseColors.Border,
                uncheckedThumbColor = ShowcaseColors.TextSecondary,
                uncheckedBorderColor = ShowcaseColors.Border,
            ),
        )
    }
}

@Composable
fun <T> ChoiceControl(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelOf: (T) -> String = { it.toString() },
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(label)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                val shape = RoundedCornerShape(50)
                Text(
                    labelOf(option),
                    fontSize = 13.sp,
                    maxLines = 1,
                    color = if (isSelected) ShowcaseColors.TextPrimary else ShowcaseColors.TextSecondary,
                    modifier = Modifier
                        .clip(shape)
                        .background(if (isSelected) ShowcaseColors.Accent else ShowcaseColors.SurfaceRaised)
                        .border(1.dp, if (isSelected) ShowcaseColors.Accent else ShowcaseColors.Border, shape)
                        .clickable { onSelect(option) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        text,
        color = ShowcaseColors.TextPrimary,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(shape)
            .background(ShowcaseColors.SurfaceRaised)
            .border(1.dp, ShowcaseColors.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
fun PlaybackControls(controller: TextAnimationController, showPause: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("↻ Replay") { controller.restart() }
            if (showPause) {
                PillButton(if (controller.isPlaying) "❚❚ Pause" else "▶ Resume") { controller.toggle() }
            }
        }
        SliderControl(
            label = "Speed",
            value = controller.speed,
            onValueChange = { controller.speed = it },
            range = 0.25f..3f,
            valueFormat = { (kotlin.math.round(it * 100f) / 100f).toString() + "x" },
        )
    }
}

@Composable
fun CodeBlock(code: String) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF07070A))
            .border(1.dp, ShowcaseColors.Border, shape),
    ) {
        Text(
            code,
            color = Color(0xFFD4D4D8),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
        )
    }
}
