package org.rust.lang.core.resolve.util

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.containingMod
import org.rust.lang.core.psi.superMods
import org.rust.lang.core.resolve.scope.RustResolveScope

object RustResolveUtil {

    fun getResolveScopeFor(elem: PsiElement): RustResolveScope? {
        var current = elem.parent
        while (current != null) {
            when (current) {
                is RustResolveScope -> return current
                else                -> current = current.parent
            }
        }

        return null
    }

    /**
     *  Returns a "crate root": the top level module of the crate to which the `PsiElement` belongs.
     *
     *  In most cases it will be situated in another file, typically `src/main.rs` or `src/lib.rs`.
     *
     *  Reference:
     *    https://doc.rust-lang.org/reference.html#crates-and-source-files
     */
    fun getCrateRootModFor(elem: RustCompositeElement): RustMod? {
        val mod = elem as? RustMod ?: elem.containingMod

        val root = mod?.superMods?.lastOrNull()
        return if (root != null && root.isCrateRoot)
            root
        else
            null
    }
}

