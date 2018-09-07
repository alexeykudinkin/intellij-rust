/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.codeInsight.lookup.LookupElement
import org.rust.lang.core.completion.createLookupElement
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.isTest
import org.rust.lang.core.resolve.ref.MethodResolveVariant
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.emptySubstitution

/**
 * ScopeEntry is some PsiElement visible in some code scope.
 *
 * [ScopeEntry] handles the two case:
 *   * aliases (that's why we need a [name] property)
 *   * lazy resolving of actual elements (that's why [element] can return `null`)
 */
interface ScopeEntry {
    val name: String
    val element: RsElement?
    val subst: Substitution get() = emptySubstitution
}

/** This special event allows to transmit "out of band" information to the resolve processor. */
enum class ScopeEvent : ScopeEntry {
    // Communicate to the resolve processor that we are about to process wildecard imports. This is basically a hack
    // to make winapi 0.2 work in a reasonable amount of time.
    STAR_IMPORTS;

    override val element: RsElement? get() = null
}

/** Return `true` to stop further processing, return `false` to continue search. */
typealias RsResolveProcessor = (ScopeEntry) -> Boolean

typealias RsMethodResolveProcessor = (MethodResolveVariant) -> Boolean

fun collectPathResolveVariants(
    referenceName: String,
    function: (RsResolveProcessor) -> Unit
): List<BoundElement<RsElement>> {
    val result = mutableListOf<BoundElement<RsElement>>()
    function { entry ->
        if (entry == ScopeEvent.STAR_IMPORTS && result.isNotEmpty()) return@function true

        if (entry.name == referenceName) {
            val element = entry.element ?: return@function false
            result += BoundElement(element, entry.subst)
        }
        false
    }
    return result
}

fun collectResolveVariants(referenceName: String, function: (RsResolveProcessor) -> Unit): List<RsElement> {
    val result = mutableListOf<RsElement>()
    function { entry ->
        if (entry == ScopeEvent.STAR_IMPORTS && result.isNotEmpty()) return@function true

        if (entry.name == referenceName) {
            result += entry.element ?: return@function false
        }
        false
    }
    return result
}

fun collectCompletionVariants(function: (RsResolveProcessor) -> Unit): Array<LookupElement> {
    val result = mutableListOf<LookupElement>()
    function { entry ->
        val element = entry.element ?: return@function false
        if (element is RsFunction && element.isTest) return@function false
        result += createLookupElement(element, entry.name)
        false
    }
    return result.toTypedArray()
}

private data class SimpleScopeEntry(
    override val name: String,
    override val element: RsElement,
    override val subst: Substitution = emptySubstitution
) : ScopeEntry

data class AssocItemScopeEntry(
    override val name: String,
    override val element: RsElement,
    override val subst: Substitution = emptySubstitution,
    val impl: RsImplItem?
) : ScopeEntry

private class LazyScopeEntry(
    override val name: String,
    thunk: Lazy<RsElement?>
) : ScopeEntry {
    override val element: RsElement? by thunk
    override fun toString(): String = "LazyScopeEntry($name, $element)"
}

operator fun RsResolveProcessor.invoke(name: String, e: RsElement, subst: Substitution = emptySubstitution): Boolean =
    this(SimpleScopeEntry(name, e, subst))

fun RsResolveProcessor.lazy(name: String, e: () -> RsElement?): Boolean =
    this(LazyScopeEntry(name, lazy(e)))

operator fun RsResolveProcessor.invoke(e: RsNamedElement): Boolean {
    val name = e.name ?: return false
    return this(name, e)
}

operator fun RsResolveProcessor.invoke(e: BoundElement<RsNamedElement>): Boolean {
    val name = e.element.name ?: return false
    return this(SimpleScopeEntry(name, e.element, e.subst))
}

fun processAll(elements: Collection<RsNamedElement>, processor: RsResolveProcessor): Boolean =
    processAll(elements.asSequence(), processor)

fun processAll(elements: Sequence<RsNamedElement>, processor: RsResolveProcessor): Boolean {
    for (e in elements) {
        if (processor(e)) return true
    }
    return false
}

fun processAllWithSubst(
    elements: Collection<RsNamedElement>,
    subst: Substitution,
    processor: RsResolveProcessor
): Boolean {
    for (e in elements) {
        if (processor(BoundElement(e, subst))) return true
    }
    return false
}
