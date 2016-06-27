package org.rust.lang.core.completion

import com.intellij.codeInsight.lookup.LookupElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.basePath
import org.rust.lang.core.psi.util.fields
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.RustResolveEngine
import org.rust.lang.core.resolve.scope.RustResolveScope
import org.rust.lang.core.types.RustStructType
import org.rust.lang.core.types.util.resolvedType

object RustCompletionEngine {
    fun complete(ref: RustQualifiedReferenceElement): Array<out Any> {
        return collectNamedElements(ref).toVariantsArray()
//        val qual = ref.qualifier
//        if (qual != null) {
//            val completionsFromResolveScope = (qual.reference.resolve() as? RustResolveScope)?.let {
//                RustResolveEngine.declarations(it)
//            } ?: emptySequence()
//        }
//        return emptyArray()
    }

    fun completeFieldName(field: RustStructExprFieldElement): Array<out LookupElement> =
        field.parentOfType<RustStructExprElement>()
                ?.let       { it.fields }
                 .orEmpty()
                 .toVariantsArray()

    fun completeFieldOrMethod(field: RustFieldExprElement): Array<out LookupElement> {
        val structType = (field.expr.resolvedType as? RustStructType) ?: return emptyArray()
        // Needs type ascription to please Kotlin's type checker, https://youtrack.jetbrains.com/issue/KT-12696.
        val fieldsAndMethods: List<RustNamedElement> = (structType.struct.fields + structType.nonStaticMethods)
        return fieldsAndMethods.toVariantsArray()
    }

    fun completeUseGlob(glob: RustUseGlobElement): Array<out Any> =
        glob.basePath?.reference?.resolve()
            .completionsFromResolveScope()
            .toVariantsArray()

    private fun collectNamedElements(ref: RustQualifiedReferenceElement): Collection<RustNamedElement> {
        // TODO: handle aliased items properly
        val qual = ref.qualifier
        if (qual != null) {
            return qual.reference.resolve()
                .completionsFromResolveScope()
        }

        return RustResolveEngine.enumerateScopesFor(ref)
            .flatMap { RustResolveEngine.declarations(it, pivot = ref) }
            .toList()
    }

//    private fun collectLookupElements(ref: RustQualifiedReferenceElement): Collection<LookupElement> {
//        val qual = ref.qualifier
//        if (qual != null) {
//            return qual.reference.resolve().completionsFromResolveScope()
//        }
//    }
}

private fun RustNamedElement?.completionsFromResolveScope(): Collection<RustNamedElement> =
    when (this) {
        is RustResolveScope -> RustResolveEngine.declarations(this).toList()
        else                -> emptyList()
    }

private fun Collection<RustNamedElement>.toVariantsArray(): Array<out LookupElement> =
    filter { it.name != null }.map { it.createLookupElement() }.toTypedArray()
