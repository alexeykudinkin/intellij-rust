package org.rust.cargo.icons

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.LayeredIcon
import org.rust.ide.icons.RsIcons

object CargoIcons {
    val ICON = IconLoader.getIcon("/icons/cargo.png")
    val LOCK_ICON = IconLoader.getIcon("/icons/cargo-lock.png")
    val BUILD_RS_ICON = IconLoader.getIcon("/icons/build-rs.png")

    val TEST = LayeredIcon(RsIcons.RUST, AllIcons.RunConfigurations.TestMark)
}
