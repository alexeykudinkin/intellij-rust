/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.*

class ConvertToUnsuffixedIntegerFix private constructor(element: RsLitExpr, private val textTemplate: String): LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getFamilyName(): String = "Convert to unsuffixed integer"

    override fun getText(): String {
        return String.format(textTemplate, convertToUnsuffixedInteger(myStartElement.element))
    }


    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val integer = convertToUnsuffixedInteger(startElement) ?: return
        val psiFactory = RsPsiFactory(project)
        startElement.replace(psiFactory.createExpression(integer))
    }


    companion object {
        fun createIfCompatible(element: RsLitExpr, textTemplate: String): ConvertToUnsuffixedIntegerFix? {
            if (convertToUnsuffixedInteger(element) != null) {
                return ConvertToUnsuffixedIntegerFix(element, textTemplate)
            }
            return null
        }

        private fun convertToUnsuffixedInteger(element: PsiElement?): String? {
            if (element == null) return null
            if (element !is RsLitExpr) return null

            val value = when (val kind = element.kind) {
                is RsLiteralKind.Integer -> kind.value
                is RsLiteralKind.Boolean -> null
                is RsLiteralKind.Float -> kind.value?.toLong()
                is RsLiteralKind.String -> kind.value?.toLongOrNull()
                is RsLiteralKind.Char -> kind.value?.toLongOrNull()
                null -> null
            } ?: return null

            return value.toString()
        }
    }
}
