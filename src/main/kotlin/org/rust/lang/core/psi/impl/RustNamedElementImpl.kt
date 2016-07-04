package org.rust.lang.core.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustTokenElementTypes

abstract class RustNamedElementImpl(node: ASTNode)   : RustCompositeElementImpl(node)
                                                     , RustNamedElement {

    protected open val nameElement: PsiElement?
        get() = findChildByType(RustTokenElementTypes.IDENTIFIER)

    override fun getName(): String? = nameElement?.text

    /**
     * NOTE: This is orphaned purposefully
     */
    override fun setName(name: String): PsiElement? {
        throw UnsupportedOperationException()
    }

    override fun getNavigationElement(): PsiElement = nameElement ?: this

    override fun getTextOffset(): Int = nameElement?.textOffset ?: super.getTextOffset()
}
