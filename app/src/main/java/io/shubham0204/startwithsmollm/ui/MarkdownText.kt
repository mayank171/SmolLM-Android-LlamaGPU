package io.shubham0204.startwithsmollm.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
 * - LaTeX math expressions (converted to Unicode)
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
            // Pre-process: convert LaTeX to Unicode
            val processedText = convertLatexToUnicode(text)
            val blocks = parseMarkdownBlocks(processedText)
            
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
                    is MarkdownBlock.MathBlock -> {
                        MathBlockView(
                            math = block.content,
                            isBlock = block.isBlock
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
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
    ) {
        // Header with language and copy button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D2D))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language?.uppercase() ?: "CODE",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9CDCFE),
                fontFamily = FontFamily.Monospace
            )
            
            IconButton(
                onClick = { copyToClipboard(context, code.trimEnd(), "Code") },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    tint = Color(0xFF9CDCFE),
                    modifier = Modifier.size(16.dp)
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
private fun MathBlockView(
    math: String,
    isBlock: Boolean
) {
    val context = LocalContext.current
    val convertedMath = convertLatexToUnicode(math)
    
    if (isBlock) {
        // Display math block with header and copy button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF8F4FF))  // Light purple tint for math
        ) {
            // Header with math icon and copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEDE7F6))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Functions,
                        contentDescription = null,
                        tint = Color(0xFF7C4DFF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MATH",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF7C4DFF),
                        fontWeight = FontWeight.Medium
                    )
                }
                
                IconButton(
                    onClick = { copyToClipboard(context, convertedMath, "Math expression") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy math",
                        tint = Color(0xFF7C4DFF),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            // Math content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = convertedMath,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 28.sp,
                        fontFamily = FontFamily.Serif
                    ),
                    color = Color(0xFF1A1A1A)
                )
            }
        }
    } else {
        // Inline math - styled but no copy button
        Text(
            text = convertedMath,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.Serif,
                color = Color(0xFF5E35B1)
            )
        )
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
    data class MathBlock(val content: String, val isBlock: Boolean) : MarkdownBlock()
}

/**
 * Copy text to clipboard with toast feedback
 */
private fun copyToClipboard(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    var remainingText = text
    
    // Pattern for code blocks: ```language\ncode```
    val codeBlockRegex = Regex("```(\\w*)\\n([\\s\\S]*?)```")
    
    // Pattern for display math: $$...$$ or \[...\]
    val displayMathRegex = Regex("\\$\\$([^$]+)\\$\\$|\\\\\\[([\\s\\S]*?)\\\\\\]")
    
    // Pattern for inline math: $...$ (but not $$)
    val inlineMathRegex = Regex("(?<!\\$)\\$([^$]+)\\$(?!\\$)|\\\\\\(([^)]+)\\\\\\)")
    
    // Combined pattern to find all special blocks
    val combinedPattern = Regex("```(\\w*)\\n([\\s\\S]*?)```|\\$\\$([^$]+)\\$\\$|\\\\\\[([\\s\\S]*?)\\\\\\]")
    
    var currentIndex = 0
    val allMatches = combinedPattern.findAll(text).toList()
    
    if (allMatches.isEmpty()) {
        // Check for inline math in plain text
        if (text.isNotBlank()) {
            blocks.addAll(parseTextWithInlineMath(text))
        }
        return blocks
    }
    
    allMatches.forEach { match ->
        // Add text before this block
        if (match.range.first > currentIndex) {
            val textBefore = text.substring(currentIndex, match.range.first).trim()
            if (textBefore.isNotBlank()) {
                blocks.addAll(parseTextWithInlineMath(textBefore))
            }
        }
        
        val matchText = match.value
        when {
            // Code block
            matchText.startsWith("```") -> {
                val language = match.groupValues[1].ifBlank { null }
                val code = match.groupValues[2]
                blocks.add(MarkdownBlock.CodeBlock(code, language))
            }
            // Display math $$...$$ 
            matchText.startsWith("$$") -> {
                val math = match.groupValues[3]
                blocks.add(MarkdownBlock.MathBlock(math, isBlock = true))
            }
            // Display math \[...\]
            matchText.startsWith("\\[") -> {
                val math = match.groupValues[4]
                blocks.add(MarkdownBlock.MathBlock(math, isBlock = true))
            }
        }
        
        currentIndex = match.range.last + 1
    }
    
    // Add remaining text
    if (currentIndex < text.length) {
        val remainingTextPart = text.substring(currentIndex).trim()
        if (remainingTextPart.isNotBlank()) {
            blocks.addAll(parseTextWithInlineMath(remainingTextPart))
        }
    }
    
    return blocks
}

/**
 * Parse text that may contain inline math expressions
 */
private fun parseTextWithInlineMath(text: String): List<MarkdownBlock> {
    // For now, just return as text block - inline math is handled in convertLatexToUnicode
    return listOf(MarkdownBlock.TextBlock(text))
}

/**
 * Convert LaTeX math expressions to Unicode symbols for display
 * Handles common math notation like \exp, \frac, \sqrt, Greek letters, etc.
 */
private fun convertLatexToUnicode(text: String): String {
    var result = text
    
    // Greek letters (lowercase)
    val greekLower = mapOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\epsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η", "\\theta" to "θ",
        "\\iota" to "ι", "\\kappa" to "κ", "\\lambda" to "λ", "\\mu" to "μ",
        "\\nu" to "ν", "\\xi" to "ξ", "\\pi" to "π", "\\rho" to "ρ",
        "\\sigma" to "σ", "\\tau" to "τ", "\\upsilon" to "υ", "\\phi" to "φ",
        "\\chi" to "χ", "\\psi" to "ψ", "\\omega" to "ω", "\\varepsilon" to "ε",
        "\\varphi" to "φ", "\\varpi" to "ϖ", "\\varrho" to "ϱ", "\\varsigma" to "ς"
    )
    
    // Greek letters (uppercase)
    val greekUpper = mapOf(
        "\\Alpha" to "Α", "\\Beta" to "Β", "\\Gamma" to "Γ", "\\Delta" to "Δ",
        "\\Epsilon" to "Ε", "\\Zeta" to "Ζ", "\\Eta" to "Η", "\\Theta" to "Θ",
        "\\Iota" to "Ι", "\\Kappa" to "Κ", "\\Lambda" to "Λ", "\\Mu" to "Μ",
        "\\Nu" to "Ν", "\\Xi" to "Ξ", "\\Pi" to "Π", "\\Rho" to "Ρ",
        "\\Sigma" to "Σ", "\\Tau" to "Τ", "\\Upsilon" to "Υ", "\\Phi" to "Φ",
        "\\Chi" to "Χ", "\\Psi" to "Ψ", "\\Omega" to "Ω"
    )
    
    // Math operators and symbols
    val mathSymbols = mapOf(
        "\\times" to "×", "\\div" to "÷", "\\pm" to "±", "\\mp" to "∓",
        "\\cdot" to "·", "\\ast" to "∗", "\\star" to "⋆", "\\circ" to "∘",
        "\\bullet" to "•", "\\oplus" to "⊕", "\\otimes" to "⊗", "\\odot" to "⊙",
        "\\leq" to "≤", "\\geq" to "≥", "\\neq" to "≠", "\\approx" to "≈",
        "\\equiv" to "≡", "\\sim" to "∼", "\\simeq" to "≃", "\\cong" to "≅",
        "\\propto" to "∝", "\\ll" to "≪", "\\gg" to "≫",
        "\\subset" to "⊂", "\\supset" to "⊃", "\\subseteq" to "⊆", "\\supseteq" to "⊇",
        "\\in" to "∈", "\\notin" to "∉", "\\ni" to "∋", "\\cup" to "∪", "\\cap" to "∩",
        "\\emptyset" to "∅", "\\varnothing" to "∅",
        "\\forall" to "∀", "\\exists" to "∃", "\\nexists" to "∄",
        "\\neg" to "¬", "\\land" to "∧", "\\lor" to "∨", "\\wedge" to "∧", "\\vee" to "∨",
        "\\Rightarrow" to "⇒", "\\Leftarrow" to "⇐", "\\Leftrightarrow" to "⇔",
        "\\rightarrow" to "→", "\\leftarrow" to "←", "\\leftrightarrow" to "↔",
        "\\uparrow" to "↑", "\\downarrow" to "↓", "\\mapsto" to "↦",
        "\\infty" to "∞", "\\partial" to "∂", "\\nabla" to "∇",
        "\\sum" to "∑", "\\prod" to "∏", "\\int" to "∫", "\\oint" to "∮",
        "\\sqrt" to "√", "\\cbrt" to "∛",
        "\\angle" to "∠", "\\perp" to "⊥", "\\parallel" to "∥",
        "\\triangle" to "△", "\\square" to "□", "\\diamond" to "◇",
        "\\prime" to "′", "\\dprime" to "″",
        "\\ldots" to "…", "\\cdots" to "⋯", "\\vdots" to "⋮", "\\ddots" to "⋱",
        "\\hbar" to "ℏ", "\\ell" to "ℓ", "\\Re" to "ℜ", "\\Im" to "ℑ",
        "\\aleph" to "ℵ", "\\wp" to "℘"
    )
    
    // Math functions (render as text with proper formatting)
    val mathFunctions = mapOf(
        "\\exp" to "exp", "\\log" to "log", "\\ln" to "ln", "\\lg" to "lg",
        "\\sin" to "sin", "\\cos" to "cos", "\\tan" to "tan",
        "\\sec" to "sec", "\\csc" to "csc", "\\cot" to "cot",
        "\\arcsin" to "arcsin", "\\arccos" to "arccos", "\\arctan" to "arctan",
        "\\sinh" to "sinh", "\\cosh" to "cosh", "\\tanh" to "tanh",
        "\\lim" to "lim", "\\max" to "max", "\\min" to "min",
        "\\sup" to "sup", "\\inf" to "inf", "\\arg" to "arg",
        "\\det" to "det", "\\dim" to "dim", "\\ker" to "ker",
        "\\gcd" to "gcd", "\\lcm" to "lcm", "\\mod" to "mod"
    )
    
    // Superscripts
    val superscripts = mapOf(
        "0" to "⁰", "1" to "¹", "2" to "²", "3" to "³", "4" to "⁴",
        "5" to "⁵", "6" to "⁶", "7" to "⁷", "8" to "⁸", "9" to "⁹",
        "+" to "⁺", "-" to "⁻", "=" to "⁼", "(" to "⁽", ")" to "⁾",
        "n" to "ⁿ", "i" to "ⁱ", "x" to "ˣ", "y" to "ʸ"
    )
    
    // Subscripts
    val subscripts = mapOf(
        "0" to "₀", "1" to "₁", "2" to "₂", "3" to "₃", "4" to "₄",
        "5" to "₅", "6" to "₆", "7" to "₇", "8" to "₈", "9" to "₉",
        "+" to "₊", "-" to "₋", "=" to "₌", "(" to "₍", ")" to "₎",
        "a" to "ₐ", "e" to "ₑ", "i" to "ᵢ", "j" to "ⱼ", "k" to "ₖ",
        "n" to "ₙ", "o" to "ₒ", "p" to "ₚ", "r" to "ᵣ", "s" to "ₛ",
        "t" to "ₜ", "u" to "ᵤ", "v" to "ᵥ", "x" to "ₓ"
    )
    
    // Apply all replacements
    greekLower.forEach { (latex, unicode) -> result = result.replace(latex, unicode) }
    greekUpper.forEach { (latex, unicode) -> result = result.replace(latex, unicode) }
    mathSymbols.forEach { (latex, unicode) -> result = result.replace(latex, unicode) }
    mathFunctions.forEach { (latex, unicode) -> result = result.replace(latex, unicode) }
    
    // Handle fractions: \frac{a}{b} -> a/b or a⁄b
    result = Regex("""\\frac\{([^}]*)\}\{([^}]*)\}""").replace(result) { match ->
        val num = match.groupValues[1]
        val den = match.groupValues[2]
        "($num)/($den)"
    }
    
    // Handle square roots: \sqrt{x} -> √(x) or \sqrt[n]{x} -> ⁿ√(x)
    result = Regex("""\\sqrt\[([^\]]*)\]\{([^}]*)\}""").replace(result) { match ->
        val n = match.groupValues[1]
        val content = match.groupValues[2]
        "${n}√($content)"
    }
    result = Regex("""\\sqrt\{([^}]*)\}""").replace(result) { match ->
        "√(${match.groupValues[1]})"
    }
    
    // Handle superscripts: ^{...} -> superscript
    result = Regex("""\^\{([^}]*)\}""").replace(result) { match ->
        val content = match.groupValues[1]
        content.map { char -> superscripts[char.toString()] ?: char.toString() }.joinToString("")
    }
    // Simple superscript: ^x -> superscript (single char)
    result = Regex("""\^([0-9a-zA-Z])""").replace(result) { match ->
        val char = match.groupValues[1]
        superscripts[char] ?: "^$char"
    }
    
    // Handle subscripts: _{...} -> subscript
    result = Regex("""_\{([^}]*)\}""").replace(result) { match ->
        val content = match.groupValues[1]
        content.map { char -> subscripts[char.toString()] ?: char.toString() }.joinToString("")
    }
    // Simple subscript: _x -> subscript (single char)
    result = Regex("""_([0-9a-zA-Z])""").replace(result) { match ->
        val char = match.groupValues[1]
        subscripts[char] ?: "_$char"
    }
    
    // Handle text in math mode: \text{...} -> just the text
    result = Regex("""\\text\{([^}]*)\}""").replace(result) { match ->
        match.groupValues[1]
    }
    
    // Handle math delimiters: $...$ and $$...$$ (just remove them, content already processed)
    result = result.replace(Regex("""\$\$([^$]*)\$\$""")) { match -> match.groupValues[1] }
    result = result.replace(Regex("""\$([^$]*)\$""")) { match -> match.groupValues[1] }
    
    // Handle \[ ... \] and \( ... \) delimiters
    result = result.replace("\\[", "").replace("\\]", "")
    result = result.replace("\\(", "").replace("\\)", "")
    
    // Clean up remaining backslashes from unknown commands
    result = Regex("""\\([a-zA-Z]+)""").replace(result) { match ->
        match.groupValues[1]  // Just keep the command name without backslash
    }
    
    // Clean up extra braces
    result = result.replace("{", "").replace("}", "")
    
    return result
}
