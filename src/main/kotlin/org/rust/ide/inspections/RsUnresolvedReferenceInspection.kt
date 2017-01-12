package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.*
import org.rust.lang.core.types.isPrimitive
import org.rust.lang.core.types.util.resolvedType

class RsUnresolvedReferenceInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Unresolved reference"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitPath(o: RsPath) {
                val p = o.parent
                val isPrimitiveType = o is RsPath &&
                    p is RsBaseType &&
                    p.resolvedType.isPrimitive

                if (isPrimitiveType || o.reference.resolve() != null) return

                val parent = o.path ?: return
                val parentRes = parent.reference.resolve()
                if (parentRes is RsMod || parentRes is RsEnumItem) {
                    holder.registerProblem(o.navigationElement, "Unresolved reference")
                }
            }
        }
}

