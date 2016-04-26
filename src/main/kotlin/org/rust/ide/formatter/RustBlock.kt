package org.rust.ide.formatter

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.psi.RustCompositeElementTypes.*
import org.rust.lang.core.psi.RustTokenElementTypes.*

class RustBlock(
    node: ASTNode,
    alignment: Alignment?,
    indent: Indent?,
    wrap: Wrap?,
    ctx: RustBlockContext
) : AbstractRustBlock(node, alignment, indent, wrap, ctx) {

    override fun getChildIndent(): Indent? = when (node.elementType) {
        in BLOCKS_TOKEN_SET -> Indent.getNormalIndent()
        else                -> Indent.getNoneIndent()
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        return ctx.spacingBuilder.getSpacing(this, child1, child2)
    }

    override fun buildChildren(): List<Block> {
        val anchor = when (node.elementType) {
            ARG_LIST -> Alignment.createAlignment()
            else     -> null
        }

        return node.getChildren(null)
            .filter { it.textLength > 0 && it.elementType != WHITE_SPACE }
            .map { buildChild(it, anchor) }
    }

    private fun buildChild(child: ASTNode, anchor: Alignment?): AbstractRustBlock =
        AbstractRustBlock.createBlock(child, calcAlignment(child, anchor), calcIndent(child), null, ctx)

    private fun calcAlignment(child: ASTNode, anchor: Alignment?): Alignment? =
        when (child.elementType) {
            in BRACES_TOKEN_SET -> null
            else                -> anchor
        }

    private fun calcIndent(child: ASTNode): Indent {
        val parentType = node.elementType
        val childType = child.elementType
        return when (parentType) {
            in BLOCKS_TOKEN_SET ->
                when (childType) {
                    in BLOCK_START_TOKEN_SET,
                    in BRACES_TOKEN_SET -> Indent.getNoneIndent()
                    else                -> Indent.getNormalIndent()
                }

            else                -> Indent.getNoneIndent()
        }
    }

    companion object {
        private val BLOCK_START_TOKEN_SET = TokenSet.create(
            PUB,
            MOD,
            STRUCT,
            ENUM,
            IMPL,
            TRAIT,
            MATCH
        )

        private val BLOCKS_TOKEN_SET = TokenSet.create(
            BLOCK,
            MOD_ITEM,
            FOREIGN_MOD_BODY,
            ENUM_BODY,
            STRUCT_DECL_ARGS,
            ARG_LIST,
            STRUCT_EXPR_BODY,
            IMPL_BODY,
            MATCH_BODY,
            TRAIT_BODY
        )

        private val BRACES_TOKEN_SET = TokenSet.create(
            LBRACE, RBRACE,
            LPAREN, RPAREN
        )
    }
}
