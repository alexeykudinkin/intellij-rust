package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.ide.icons.RustIcons
import org.rust.ide.icons.addStaticMark
import org.rust.lang.core.psi.RustDeclaringElement
import org.rust.lang.core.psi.RustImplMethodMember
import org.rust.lang.core.psi.iconWithVisibility
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import javax.swing.Icon

abstract class RustImplMethodImplMixinMember(node: ASTNode) : RustNamedElementImpl(node)
                                                            , RustImplMethodMember {

    override val declarations: Collection<RustDeclaringElement>
        get() = listOfNotNull(parameters.selfArgument) + parameters.parameterList.orEmpty()

    override fun getIcon(flags: Int): Icon? {
        var icon = RustIcons.METHOD
        if (isStatic) {
            icon = icon.addStaticMark()
        }
        return iconWithVisibility(flags, icon)
    }

    override val isPublic: Boolean
        get() = vis != null

}

val RustImplMethodMember.isStatic: Boolean get() = parameters.selfArgument == null
