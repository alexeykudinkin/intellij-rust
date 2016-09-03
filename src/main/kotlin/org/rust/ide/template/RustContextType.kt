package org.rust.ide.template

import com.intellij.codeInsight.template.EverywhereContextType
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import org.rust.ide.highlight.RustHighlighter
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.util.parentOfType
import kotlin.reflect.KClass

sealed class RustContextType(
    id: String,
    presentableName: String,
    baseContextType: KClass<out TemplateContextType>
) : TemplateContextType(id, presentableName, baseContextType.java) {
    final override fun isInContext(file: PsiFile, offset: Int): Boolean {
        if (!PsiUtilCore.getLanguageAtOffset(file, offset).isKindOf(RustLanguage)) {
            return false
        }

        val element = file.findElementAt(offset)
        if (element == null || element is PsiComment || element is RustLiteral) {
            return false
        }

        return isInContext(element)
    }

    abstract protected fun isInContext(element: PsiElement): Boolean

    override fun createHighlighter(): SyntaxHighlighter = RustHighlighter()

    class Generic : RustContextType("RUST_FILE", "Rust", EverywhereContextType::class) {
        override fun isInContext(element: PsiElement): Boolean = true
    }

    class Statement : RustContextType("RUST_STATEMENT", "Statement", Generic::class) {
        override fun isInContext(element: PsiElement): Boolean =
            // We are inside block but there is no item nor attr between
            PsiTreeUtil.findFirstParent(element, blockOrItem) is RustBlockElement
    }

    class Item : RustContextType("RUST_ITEM", "Item", Generic::class) {
        override fun isInContext(element: PsiElement): Boolean =
            // We are inside item but there is no block between
            PsiTreeUtil.findFirstParent(element, blockOrItem) is RustItemElement
    }

    class Struct : RustContextType("RUST_STRUCT", "Structure", Item::class) {
        override fun isInContext(element: PsiElement): Boolean =
            // Structs can't be nested or contain other expressions,
            // so it is ok to look for any Struct ancestor.
            element.parentOfType<RustStructItemElement>() != null
    }

    class Mod : RustContextType("RUST_MOD", "Module", Item::class) {
        override fun isInContext(element: PsiElement): Boolean =
            // We are inside RustModItemElement
            PsiTreeUtil.findFirstParent(element, blockOrItem) is RustModItemElement
    }

    class Attribute : RustContextType("RUST_ATTRIBUTE", "Attribute", Item::class) {
        override fun isInContext(element: PsiElement): Boolean =

            element.parentOfType<RustAttrElement>() != null
    }

    companion object {
        private val blockOrItem = Condition<PsiElement> { element ->
            element is RustBlockElement || element is RustItemElement || element is RustAttrElement
        }
    }
}
