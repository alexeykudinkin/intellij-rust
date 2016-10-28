package org.rust.ide.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustFnItemElement
import org.rust.lang.core.psi.RustGenericParamsElement
import org.rust.lang.core.psi.util.parentOfType

class MoveTypeConstraintToWhereClauseIntention : PsiElementBaseIntentionAction() {
    override fun getText() = "Move type constraint to where clause"
    override fun getFamilyName() = text
    override fun startInWriteAction() = true

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        if (!element.isWritable) return false

        val genericParams = element.parentOfType<RustGenericParamsElement>() ?: return false
        val hasTypeBounds = !genericParams.typeParamList.filterNotNull().isEmpty()
        val hasLifetimeBounds = !genericParams.lifetimeParamList.filterNotNull().isEmpty()
        return hasTypeBounds || hasLifetimeBounds
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val genericParams = element.parentOfType<RustGenericParamsElement>() ?: return
        val lifetimeBounds = genericParams.lifetimeParamList
        val typeBounds = genericParams.typeParamList
        val whereClause = RustElementFactory.createWhereClause(project, lifetimeBounds, typeBounds) ?: return

        val function = element.parentOfType<RustFnItemElement>() ?: return
        val offset = function.addBefore(whereClause, function.block).textOffset + whereClause.textLength
        editor.caretModel.moveToOffset(offset)
        typeBounds.forEach { it.typeParamBounds?.delete() }
        lifetimeBounds.forEach { it.lifetimeParamBounds?.delete() }
    }
}
