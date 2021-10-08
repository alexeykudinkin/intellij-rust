/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.*
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsItemsOwner
import org.rust.lang.core.psi.ext.isEnabledByCfg
import org.rust.lang.core.psi.ext.qualifier
import org.rust.lang.core.types.infer.ResolvedPath
import org.rust.lang.core.types.inference
import org.rust.openapiext.TreeStatus
import org.rust.openapiext.processElementsWithMacros

interface PathUsageMap {
    val pathUsages: Map<String, Set<RsElement>>
    val unresolvedPaths: Set<String>

    val traitUsages: Set<RsTraitItem>
    val unresolvedMethods: Set<String>
}

class PathUsageMapMutable : PathUsageMap {
    override val pathUsages: MutableMap<String, MutableSet<RsElement>> = hashMapOf()
    override val unresolvedPaths: MutableSet<String> = hashSetOf()

    override val traitUsages: MutableSet<RsTraitItem> = hashSetOf()
    override val unresolvedMethods: MutableSet<String> = hashSetOf()

    fun recordPath(name: String, items: List<RsElement>) {
        if (items.isEmpty()) {
            unresolvedPaths += name
        } else {
            pathUsages.getOrPut(name) { hashSetOf() } += items
        }
    }

    fun recordMethod(methodName: String, traits: Set<RsTraitItem>) {
        if (traits.isEmpty()) {
            unresolvedMethods += methodName
        } else {
            traitUsages += traits
        }
    }
}

private val PATH_USAGE_KEY: Key<CachedValue<PathUsageMap>> = Key.create("PATH_USAGE_KEY")

val RsItemsOwner.pathUsage: PathUsageMap
    get() = CachedValuesManager.getCachedValue(this, PATH_USAGE_KEY) {
        val usages = calculatePathUsages(this)
        CachedValueProvider.Result.create(usages, PsiModificationTracker.MODIFICATION_COUNT)
    }

sealed class PathUsageBase {
    data class PathUsage(val name: String, val targets: List<RsElement>, val source: PsiElement?) : PathUsageBase()
    data class TraitUsage(val methodName: String, val traits: Set<RsTraitItem>) : PathUsageBase()
}

fun traversePathUsages(owner: RsItemsOwner, processor: (PathUsageBase) -> Unit) {
    for (child in owner.children) {
        handleSubtree(child, processor)
    }
}

private fun calculatePathUsages(owner: RsItemsOwner): PathUsageMap {
    val usageMap = PathUsageMapMutable()

    traversePathUsages(owner) { usage ->
        when (usage) {
            is PathUsageBase.PathUsage -> usageMap.recordPath(usage.name, usage.targets)
            is PathUsageBase.TraitUsage -> usageMap.recordMethod(usage.methodName, usage.traits)
        }
    }

    return usageMap
}

private fun handleSubtree(root: PsiElement, processor: (PathUsageBase) -> Unit) {
    processElementsWithMacros(root) { element ->
        if (handleElement(element, processor)) {
            TreeStatus.VISIT_CHILDREN
        } else {
            TreeStatus.SKIP_CHILDREN
        }
    }
}

private fun handleElement(element: PsiElement, processor: (PathUsageBase) -> Unit): Boolean {
    if (!element.isEnabledByCfg) return false

    return when (element) {
        is RsModItem -> false
        is RsPatIdent -> {
            val name = element.patBinding.referenceName
            val targets = element.patBinding.reference.multiResolve()
            // if `targets` is empty, there is no way to distinguish "unresolved reference" and "usual pat ident"
            if (targets.isNotEmpty()) {
                processor(PathUsageBase.PathUsage(name, targets, element.patBinding.referenceNameElement))
            }
            true
        }
        is RsPath -> {
            val name = element.referenceName ?: return true
            if (element.qualifier != null || element.typeQual != null) {
                val requiredTraits = getAssociatedItemRequiredTraits(element).orEmpty()
                processor(PathUsageBase.TraitUsage(name, requiredTraits))
            } else {
                val useSpeck = element.parentOfType<RsUseSpeck>()
                if (useSpeck == null || useSpeck.isTopLevel) {
                    if (name in IGNORED_USE_PATHS) return true
                    val targets = element.reference?.multiResolve().orEmpty()
                    processor(PathUsageBase.PathUsage(name, targets, element.referenceNameElement))
                }
            }
            true
        }
        is RsMacroCall -> {
            handleSubtree(element.path, processor)
            true
        }
        is RsMethodCall -> {
            val requiredTraits = getMethodRequiredTraits(element).orEmpty()
            processor(PathUsageBase.TraitUsage(element.referenceName, requiredTraits))
            true
        }
        else -> true
    }
}

private val IGNORED_USE_PATHS = listOf("crate", "self", "super")

private fun getMethodRequiredTraits(call: RsMethodCall): Set<RsTraitItem>? {
    val result = call.inference?.getResolvedMethod(call) ?: return null
    return result.mapNotNull {
        it.source.implementedTrait?.element
    }.toSet()
}

private fun getAssociatedItemRequiredTraits(path: RsPath): Set<RsTraitItem>? {
    val parent = path.parent as? RsPathExpr ?: return null
    val resolved = path.inference?.getResolvedPath(parent) ?: return null
    return resolved.mapNotNull {
        if (it is ResolvedPath.AssocItem) {
            it.source.implementedTrait?.element
        } else null
    }.toSet()
}

/**
 * We should collect paths only from relative use specks,
 * that is top-level use specks without `::`
 * E.g. we shouldn't collect such paths: `use ::{foo, bar}`
 */
private val RsUseSpeck.isTopLevel: Boolean
    get() = (path != null || coloncolon == null)
        && parentOfType<RsUseSpeck>()?.isTopLevel != false
