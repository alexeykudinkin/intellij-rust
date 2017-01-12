package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsLoopExpr
import org.rust.lang.core.psi.RustPsiFactory

class RsWithLoopSurrounder : RsStatementsSurrounderBase.SimpleBlock<RsLoopExpr>() {
    override fun getTemplateDescription(): String = "loop { }"

    override fun createTemplate(project: Project): Pair<RsLoopExpr, RsBlock> {
        val l = RustPsiFactory(project).createExpression("loop {\n}") as RsLoopExpr
        return l to l.block!!
    }

}
