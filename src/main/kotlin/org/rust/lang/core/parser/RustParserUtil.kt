package org.rust.lang.core.parser

import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilderUtil
import com.intellij.lang.WhitespacesBinders
import com.intellij.openapi.util.Key
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.psi.RustCompositeElementTypes
import org.rust.lang.core.psi.RustTokenElementTypes.*

@Suppress("UNUSED_PARAMETER")
object RustParserUtil : GeneratedParserUtilBase() {
    enum class PathParsingMode { COLONS, NO_COLONS, NO_TYPES_ALLOWED }

    private val STRUCT_ALLOWED: Key<Boolean> = Key("org.rust.STRUCT_ALLOWED")
    private val PATH_PARSING_MODE: Key<PathParsingMode> = Key("org.rust.PATH_PARSING_MODE")

    private val PsiBuilder.structAllowed: Boolean get() = getUserData(STRUCT_ALLOWED) ?: true

    private val PsiBuilder.pathParsingMode: PathParsingMode get() = requireNotNull(getUserData(PATH_PARSING_MODE)) {
        "Path context is not set. Be sure to call one of `withParsingMode...` functions"
    }

    @JvmField val DOC_COMMENT_BINDER = WhitespacesBinders.leadingCommentsBinder(
        TokenSet.create(OUTER_BLOCK_DOC_COMMENT, OUTER_EOL_DOC_COMMENT))

    //
    // Helpers
    //

    // Parses either a paren_expr (92) or a tuple_expr (92, ) by postponing the decision of
    // what exactly is parsed until `,` is (not) seen.
    @JvmStatic fun tupleOrParenExpr(builder: PsiBuilder, level: Int,
                                    anyExpr: Parser,
                                    tupleExprEnd: Parser,
                                    parenExprEnd: Parser): Boolean {
        if (!recursion_guard_(builder, level, "tupleOrParenExpr")) return false
        if (!nextTokenIsFast(builder, LPAREN)) return false
        val marker = enter_section_(builder)
        var result = consumeTokenFast(builder, LPAREN)
        result = result && anyExpr.parse(builder, level + 1)

        var hasComma = false
        result = result && if (nextTokenIsFast(builder, COMMA)) {
            hasComma = true
            tupleExprEnd.parse(builder, level + 1)
        } else {
            parenExprEnd.parse(builder, level + 1)
        }
        val elementType = if (hasComma) RustCompositeElementTypes.TUPLE_EXPR else RustCompositeElementTypes.PAREN_EXPR

        exit_section_(builder, marker, elementType, result)
        return result
    }

    @JvmStatic fun checkStructAllowed(b: PsiBuilder, level: Int): Boolean = b.structAllowed

    @JvmStatic fun checkBraceAllowed(b: PsiBuilder, level: Int): Boolean {
        return b.structAllowed || b.tokenType != LBRACE
    }

    @JvmStatic fun withoutStructLiterals(b: PsiBuilder, level: Int, parser: Parser): Boolean =
        b.withContext(STRUCT_ALLOWED, false) { parser.parse(this, level) }

    @JvmStatic fun withStructLiterals(b: PsiBuilder, level: Int, parser: Parser): Boolean =
        b.withContext(STRUCT_ALLOWED, true) { parser.parse(this, level) }

    @JvmStatic fun withPathModeNoColons(b: PsiBuilder, level: Int, parser: Parser): Boolean =
        b.withContext(PATH_PARSING_MODE, PathParsingMode.NO_COLONS) { parser.parse(this, level) }

    @JvmStatic fun withPathModeColons(b: PsiBuilder, level: Int, parser: Parser): Boolean =
        b.withContext(PATH_PARSING_MODE, PathParsingMode.COLONS) { parser.parse(this, level) }

    @JvmStatic fun withPathModeNoTypes(b: PsiBuilder, level: Int, parser: Parser): Boolean =
        b.withContext(PATH_PARSING_MODE, PathParsingMode.NO_TYPES_ALLOWED) { parser.parse(this, level) }

    @JvmStatic fun isPathModeColons(b: PsiBuilder, level: Int): Boolean = b.pathParsingMode == PathParsingMode.COLONS
    @JvmStatic fun isPathModeNoColons(b: PsiBuilder, level: Int): Boolean = b.pathParsingMode == PathParsingMode.NO_COLONS

    @JvmStatic fun unpairedToken(b: PsiBuilder, level: Int): Boolean =
        when (b.tokenType) {
            LBRACE, RBRACE -> false
            LPAREN, RPAREN -> false
            LBRACK, RBRACK -> false
            else           -> {
                b.advanceLexer()
                true
            }
        }

    @JvmStatic fun collapse(b: PsiBuilder, level: Int, tokenType: IElementType, vararg parts: IElementType): Boolean {
        // We do not want whitespace between parts, so firstly we do raw lookup for each part,
        // and when we make sure that we have desired token, we consume and collapse it.
        parts.forEachIndexed { i, tt ->
            if (b.rawLookup(i) != tt) return false
        }
        val marker = b.mark()
        PsiBuilderUtil.advance(b, parts.size)
        marker.collapse(tokenType)
        return true
    }

    private fun<T> PsiBuilder.withContext(key: Key<T>, value: T, block: PsiBuilder.() -> Boolean): Boolean {
        val old = getUserData(key)
        putUserData(key, value)
        val result = block()
        putUserData(key, old)
        return result
    }

}

