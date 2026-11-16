package com.toplevel.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toplevel.app.R
import com.toplevel.app.data.Stat

/** "System window" palette: near-black blue, electric cyan glow. */
object C {
    val bg = Color(0xFF02050C)
    val bg2 = Color(0xFF050D1E)
    val panel = Color(0xFF061022)
    val panel2 = Color(0xFF0A1A36)
    val border = Color(0xFF1B4E8F)
    val accent = Color(0xFF2CC4FF)
    val accentSoft = Color(0xFFA6EBFF)
    val glow = Color(0xFF1E9BFF)
    val text = Color(0xFFE4F3FF)
    val muted = Color(0xFF6E89AD)
    val good = Color(0xFF3DF2B0)
    val warn = Color(0xFFFFC14D)
    val bad = Color(0xFFFF4D6D)
    val shadow = Color(0xFF8C5CFF) // purple, for rare / high-rank things

    fun stat(s: Stat) = when (s) {
        Stat.STRENGTH -> Color(0xFFFF5C6C)
        Stat.VITALITY -> Color(0xFF3DF2B0)
        Stat.INTELLECT -> Color(0xFF2CC4FF)
        Stat.DISCIPLINE -> Color(0xFFFFC14D)
        Stat.SPIRIT -> Color(0xFFB48CFF)
    }

    fun rank(r: String) = when (r) {
        "S" -> shadow
        "A" -> bad
        "B" -> warn
        "C" -> good
        else -> accent
    }
}

object Fonts {
    /** Body/UI: narrow, techy sans. */
    val body = FontFamily(
        Font(R.font.rajdhani_medium, FontWeight.Normal),
        Font(R.font.rajdhani_medium, FontWeight.Medium),
        Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
        Font(R.font.rajdhani_bold, FontWeight.Bold),
        Font(R.font.rajdhani_bold, FontWeight.ExtraBold),
        Font(R.font.rajdhani_bold, FontWeight.Black),
    )

    /** Labels and numbers. Same family, used where the old code used monospace. */
    val tech = body

    /** Big titles and level numbers: wide geometric display face (variable weight). */
    @OptIn(ExperimentalTextApi::class)
    val display = FontFamily(
        listOf(400, 500, 600, 700, 800, 900).map { w ->
            Font(R.font.orbitron, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
        }
    )
}

private fun typography(): Typography {
    val b = Typography()
    fun TextStyle.f() = copy(fontFamily = Fonts.body)
    return Typography(
        displayLarge = b.displayLarge.copy(fontFamily = Fonts.display),
        displayMedium = b.displayMedium.copy(fontFamily = Fonts.display),
        displaySmall = b.displaySmall.copy(fontFamily = Fonts.display),
        headlineLarge = b.headlineLarge.copy(fontFamily = Fonts.display),
        headlineMedium = b.headlineMedium.f(),
        headlineSmall = b.headlineSmall.f(),
        titleLarge = b.titleLarge.copy(fontFamily = Fonts.body, fontWeight = FontWeight.Bold),
        titleMedium = b.titleMedium.f(),
        titleSmall = b.titleSmall.f(),
        bodyLarge = b.bodyLarge.copy(fontFamily = Fonts.body, fontWeight = FontWeight.Medium, fontSize = 16.sp),
        bodyMedium = b.bodyMedium.copy(fontFamily = Fonts.body, fontWeight = FontWeight.Medium, fontSize = 15.sp),
        bodySmall = b.bodySmall.f(),
        labelLarge = b.labelLarge.copy(fontFamily = Fonts.body, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        labelMedium = b.labelMedium.f(),
        labelSmall = b.labelSmall.f(),
    )
}

@Composable
fun TopLevelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.accent,
            onPrimary = Color.Black,
            secondary = C.accentSoft,
            background = C.bg,
            surface = C.panel,
            surfaceVariant = C.panel2,
            surfaceContainerHigh = C.panel,
            surfaceContainer = C.panel,
            onBackground = C.text,
            onSurface = C.text,
            onSurfaceVariant = C.muted,
            outline = C.border,
        ),
        typography = typography(),
        content = content,
    )
}

/** The window shape: rectangle with small clipped corners. */
val WindowShape = CutCornerShape(10.dp)
val SmallShape = CutCornerShape(6.dp)

/** Draws a glowing outline (border + soft light around it) along [shape]. */
fun Modifier.glow(
    color: Color = C.accent,
    shape: Shape = WindowShape,
    radius: Dp = 10.dp,
    strength: Float = 0.7f,
    width: Dp = 1.dp,
): Modifier = drawWithContent {
    drawContent()
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }.asAndroidPath()
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = width.toPx()
        this.color = color.toArgb()
        if (radius > 0.dp) setShadowLayer(radius.toPx(), 0f, 0f, color.copy(alpha = strength).toArgb())
    }
    drawIntoCanvas { it.nativeCanvas.drawPath(path, paint) }
}

/** Full-screen backdrop: deep gradient, faint grid, glow at the top. */
@Composable
fun SystemBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(C.bg2, C.bg, C.bg)))
        drawRect(
            Brush.radialGradient(
                listOf(C.glow.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width * 0.5f, 0f),
                radius = size.maxDimension * 0.7f,
            )
        )
        val step = 28.dp.toPx()
        val line = C.accent.copy(alpha = 0.035f)
        var x = 0f
        while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = 0f
        while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), 1f); y += step }
    }
}

/** Glowing text style for headers and big numbers (keeps the theme font). */
@Composable
fun glowStyle(color: Color = C.accent, blur: Float = 18f): TextStyle =
    androidx.compose.material3.LocalTextStyle.current.copy(
        shadow = Shadow(color = color.copy(alpha = 0.85f), offset = Offset.Zero, blurRadius = blur)
    )

/** The glowing "system window" panel. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    color: Color = C.accent,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .glow(color.copy(alpha = 0.75f), radius = 8.dp, strength = 0.45f)
            .clip(WindowShape)
            .background(Brush.verticalGradient(listOf(C.panel2.copy(alpha = 0.92f), C.panel.copy(alpha = 0.92f))))
            .padding(16.dp)
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("◆", color = color, fontSize = 10.sp, style = glowStyle(color, 10f))
                Spacer(Modifier.width(8.dp))
                Text(
                    title.uppercase(),
                    color = color,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Fonts.tech,
                    letterSpacing = 2.5.sp,
                    modifier = Modifier.weight(1f),
                )
                action?.invoke()
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().height(1.dp)
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), Color.Transparent)))
            )
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Int = 6) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(C.bg.copy(alpha = 0.8f))
            .glow(color.copy(alpha = 0.35f), shape = androidx.compose.ui.graphics.RectangleShape, radius = 0.dp)
    ) {
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) Box(
            Modifier
                .fillMaxWidth(f)
                .height(height.dp)
                .glow(color, shape = androidx.compose.ui.graphics.RectangleShape, radius = 6.dp, strength = 0.8f, width = 0.dp)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color)))
        )
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    singleLine: Boolean = false,
    placeholder: String? = null,
    password: Boolean = false,
    voice: Boolean = false,
    keyboard: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) { { Text(placeholder, color = C.muted, fontSize = 14.sp) } } else null,
        modifier = modifier.fillMaxWidth(),
        minLines = minLines,
        singleLine = singleLine,
        shape = SmallShape,
        keyboardOptions = keyboard,
        textStyle = MaterialTheme.typography.bodyLarge,
        trailingIcon = if (voice) { { VoiceButton { t -> onChange(if (value.isBlank()) t else "$value $t") } } } else null,
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.accent,
            unfocusedBorderColor = C.border,
            focusedLabelColor = C.accent,
            cursorColor = C.accent,
            focusedContainerColor = C.bg.copy(alpha = 0.6f),
            unfocusedContainerColor = C.bg.copy(alpha = 0.4f),
        ),
    )
}

/** Outlined glowing button with clipped corners. */
@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = C.accent,
    onClick: () -> Unit,
) {
    val c = if (enabled) color else C.border
    Box(
        modifier
            .glow(c, SmallShape, radius = if (enabled) 6.dp else 0.dp, strength = 0.5f)
            .clip(SmallShape)
            .background(c.copy(alpha = if (enabled) 0.10f else 0.04f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), color = if (enabled) color else C.muted, fontWeight = FontWeight.Bold,
            fontSize = 14.sp, letterSpacing = 1.5.sp)
    }
}

/** Solid filled button for the main action on a page. */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .glow(if (enabled) C.accent else C.border, SmallShape, radius = if (enabled) 14.dp else 0.dp, strength = 0.8f)
            .clip(SmallShape)
            .background(if (enabled) Brush.horizontalGradient(listOf(C.glow, C.accent)) else Brush.horizontalGradient(listOf(C.panel2, C.panel2)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), color = if (enabled) C.bg else C.muted, fontWeight = FontWeight.Bold,
            fontSize = 16.sp, letterSpacing = 2.sp)
    }
}

@Composable
fun Chip(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = Fonts.tech,
        letterSpacing = 1.sp,
        modifier = Modifier
            .glow(color.copy(alpha = 0.6f), SmallShape, radius = 0.dp)
            .clip(SmallShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** Small diamond bullet used in lists. */
@Composable
fun Diamond(color: Color = C.accent, size: Dp = 8.dp) {
    Box(
        Modifier.size(size).clip(CutCornerShape(50))
            .background(color)
    )
}

val Gap = Arrangement.spacedBy(14.dp)

/** Simple tap modifier. */
fun Modifier.tap(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

/** Mic button: speech-to-text via the phone's speech recognizer. */
@Composable
fun VoiceButton(onText: (String) -> Unit) {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val t = res.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!t.isNullOrBlank()) onText(t)
    }
    androidx.compose.material3.IconButton(onClick = {
        try {
            launcher.launch(
                android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            )
        } catch (_: Exception) { }
    }) { Text("🎙", fontSize = 18.sp) }
}

/** File types accepted when importing a plan (Excel / CSV / text). */
val planMimeTypes = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel", "text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream",
)
