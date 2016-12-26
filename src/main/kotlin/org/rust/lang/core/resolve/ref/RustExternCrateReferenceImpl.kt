package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.RustCompletionEngine
import org.rust.lang.core.psi.RustExternCrateItemElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.resolve.RustResolveEngine

class RustExternCrateReferenceImpl(
    externCrate: RustExternCrateItemElement
) : RustReferenceBase<RustExternCrateItemElement>(externCrate),
    RustReference {

    override val RustExternCrateItemElement.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> = RustCompletionEngine.completeExternCrate(element)

    override fun resolveInner(): List<RustNamedElement> = listOfNotNull(RustResolveEngine.resolveExternCrate(element))
}
