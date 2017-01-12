package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsBlockExpr
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsLambdaExpr
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.psi.util.parentOfType

class WrapLambdaExprIntention : RsElementBaseIntentionAction<RsExpr>() {
    override fun getText() = "Add braces to lambda expression"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsExpr? {
        val lambdaExpr = element.parentOfType<RsLambdaExpr>() ?: return null
        val body = lambdaExpr.expr ?: return null
        return if (body !is RsBlockExpr) body else null
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsExpr) {
        val relativeCaretPosition = editor.caretModel.offset - ctx.textOffset

        val bodyStr = "\n${ctx.text}\n"
        val blockExpr = RustPsiFactory(project).createBlockExpr(bodyStr)

        val offset = ((ctx.replace(blockExpr)) as RsBlockExpr).block?.expr?.textOffset ?: return
        editor.caretModel.moveToOffset(offset + relativeCaretPosition)
    }
}
