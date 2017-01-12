package org.rust.lang.core.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import org.rust.lang.core.psi.RsNamedElement
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.psi.RsTokenElementTypes

abstract class RsNamedElementImpl(node: ASTNode) : RsCompositeElementImpl(node),
                                                   RsNamedElement,
                                                   PsiNameIdentifierOwner {

    override fun getNameIdentifier(): PsiElement? = findChildByType(RsTokenElementTypes.IDENTIFIER)

    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(RustPsiFactory(project).createIdentifier(name))
        return this
    }

    override fun getNavigationElement(): PsiElement = nameIdentifier ?: this

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()
}
