package org.rust.lang.core.psi

import com.intellij.openapi.util.Iconable
import org.rust.ide.icons.addVisibilityIcon
import javax.swing.Icon

interface RsVisibilityOwner : RsCompositeElement {
    val vis: RsVis?
    val isPublic: Boolean
}

fun RsVisibilityOwner.iconWithVisibility(flags: Int, icon: Icon): Icon =
    if ((flags and Iconable.ICON_FLAG_VISIBILITY) == 0)
        icon
    else
        icon.addVisibilityIcon(isPublic)
