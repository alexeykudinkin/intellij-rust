package org.rust.lang.core.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.util.childOfType

object RustElementFactory {
    fun createFileFromText(project: Project, text: String): RustFile? =
        PsiFileFactory.getInstance(project).createFileFromText("DUMMY.rs", RustLanguage, text) as RustFile?

    fun createExpression(project: Project, expression: String): RustExprElement? {
        val file = createFileFromText(project, "fn main() {$expression;}")
        return file?.childOfType<RustExprElement>()
    }

    fun createModDeclItem(project: Project, modName: String): RustModDeclItemElement? {
        val file = createFileFromText(project, "mod $modName;")
        return file?.childOfType<RustModDeclItemElement>()
    }

    fun createOuterAttr(project: Project, attrContents: String): RustOuterAttrElement? {
        val file = createFileFromText(project, "#[$attrContents] struct Dummy;")
        return file?.childOfType<RustOuterAttrElement>()
    }

    fun createUseItem(project: Project, path: String): RustUseItemElement? {
        val file = createFileFromText(project, "use $path;")
        return file?.childOfType<RustUseItemElement>()
    }
}
