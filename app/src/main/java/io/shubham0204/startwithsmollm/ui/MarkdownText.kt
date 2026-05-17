package io.shubham0204.startwithsmollm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders markdown-formatted text with support for:
 * - Code blocks (```language ... ```)
 * - Inline code (`code`)
 * - Bold (**text**)
 * - Italic (*text*)
 * - Headers (# ## ###)
 * - Bullet lists (- item)
 * - Numbered lists (1. item)
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    defaultColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val blocks = parseMarkdownBlocks(text)
            
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.CodeBlock -> {
                        CodeBlockView(
                            code = block.code,
                            language = block.language
                        )
                    }
                    is MarkdownBlock.TextBlock -> {
                        FormattedText(
                            text = block.content,
                            defaultColor = defaultColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockView(
    code: String,
    language: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
    ) {
        // Language header
        if (!language.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D2D2D))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9CDCFE),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        
        // Code content with horizontal scroll
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = code.trimEnd(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                ),
                color = Color(0xFFD4D4D4)
            )
        }
    }
}

@Composable
private fun FormattedText(
    text: String,
    defaultColor: Color
) {
    val annotatedString = buildAnnotatedString {
        var currentIndex = 0
        val content = text
        
        while (currentIndex < content.length) {
            when {
                // Headers
                content.startsWith("### ", currentIndex) && (currentIndex == 0 || content[currentIndex - 1] == '\n') -> {
                    val endOfLine = content.indexOf('\n', currentIndex).takeIf { it != -1 } ?: content.length
                    val headerText = content.substring(currentIndex + 4, endOfLine)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                        append(headerText)
                    }
                    append("\n")
                    currentIndex = endOfLine + 1
                }
                content.startsWith("## ", currentIndex) && (currentIndex == 0 || content[currentIndex - 1] == '\n') -> {
                    val endOfLine = content.indexOf('\n', currentIndex).takeIf { it != -1 } ?: content.length
                    val headerText = content.substring(currentIndex + 3, endOfLine)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)) {
                        append(headerText)
                    }
                    append("\n")
                    currentIndex = endOfLine + 1
                }
                content.startsWith("# ", currentIndex) && (currentIndex == 0 || content[currentIndex - 1] == '\n') -> {
                    val endOfLine = content.indexOf('\n', currentIndex).takeIf { it != -1 } ?: content.length
                    val headerText = content.substring(currentIndex + 2, endOfLine)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
                        append(headerText)
                    }
                    append("\n")
                    currentIndex = endOfLine + 1
                }
                // Inline code
                content.startsWith("`", currentIndex) && !content.startsWith("```", currentIndex) -> {
                    val endTick = content.indexOf('`', currentIndex + 1)
                    if (endTick != -1) {
                        val codeText = content.substring(currentIndex + 1, endTick)
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFF2D2D2D),
                            color = Color(0xFFCE9178)
                        )) {
                            append(" $codeText ")
                        }
                        currentIndex = endTick + 1
                    } else {
                        append(content[currentIndex])
                        currentIndex++
                    }
                }
                // Bold
                content.startsWith("**", currentIndex) -> {
                    val endBold = content.indexOf("**", currentIndex + 2)
                    if (endBold != -1) {
                        val boldText = content.substring(currentIndex + 2, endBold)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(boldText)
                        }
                        currentIndex = endBold + 2
                    } else {
                        append(content[currentIndex])
                        currentIndex++
                    }
                }
                // Italic
                content.startsWith("*", currentIndex) && !content.startsWith("**", currentIndex) -> {
                    val endItalic = content.indexOf('*', currentIndex + 1)
                    if (endItalic != -1 && !content.startsWith("**", endItalic)) {
                        val italicText = content.substring(currentIndex + 1, endItalic)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(italicText)
                        }
                        currentIndex = endItalic + 1
                    } else {
                        append(content[currentIndex])
                        currentIndex++
                    }
                }
                // Bullet points
                content.startsWith("- ", currentIndex) && (currentIndex == 0 || content[currentIndex - 1] == '\n') -> {
                    append("  •  ")
                    currentIndex += 2
                }
                // Numbered lists
                content[currentIndex].isDigit() && currentIndex + 2 < content.length && 
                        content[currentIndex + 1] == '.' && content[currentIndex + 2] == ' ' &&
                        (currentIndex == 0 || content[currentIndex - 1] == '\n') -> {
                    append("  ${content[currentIndex]}.  ")
                    currentIndex += 3
                }
                else -> {
                    append(content[currentIndex])
                    currentIndex++
                }
            }
        }
    }
    
    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge,
        color = defaultColor
    )
}

private sealed class MarkdownBlock {
    data class CodeBlock(val code: String, val language: String?) : MarkdownBlock()
    data class TextBlock(val content: String) : MarkdownBlock()
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    var currentIndex = 0
    val codeBlockRegex = Regex("```(\\w*)\\n([\\s\\S]*?)```")
    
    val matches = codeBlockRegex.findAll(text).toList()
    
    if (matches.isEmpty()) {
        if (text.isNotBlank()) {
            blocks.add(MarkdownBlock.TextBlock(text))
        }
        return blocks
    }
    
    matches.forEach { match ->
        // Add text before code block
        if (match.range.first > currentIndex) {
            val textBefore = text.substring(currentIndex, match.range.first).trim()
            if (textBefore.isNotBlank()) {
                blocks.add(MarkdownBlock.TextBlock(textBefore))
            }
        }
        
        // Add code block
        val language = match.groupValues[1].ifBlank { null }
        val code = match.groupValues[2]
        blocks.add(MarkdownBlock.CodeBlock(code, language))
        
        currentIndex = match.range.last + 1
    }
    
    // Add remaining text after last code block
    if (currentIndex < text.length) {
        val remainingText = text.substring(currentIndex).trim()
        if (remainingText.isNotBlank()) {
            blocks.add(MarkdownBlock.TextBlock(remainingText))
        }
    }
    
    return blocks
}
