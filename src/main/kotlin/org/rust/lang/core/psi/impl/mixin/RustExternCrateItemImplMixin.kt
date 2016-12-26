package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustExternCrateItemElement
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.psi.impl.RustStubbedNamedElementImpl
import org.rust.lang.core.resolve.ref.RustExternCrateReferenceImpl
import org.rust.lang.core.resolve.ref.RustReference
import org.rust.lang.core.stubs.RustExternCrateItemElementStub

abstract class RustExternCrateItemImplMixin : RustStubbedNamedElementImpl<RustExternCrateItemElementStub>,
                                              RustExternCrateItemElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustExternCrateItemElementStub, elementType: IStubElementType<*, *>) : super(stub, elementType)

    override fun getReference(): RustReference = RustExternCrateReferenceImpl(this)

    override val referenceNameElement: PsiElement get() = identifier

    override val referenceName: String get() = referenceNameElement.text

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this, stub)

    override fun getIcon(flags: Int) = RustIcons.CRATE
}
