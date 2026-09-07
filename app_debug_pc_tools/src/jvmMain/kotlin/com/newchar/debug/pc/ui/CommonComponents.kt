package com.newchar.debug.pc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppTheme {
    val background = Color(0xFFF5F6F8)
    val panel = Color(0xFFFFFFFF)
    val panelAlt = Color(0xFFF0F2F5)
    val border = Color(0xFFD7DCE3)
    val accent = Color(0xFF3566D6)
    val accentSoft = Color(0xFFE6EEFF)
    val textPrimary = Color(0xFF1F2430)
    val textSecondary = Color(0xFF5B6472)
    val textHint = Color(0xFF8A93A3)
    val danger = Color(0xFFC44536)
}

@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(fontSize = 14.sp, color = AppTheme.textPrimary),
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}

@Composable
fun AppButton(
    text: String,
    prefix: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AppButtonBase(
        text = text,
        prefix = prefix,
        modifier = modifier,
        backgroundColor = AppTheme.accent,
        contentColor = Color.White,
        onClick = onClick,
    )
}

@Composable
fun AppOutlinedButton(
    text: String,
    prefix: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable (() -> Unit)? = null,
) {
    if (content != null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, AppTheme.border, RoundedCornerShape(10.dp))
                .background(AppTheme.panel)
                .clickable(onClick = onClick)
                .padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    } else {
        AppButtonBase(
            text = text,
            prefix = prefix,
            modifier = modifier,
            backgroundColor = Color.Transparent,
            contentColor = AppTheme.accent,
            borderColor = AppTheme.border,
            onClick = onClick,
        )
    }
}

@Composable
private fun AppButtonBase(
    text: String,
    prefix: String?,
    modifier: Modifier,
    backgroundColor: Color,
    contentColor: Color,
    borderColor: Color? = null,
    onClick: () -> Unit,
) {
    val baseModifier = modifier
        .clip(RoundedCornerShape(10.dp))
        .then(
            if (borderColor != null) {
                Modifier.border(1.dp, borderColor, RoundedCornerShape(10.dp))
            } else {
                Modifier
            }
        )
        .background(backgroundColor)
        .clickable(onClick = onClick)
        .padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp))

    Box(
        modifier = baseModifier,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            prefix?.let {
                AppText(text = it, style = TextStyle(fontSize = 15.sp, color = contentColor))
            }
            AppText(
                text = text,
                style = TextStyle(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = contentColor),
            )
        }
    }
}

@Composable
fun PanelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, AppTheme.border, RoundedCornerShape(12.dp))
            .background(AppTheme.panel)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AppText(text = label, style = TextStyle(fontSize = 13.sp, color = AppTheme.textSecondary))
        AppText(text = value, style = TextStyle(fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), maxLines = 1)
    }
}

@Composable
fun AppDivider(modifier: Modifier = Modifier, color: Color = AppTheme.border) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}