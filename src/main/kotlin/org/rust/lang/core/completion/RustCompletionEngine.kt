package org.rust.lang.core.completion

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.cargo.util.preludeModule
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.impl.mixin.basePath
import org.rust.lang.core.psi.impl.mixin.letDeclarationsVisibleAt
import org.rust.lang.core.psi.impl.rustMod
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.psi.util.visibleFields
import org.rust.lang.core.resolve.enumerateScopesFor
import org.rust.lang.core.resolve.scope.RustResolveScope
import org.rust.lang.core.resolve.scope.boundElements
import org.rust.lang.core.resolve.util.RustResolveUtil
import java.util.*

object RustCompletionEngine {
    fun complete(ref: RustQualifiedReferenceElement): Array<RustNamedElement> =
        collectNamedElements(ref).toVariantsArray()

    fun completeFieldName(field: RustFieldNameElement): Array<RustNamedElement> =
        field.parentOfType<RustStructExprElement>()
                ?.let { it.visibleFields }
                 .orEmpty()
                 .filter { it.name != null }
                 .toTypedArray()

    fun completeUseGlob(glob: RustUseGlobElement): Array<RustNamedElement> =
        glob.basePath?.reference?.resolve()
            .completionsFromResolveScope()
            .toVariantsArray()

    private fun collectNamedElements(ref: RustQualifiedReferenceElement): Collection<RustNamedElement> {
        val qual = ref.qualifier
        if (qual != null) {
            return qual.reference.resolve()
                .completionsFromResolveScope()
        }

        val visitor = CompletionScopeVisitor(ref)
        val scopes = enumerateScopesFor(ref).toMutableList()

        val preludeMod = ref.module?.preludeModule
        if (preludeMod != null && preludeMod is RustFile) scopes += preludeMod

        for (scope in scopes) {
            scope.accept(visitor)
        }

        return visitor.completions
    }
}

private class CompletionScopeVisitor(private val context: RustQualifiedReferenceElement) : RustElementVisitor() {

    val completions: MutableSet<RustNamedElement> = HashSet()

    override fun visitFile(o: PsiFile) {
        o.rustMod?.let { visitResolveScope(it) }
    }

    override fun visitModItem(o: RustModItemElement)                         = visitResolveScope(o)
    override fun visitLambdaExpr(o: RustLambdaExprElement)                   = visitResolveScope(o)
    override fun visitTraitMethodMember(o: RustTraitMethodMemberElement)     = visitResolveScope(o)
    override fun visitFnItem(o: RustFnItemElement)                           = visitResolveScope(o)

    override fun visitScopedLetExpr(o: RustScopedLetExprElement) {
        if (!PsiTreeUtil.isAncestor(o.scopedLetDecl, context, true)) {
            completions.addAll(o.scopedLetDecl.boundElements)
        }
    }

    override fun visitResolveScope(scope: RustResolveScope) {
        completions.addAll(scope.boundElements)
    }

    override fun visitForExpr(o: RustForExprElement) {
        completions.addAll(o.scopedForDecl.boundElements)
    }

    override fun visitBlock(block: RustBlockElement) {
        block.letDeclarationsVisibleAt(context)
            .flatMapTo(completions) { it.boundElements.asSequence() }
    }
}

private fun RustNamedElement?.completionsFromResolveScope(): Collection<RustNamedElement> =
    when (this) {
        is RustResolveScope -> boundElements
        else                -> emptyList()
    }

private fun Collection<RustNamedElement>.toVariantsArray(): Array<RustNamedElement> =
    filter { it.name != null }.toTypedArray()
