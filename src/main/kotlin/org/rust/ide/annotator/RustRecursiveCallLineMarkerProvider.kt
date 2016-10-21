package org.rust.ide.annotator

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.Comparing
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.util.FunctionUtil
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.ref.RustReference
import java.util.*

/**
 * Line marker provider that annotates recursive funciton and method calls with
 * an icon on the gutter.
 */
class RustRecursiveCallLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement) = null

    override fun collectSlowLineMarkers(elements: MutableList<PsiElement>,
                                        result: MutableCollection<LineMarkerInfo<PsiElement>>) {
        val lines = HashSet<Int>()  // To prevent several markers on one line
        for (el in elements) {
            val isRecursive = when (el) {
                is RustCallExprElement       -> el.isRecursive(el.pathExpr()?.path?.reference)
                is RustMethodCallExprElement -> el.isRecursive(el.reference)
                else                         -> false
            }
            if (isRecursive) {
                val instance = PsiDocumentManager.getInstance(el.project)
                val doc = instance.getDocument(el.containingFile) ?: continue
                val lineNumber = doc.getLineNumber(el.textOffset)
                if (!lines.contains(lineNumber)) {
                    result.add(LineMarkerInfo(
                        el,
                        el.textRange,
                        RustIcons.RECURSIVE_CALL,
                        Pass.UPDATE_OVERRIDDEN_MARKERS,
                        FunctionUtil.constant("Recursive call"),
                        null,
                        GutterIconRenderer.Alignment.RIGHT))
                    lines.add(lineNumber)
                }
            }
        }
    }

    private fun RustCallExprElement.pathExpr() : RustPathExprElement? {
        val e = expr
        return if (e is RustPathExprElement) e else null
    }

    private fun RustExprElement.isRecursive(ref: RustReference?): Boolean {
        if (ref == null) return false
        val def = ref.resolve() ?: return false
        return (Comparing.equal(parentOfType<RustImplMethodMemberElement>(), def))  // Methods and associative functions
                || Comparing.equal(parentOfType<RustFnItemElement>(), def)          // Pure functions
    }
}
