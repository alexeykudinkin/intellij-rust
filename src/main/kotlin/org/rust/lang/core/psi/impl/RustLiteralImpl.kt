package org.rust.lang.core.psi.impl

import com.intellij.lexer.Lexer
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.lexer.RustLiteralLexer
import org.rust.lang.core.psi.LiteralTokenTypes
import org.rust.lang.core.psi.RustLiteral
import org.rust.lang.core.psi.RustTokenElementTypes
import org.rust.lang.utils.unescapeRust

private val VALID_INTEGER_SUFFIXES = listOf("u8", "i8", "u16", "i16", "u32", "i32", "u64", "i64", "isize", "usize")
private val VALID_FLOAT_SUFFIXES = listOf("f32", "f64")

class RustLiteralImpl(type: IElementType, text: CharSequence) : LeafPsiElement(type, text), RustLiteral {
    override val tokenType: IElementType
        get() = node.elementType

    override val value: Any?
        get() = TODO() // TODO: Implement this.

    override val valueString: String
        get() = RustLiteralLexer.of(tokenType).findToken(text, LiteralTokenTypes.VALUE)?.unescapeRust() ?: ""

    override val suffix: String
        get() = RustLiteralLexer.of(tokenType).findToken(text, LiteralTokenTypes.SUFFIX) ?: ""

    override val possibleSuffixes: Collection<String>
        get() = when (tokenType) {
            RustTokenElementTypes.INTEGER_LITERAL -> VALID_INTEGER_SUFFIXES
            RustTokenElementTypes.FLOAT_LITERAL   -> VALID_FLOAT_SUFFIXES
            else                                  -> emptyList()
        }

    override val hasPairedDelimiters: Boolean
        get() {
            val delimCount = RustLiteralLexer.of(tokenType).countToken(text, LiteralTokenTypes.DELIMITER)
            return delimCount == 0 || delimCount == 2
        }

    override fun toString(): String = "RustLiteralImpl($tokenType)"
}

private fun Lexer.findToken(buffer: CharSequence, tokenType: IElementType): String? {
    var tt = this.tokenType

    start(buffer)
    while (tt != null) {
        if (tt == tokenType) {
            return tokenText
        }
        advance()
        tt = this.tokenType
    }

    return null
}

private fun Lexer.countToken(buffer: CharSequence, tokenType: IElementType): Int {
    var i = 0
    var tt = this.tokenType

    start(buffer)
    while (tt != null) {
        if (tt == tokenType) {
            i++
        }
        advance()
        tt = this.tokenType
    }
    return i
}
