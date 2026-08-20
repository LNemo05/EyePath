package org.walkguard.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.walkguard.app.ui.theme.AppleBlue
import org.walkguard.app.ui.theme.AppleBlueBorder
import org.walkguard.app.ui.theme.AppleBlueSoft
import org.walkguard.app.ui.theme.AppleCard
import org.walkguard.app.ui.theme.AppleChevron
import org.walkguard.app.ui.theme.AppleFill
import org.walkguard.app.ui.theme.AppleFillStrong
import org.walkguard.app.ui.theme.AppleGreen
import org.walkguard.app.ui.theme.AppleGreenSoft
import org.walkguard.app.ui.theme.AppleRed
import org.walkguard.app.ui.theme.AppleRedSoft
import org.walkguard.app.ui.theme.AppleSecondary
import org.walkguard.app.ui.theme.AppleSeparator
import org.walkguard.app.ui.theme.AppleTabBarBg
import org.walkguard.app.ui.theme.AppleText
import org.walkguard.app.ui.theme.AppleTertiary

val ScreenContentPadding = PaddingValues(
    start = 16.dp,
    end = 16.dp,
    top = 8.dp,
    bottom = 96.dp
)

private val CardShape = RoundedCornerShape(20.dp)
private val SoftTileShape = RoundedCornerShape(16.dp)
private val FieldShape = RoundedCornerShape(12.dp)
private val PillShape = RoundedCornerShape(999.dp)
private val TabBarShape = RoundedCornerShape(22.dp)

@Composable
fun LargeTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
        color = AppleText,
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    )
}

@Composable
fun ScreenSubtitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 4.dp).padding(bottom = 14.dp),
        color = AppleSecondary,
        fontSize = 15.sp,
        lineHeight = 21.sp
    )
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
        color = AppleSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.3.sp
    )
}

@Composable
fun CardTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = AppleSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp
    )
}

@Composable
fun FooterNote(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        color = AppleSecondary,
        fontSize = 12.5.sp,
        lineHeight = 17.sp
    )
}

@Composable
fun AppleCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp, shape = CardShape, ambientColor = Color(0x0F000000), spotColor = Color(0x14000000))
            .clip(CardShape),
        shape = CardShape,
        color = AppleCard
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = shape, ambientColor = Color(0x0F000000), spotColor = Color(0x14000000))
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.7f), shape),
        shape = shape,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0x24007AFF),
                            Color(0x1A5856D6),
                            Color(0xE6FFFFFF)
                        )
                    )
                )
                .padding(20.dp),
            content = content
        )
    }
}

@Composable
fun StatusBadge(
    text: String,
    ok: Boolean,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier
            .clip(PillShape)
            .background(if (ok) AppleGreenSoft else AppleRedSoft)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = if (ok) Color(0xFF0B7A2F) else Color(0xFFB42318),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1
    )
}

@Composable
fun StatusTile(
    label: String,
    value: String,
    valueColor: Color = AppleText,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(SoftTileShape)
            .background(AppleFill)
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Text(
            text = label,
            color = AppleSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

enum class ModeTone { Neutral, Selected }

@Composable
fun ModeOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background by animateColorAsState(
        targetValue = if (selected) AppleBlueSoft else AppleFill,
        label = "modeBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) AppleBlueBorder else Color.Transparent,
        label = "modeBorder"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SoftTileShape)
            .background(background)
            .border(1.5.dp, borderColor, SoftTileShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            color = AppleText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = description,
            color = AppleSecondary,
            fontSize = 12.5.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
fun PillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background by animateColorAsState(
        targetValue = if (selected) AppleBlue else AppleFill,
        label = "pillBg"
    )
    val content by animateColorAsState(
        targetValue = if (selected) Color.White else AppleText,
        label = "pillFg"
    )
    Text(
        text = text,
        modifier = modifier
            .clip(PillShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        color = content,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun ApplePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppleBlue,
            contentColor = Color.White,
            disabledContainerColor = AppleFill,
            disabledContentColor = AppleSecondary
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppleSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppleFill,
            contentColor = AppleText,
            disabledContainerColor = AppleFill,
            disabledContentColor = AppleTertiary
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .clip(FieldShape),
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        placeholder = {
            if (placeholder.isNotEmpty()) {
                Text(placeholder, color = AppleTertiary)
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = AppleFill,
            unfocusedContainerColor = AppleFill,
            disabledContainerColor = AppleFill,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = AppleBlue,
            focusedTextColor = AppleText,
            unfocusedTextColor = AppleText
        ),
        shape = FieldShape
    )
}

@Composable
fun GroupedList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    AppleCard(modifier = modifier, content = content)
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showDivider: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 0.5.dp,
                color = AppleSeparator
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = AppleText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = AppleSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            trailing?.invoke(this)
        }
    }
}

@Composable
fun Chevron(
    modifier: Modifier = Modifier
) {
    Text(
        text = "›",
        modifier = modifier,
        color = AppleChevron,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
fun SecondaryValue(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AppleSecondary
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun <T> PolicyDropdown(
    selected: T,
    options: List<T>,
    labelFor: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .widthIn(max = 108.dp)
                .clip(FieldShape)
                .background(if (expanded) AppleBlueSoft else AppleFill)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = labelFor(selected),
                color = if (expanded) AppleBlue else AppleText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (expanded) "▴" else "▾",
                color = if (expanded) AppleBlue else AppleSecondary,
                fontSize = 10.sp
            )
        }
        // Compose the heavy Popup/menu tree only while open so list rows stay cheap during scroll.
        if (expanded) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 168.dp),
                // Use Menu surface params only — stacking Modifier.background on top of the default
                // Material surface creates a double-layer look behind the popup.
                shape = RoundedCornerShape(16.dp),
                containerColor = AppleCard,
                tonalElevation = 0.dp,
                shadowElevation = 12.dp
            ) {
                options.forEach { option ->
                    val isSelected = option == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) AppleBlueSoft else Color.Transparent)
                            .clickable {
                                expanded = false
                                onSelected(option)
                            }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = labelFor(option),
                            color = if (isSelected) AppleBlue else AppleText,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        )
                        if (isSelected) {
                            Text(
                                text = "✓",
                                color = AppleBlue,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

data class FloatingTabItem(
    val key: Any,
    val label: String
)

@Composable
fun FloatingTextTabBar(
    tabs: List<FloatingTabItem>,
    selectedKey: Any,
    onTabSelected: (Any) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .shadow(
                elevation = 16.dp,
                shape = TabBarShape,
                ambientColor = Color(0x1F000000),
                spotColor = Color(0x1F000000)
            )
            .clip(TabBarShape)
            .border(1.dp, Color.White.copy(alpha = 0.9f), TabBarShape),
        shape = TabBarShape,
        color = AppleTabBarBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = tab.key == selectedKey
                val background by animateColorAsState(
                    targetValue = if (selected) AppleBlueSoft else Color.Transparent,
                    label = "tabBg"
                )
                val content by animateColorAsState(
                    targetValue = if (selected) AppleBlue else AppleSecondary,
                    label = "tabFg"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(background)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(tab.key) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.label,
                        color = content,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun MetricBarRow(
    label: String,
    value: Int,
    maxValue: Int,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val fraction = if (maxValue <= 0) 0f else (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            color = AppleSecondary,
            fontSize = 13.sp,
            modifier = Modifier.width(56.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(PillShape)
                .background(AppleFill)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(PillShape)
                    .background(barColor)
            )
        }
        Text(
            text = value.toString(),
            color = AppleSecondary,
            fontSize = 13.sp,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    AppleTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        singleLine = true
    )
}

@Composable
fun AppListDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 0.5.dp,
        color = AppleSeparator
    )
}

fun toneColor(good: Boolean?, warn: Boolean = false): Color = when {
    good == true -> AppleGreen
    warn -> org.walkguard.app.ui.theme.AppleOrange
    good == false -> AppleRed
    else -> AppleText
}
