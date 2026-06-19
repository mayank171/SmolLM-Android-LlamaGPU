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
 * Renders markdown-formatted assistant output with robust support for:
 *  - Fenced code blocks (```lang ... ```), including unclosed/streaming blocks
 *  - Inline code (`code`)
 *  - Display math: $$...$$, \[...\], and stand-alone equation lines
 *  - Inline math: $...$, \(...\), and obvious LaTeX fragments (e.g. \frac{}{})
 *  - Bold (**text**) / italic (*text*)
 *  - Headers (#, ##, ###)
 *  - Bullet (-) and numbered (1.) lists
 *
 * IMPORTANT: Code blocks are rendered verbatim. LaTeX-to-Unicode conversion is
 * only applied inside math segments so braces and backslashes in code survive.
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
                    is MarkdownBlock.CodeBlock -> CodeBlockView(
                        code = block.code,
                        language = block.language,
                        isStreaming = block.isStreaming
                    )
                    is MarkdownBlock.MathBlock -> MathBlockView(math = block.content)
                    is MarkdownBlock.TextBlock -> FormattedText(
                        text = block.content,
                        defaultColor = defaultColor
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Block rendering
// ---------------------------------------------------------------------------

@Composable
private fun CodeBlockView(
    code: String,
    language: String?,
    isStreaming: Boolean = false
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D2D))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = (language?.takeIf { it.isNotBlank() }?.uppercase() ?: "CODE") +
                        if (isStreaming) " · streaming…" else "",
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = code.trimEnd('\n'),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                ),
                color = Color(0xFFD4D4D4)
            )
        }
    }
}

@Composable
private fun MathBlockView(math: String) {
    val context = LocalContext.current
    val rendered = convertLatexToUnicode(math).trim()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D2D))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Functions,
                    contentDescription = null,
                    tint = Color(0xFF9CDCFE),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "MATH",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9CDCFE),
                    fontWeight = FontWeight.Medium
                )
            }
            IconButton(
                onClick = { copyToClipboard(context, rendered, "Math expression") },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy math",
                    tint = Color(0xFF9CDCFE),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rendered,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                    fontFamily = FontFamily.Serif
                ),
                color = Color(0xFFD4D4D4)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Inline text rendering (handles inline code, inline math, bold/italic, lists)
// ---------------------------------------------------------------------------

@Composable
private fun FormattedText(
    text: String,
    defaultColor: Color
) {
    val annotated = buildAnnotatedString {
        val content = text
        var i = 0
        val len = content.length
        fun atLineStart() = i == 0 || content[i - 1] == '\n'

        while (i < len) {
            val c = content[i]

            // Headers
            if (atLineStart() && content.startsWith("### ", i)) {
                val eol = content.indexOf('\n', i).let { if (it == -1) len else it }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                    append(content.substring(i + 4, eol))
                }
                append("\n"); i = eol + 1; continue
            }
            if (atLineStart() && content.startsWith("## ", i)) {
                val eol = content.indexOf('\n', i).let { if (it == -1) len else it }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)) {
                    append(content.substring(i + 3, eol))
                }
                append("\n"); i = eol + 1; continue
            }
            if (atLineStart() && content.startsWith("# ", i)) {
                val eol = content.indexOf('\n', i).let { if (it == -1) len else it }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
                    append(content.substring(i + 2, eol))
                }
                append("\n"); i = eol + 1; continue
            }

            // Bullet & numbered list markers
            if (atLineStart() && content.startsWith("- ", i)) {
                append("  •  "); i += 2; continue
            }
            if (atLineStart() && c.isDigit() && i + 2 < len &&
                content[i + 1] == '.' && content[i + 2] == ' '
            ) {
                append("  $c.  "); i += 3; continue
            }

            // Inline code: `...` (single backtick, not part of a fence)
            if (c == '`' && !content.startsWith("```", i)) {
                val end = content.indexOf('`', i + 1)
                if (end != -1) {
                    val codeText = content.substring(i + 1, end)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFF2D2D2D),
                            color = Color(0xFFCE9178),
                            fontSize = 14.sp
                        )
                    ) { append(" $codeText ") }
                    i = end + 1; continue
                }
            }

            // Inline math: $...$  (single, not $$) and \(...\)
            if (c == '$' && !content.startsWith("$$", i)) {
                val end = content.indexOf('$', i + 1)
                if (end != -1 && end - i > 1 && !content.startsWith("$$", end)) {
                    val math = content.substring(i + 1, end)
                    appendInlineMath(math)
                    i = end + 1; continue
                }
            }
            if (content.startsWith("\\(", i)) {
                val end = content.indexOf("\\)", i + 2)
                if (end != -1) {
                    appendInlineMath(content.substring(i + 2, end))
                    i = end + 2; continue
                }
            }

            // Bold **...**
            if (content.startsWith("**", i)) {
                val end = content.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(content.substring(i + 2, end))
                    }
                    i = end + 2; continue
                }
            }
            // Italic *...*
            if (c == '*' && !content.startsWith("**", i)) {
                val end = content.indexOf('*', i + 1)
                if (end != -1 && !content.startsWith("**", end)) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(content.substring(i + 1, end))
                    }
                    i = end + 1; continue
                }
            }

            append(c); i++
        }
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge,
        color = defaultColor
    )
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineMath(raw: String) {
    withStyle(
        SpanStyle(
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            color = Color(0xFF5E35B1),
            background = Color(0xFFF3E5F5)
        )
    ) {
        append(" ")
        append(convertLatexToUnicode(raw))
        append(" ")
    }
}

// ---------------------------------------------------------------------------
// Block parsing
// ---------------------------------------------------------------------------

private sealed class MarkdownBlock {
    data class CodeBlock(
        val code: String,
        val language: String?,
        val isStreaming: Boolean = false
    ) : MarkdownBlock()
    data class TextBlock(val content: String) : MarkdownBlock()
    data class MathBlock(val content: String) : MarkdownBlock()
}

/**
 * Top-level parser. Order of operations:
 *   1. Extract fenced code blocks (closed AND any trailing unclosed fence for streaming).
 *   2. From the remaining text, extract display math: $$...$$ and \[...\].
 *   3. From whatever is still text, promote whole lines that look like equations
 *      (contain LaTeX commands such as \frac, \sqrt, \sum, \int, or look like
 *      "A = B" with math operators/superscripts) to display math blocks.
 *   4. Keep the rest as TextBlock; inline math/code is handled by FormattedText.
 */
private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    if (text.isEmpty()) return emptyList()
    val out = mutableListOf<MarkdownBlock>()

    // 1) Fenced code blocks. Tolerant: optional language, optional newline.
    //    Closed:   ```lang\n...```
    //    Streaming (no closer yet, only ONE allowed and must be the last fence):
    //              ```lang\n...<EOF>
    val closedFence = Regex("```([A-Za-z0-9_+\\-]*)[ \\t]*\\r?\\n?([\\s\\S]*?)```")
    var cursor = 0
    val matches = closedFence.findAll(text).toList()
    for (m in matches) {
        if (m.range.first > cursor) {
            splitNonCode(text.substring(cursor, m.range.first), out)
        }
        val lang = m.groupValues[1].ifBlank { null }
        out.add(MarkdownBlock.CodeBlock(m.groupValues[2], lang))
        cursor = m.range.last + 1
    }
    val tail = text.substring(cursor)
    // Streaming: a lone unmatched ``` opens an in-progress code block until EOF.
    val openIdx = tail.indexOf("```")
    if (openIdx >= 0) {
        if (openIdx > 0) splitNonCode(tail.substring(0, openIdx), out)
        val after = tail.substring(openIdx + 3)
        val nl = after.indexOf('\n')
        val lang: String?
        val body: String
        if (nl >= 0) {
            lang = after.substring(0, nl).trim().ifBlank { null }
            body = after.substring(nl + 1)
        } else {
            lang = after.trim().ifBlank { null }
            body = ""
        }
        out.add(MarkdownBlock.CodeBlock(body, lang, isStreaming = true))
    } else if (tail.isNotEmpty()) {
        splitNonCode(tail, out)
    }
    return out
}

/** Pull display math and bare equations out of a non-code text region. */
private fun splitNonCode(text: String, out: MutableList<MarkdownBlock>) {
    if (text.isBlank()) {
        if (text.isNotEmpty()) out.add(MarkdownBlock.TextBlock(text))
        return
    }
    // Display math: $$...$$ or \[...\]
    val displayMath = Regex("\\$\\$([\\s\\S]+?)\\$\\$|\\\\\\[([\\s\\S]+?)\\\\\\]")
    var idx = 0
    for (m in displayMath.findAll(text)) {
        if (m.range.first > idx) {
            promoteEquationLines(text.substring(idx, m.range.first), out)
        }
        val math = m.groupValues[1].ifBlank { m.groupValues[2] }
        out.add(MarkdownBlock.MathBlock(math))
        idx = m.range.last + 1
    }
    if (idx < text.length) promoteEquationLines(text.substring(idx), out)
}

/**
 * Walk a region line-by-line: any line that "looks like math" (contains LaTeX
 * commands or matches an equation pattern with operators/superscripts) becomes
 * a MathBlock. Adjacent text lines are coalesced back into TextBlocks.
 */
private fun promoteEquationLines(region: String, out: MutableList<MarkdownBlock>) {
    if (region.isEmpty()) return
    val lines = region.split('\n')
    val buf = StringBuilder()
    fun flushText() {
        if (buf.isNotEmpty()) {
            val s = buf.toString()
            // Avoid emitting purely-empty text blocks
            if (s.isNotBlank()) out.add(MarkdownBlock.TextBlock(s.trimEnd('\n')))
            buf.setLength(0)
        }
    }
    for ((i, raw) in lines.withIndex()) {
        val line = raw.trim()
        val isEquationLine = line.isNotEmpty() && looksLikeEquation(line)
        if (isEquationLine) {
            flushText()
            out.add(MarkdownBlock.MathBlock(line))
        } else {
            buf.append(raw)
            if (i != lines.lastIndex) buf.append('\n')
        }
    }
    flushText()
}

/**
 * Heuristic: does this single line look like a stand-alone equation?
 *  - Contains a LaTeX command like \frac, \sqrt, \sum, \int, \lim, \mathbf, etc.
 *  - OR matches "<expr> = <expr>" where one side has math operators/superscripts
 *    (^, _, ², ³, *, /, +, -, √, π, Σ, Π, ∫, ∞) and the line is short-ish.
 *  - Must NOT look like prose (no sentence-ending punctuation followed by space-word,
 *    very few alphabetic words separated by spaces, etc.).
 */
private fun looksLikeEquation(line: String): Boolean {
    if (line.length > 200) return false
    // Strong signal: explicit LaTeX commands.
    val latexCmd = Regex("\\\\(frac|sqrt|sum|prod|int|oint|lim|infty|partial|nabla|" +
            "alpha|beta|gamma|delta|theta|lambda|mu|sigma|phi|psi|omega|pi|rho|tau|" +
            "leq|geq|neq|approx|times|cdot|pm|mp|in|notin|subset|forall|exists|" +
            "rightarrow|leftarrow|Rightarrow|mathbf|mathrm|mathcal|text)\\b")
    if (latexCmd.containsMatchIn(line)) return true

    // Equation-like: contains '=' with math-y characters and few prose markers.
    if ('=' in line) {
        val hasMathChar = line.any { ch ->
            ch in "^_²³⁴⁵⁶⁷⁸⁹⁰¹*/+√πΣΠ∫∞≤≥≠≈±×÷" ||
                ch == '\\'
        } || Regex("[A-Za-z]\\s*\\^\\s*[0-9A-Za-z{]").containsMatchIn(line) ||
            Regex("[A-Za-z]\\s*_\\s*[0-9A-Za-z{]").containsMatchIn(line)
        // Reject obvious prose: lots of words, ends with a period AND many spaces.
        val words = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val proseScore = words.count { it.length >= 3 && it.all { c -> c.isLetter() } }
        if (hasMathChar && proseScore <= 6) return true
    }
    return false
}

// ---------------------------------------------------------------------------
// LaTeX → Unicode (ONLY called inside math segments — never on code or prose)
// ---------------------------------------------------------------------------

private val GREEK_LOWER = mapOf(
    "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
    "\\epsilon" to "ε", "\\varepsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η",
    "\\theta" to "θ", "\\vartheta" to "ϑ", "\\iota" to "ι", "\\kappa" to "κ",
    "\\lambda" to "λ", "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ",
    "\\pi" to "π", "\\varpi" to "ϖ", "\\rho" to "ρ", "\\varrho" to "ϱ",
    "\\sigma" to "σ", "\\varsigma" to "ς", "\\tau" to "τ", "\\upsilon" to "υ",
    "\\phi" to "φ", "\\varphi" to "φ", "\\chi" to "χ", "\\psi" to "ψ", "\\omega" to "ω"
)
private val GREEK_UPPER = mapOf(
    "\\Gamma" to "Γ", "\\Delta" to "Δ", "\\Theta" to "Θ", "\\Lambda" to "Λ",
    "\\Xi" to "Ξ", "\\Pi" to "Π", "\\Sigma" to "Σ", "\\Upsilon" to "Υ",
    "\\Phi" to "Φ", "\\Psi" to "Ψ", "\\Omega" to "Ω"
)
private val SYMBOLS = mapOf(
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
    "\\rightarrow" to "→", "\\to" to "→", "\\leftarrow" to "←", "\\leftrightarrow" to "↔",
    "\\uparrow" to "↑", "\\downarrow" to "↓", "\\mapsto" to "↦",
    "\\infty" to "∞", "\\partial" to "∂", "\\nabla" to "∇",
    "\\sum" to "∑", "\\prod" to "∏", "\\int" to "∫", "\\oint" to "∮",
    "\\angle" to "∠", "\\perp" to "⊥", "\\parallel" to "∥",
    "\\triangle" to "△", "\\square" to "□", "\\diamond" to "◇",
    "\\prime" to "′", "\\ldots" to "…", "\\cdots" to "⋯",
    "\\hbar" to "ℏ", "\\ell" to "ℓ"
)
private val FUNCTIONS = listOf(
    "exp", "log", "ln", "lg", "sin", "cos", "tan", "sec", "csc", "cot",
    "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh",
    "lim", "max", "min", "sup", "inf", "arg", "det", "dim", "ker",
    "gcd", "lcm", "mod"
)
private val SUPER = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
    'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'y' to 'ʸ'
)
private val SUB = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
    '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
    '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
    'a' to 'ₐ', 'e' to 'ₑ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ',
    'n' to 'ₙ', 'o' to 'ₒ', 'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ',
    't' to 'ₜ', 'u' to 'ᵤ', 'v' to 'ᵥ', 'x' to 'ₓ'
)

private fun convertLatexToUnicode(input: String): String {
    var s = input
    // Strip math-mode delimiters if any survived ($, \(\), \[\])
    s = s.replace(Regex("^\\s*\\$\\$|\\$\\$\\s*$"), "")
    s = s.replace(Regex("^\\s*\\$|\\$\\s*$"), "")
    s = s.replace("\\(", "").replace("\\)", "")
    s = s.replace("\\[", "").replace("\\]", "")

    // \text{...} -> just text
    s = Regex("\\\\text\\{([^}]*)\\}").replace(s) { it.groupValues[1] }
    // \mathbf, \mathrm, \mathcal{...} -> contents
    s = Regex("\\\\math(?:bf|rm|cal|sf|it)\\{([^}]*)\\}").replace(s) { it.groupValues[1] }

    // \frac{a}{b} -> (a)/(b)
    s = Regex("\\\\frac\\s*\\{([^{}]*)\\}\\s*\\{([^{}]*)\\}").replace(s) {
        "(${it.groupValues[1]})/(${it.groupValues[2]})"
    }
    // \sqrt[n]{x} and \sqrt{x}
    s = Regex("\\\\sqrt\\s*\\[([^\\]]*)\\]\\s*\\{([^{}]*)\\}").replace(s) {
        "${it.groupValues[1]}√(${it.groupValues[2]})"
    }
    s = Regex("\\\\sqrt\\s*\\{([^{}]*)\\}").replace(s) { "√(${it.groupValues[1]})" }

    // Function names: \sin -> sin (do this BEFORE generic backslash cleanup)
    for (f in FUNCTIONS) s = s.replace("\\$f", f)

    // Greek + symbols
    GREEK_LOWER.forEach { (k, v) -> s = s.replace(k, v) }
    GREEK_UPPER.forEach { (k, v) -> s = s.replace(k, v) }
    SYMBOLS.forEach { (k, v) -> s = s.replace(k, v) }

    // Superscripts: ^{...}
    s = Regex("\\^\\{([^{}]*)\\}").replace(s) { m ->
        m.groupValues[1].map { SUPER[it] ?: it }.joinToString("")
    }
    // ^x (single)
    s = Regex("\\^([0-9A-Za-z+\\-=()])").replace(s) { m ->
        val ch = m.groupValues[1][0]
        SUPER[ch]?.toString() ?: "^$ch"
    }
    // Subscripts: _{...}
    s = Regex("_\\{([^{}]*)\\}").replace(s) { m ->
        m.groupValues[1].map { SUB[it] ?: it }.joinToString("")
    }
    s = Regex("_([0-9A-Za-z+\\-=()])").replace(s) { m ->
        val ch = m.groupValues[1][0]
        SUB[ch]?.toString() ?: "_$ch"
    }

    // Unknown remaining \command -> drop the slash, keep the word
    s = Regex("\\\\([A-Za-z]+)").replace(s) { it.groupValues[1] }
    // Drop standalone braces left over from LaTeX grouping
    s = s.replace("{", "").replace("}", "")
    // Collapse stray double spaces
    s = s.replace(Regex("[ \\t]{2,}"), " ")
    return s
}

// ---------------------------------------------------------------------------
// Clipboard helper
// ---------------------------------------------------------------------------

private fun copyToClipboard(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}
