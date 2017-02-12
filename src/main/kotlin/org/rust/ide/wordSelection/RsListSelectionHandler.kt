package org.rust.ide.wordSelection

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.psi.*

class RsListSelectionHandler : ExtendWordSelectionHandlerBase() {
    override fun canSelect(e: PsiElement): Boolean =
        e is RsTypeArgumentList || e is RsValueArgumentList || e is RsTypeParameterList || e is RsValueParameterList

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>? {
        val node = e.node!!
        val startNode = node.findChildByType(TokenSet.create(RsElementTypes.LPAREN,RsElementTypes.LT)) ?: return null
        val endNode = node.findChildByType(TokenSet.create(RsElementTypes.RPAREN,RsElementTypes.GT)) ?: return null
        val range = TextRange(startNode.startOffset + 1, endNode.startOffset)
        return listOf(range)
    }
}
