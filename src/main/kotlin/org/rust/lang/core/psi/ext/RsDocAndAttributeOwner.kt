/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.NavigatablePsiElement
import org.rust.lang.core.psi.RsAttr
import org.rust.lang.core.psi.RsInnerAttr
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.RsOuterAttr

interface RsDocAndAttributeOwner : RsCompositeElement, NavigatablePsiElement

interface RsInnerAttributeOwner : RsDocAndAttributeOwner {
    /**
     * Outer attributes are always children of the owning node.
     * In contrast, inner attributes can be either direct
     * children or grandchildren.
     */
    val innerAttrList: List<RsInnerAttr>
}

/**
 * An element with attached outer attributes and documentation comments.
 * Such elements should use left edge binder to properly wrap preceding comments.
 *
 * Fun fact: in Rust, documentation comments are a syntactic sugar for attribute syntax.
 *
 * ```
 * /// docs
 * fn foo() {}
 * ```
 *
 * is equivalent to
 *
 * ```
 * #[doc="docs"]
 * fn foo() {}
 * ```
 */
interface RsOuterAttributeOwner : RsDocAndAttributeOwner {
    val outerAttrList: List<RsOuterAttr>
}

/**
 * Find the first outer attribute with the given identifier.
 */
fun RsOuterAttributeOwner.findOuterAttr(name: String): RsOuterAttr? =
    outerAttrList.find { it.metaItem.referenceName == name }

/**
 * Get sequence of all item's inner and outer attributes.
 * Inner attributes take precedence, so they must go first.
 */
val RsDocAndAttributeOwner.allAttributes: Sequence<RsAttr>
    get() = Sequence { (this as? RsInnerAttributeOwner)?.innerAttrList.orEmpty().iterator() } +
        Sequence { (this as? RsOuterAttributeOwner)?.outerAttrList.orEmpty().iterator() }

/**
 * Returns [QueryAttributes] for given PSI element.
 */
val RsDocAndAttributeOwner.queryAttributes: QueryAttributes
    get() = QueryAttributes(allAttributes)

/**
 * Allows for easy querying [RsDocAndAttributeOwner] for specific attributes.
 *
 * **Do not instantiate directly**, use [RsDocAndAttributeOwner.queryAttributes] instead.
 */
class QueryAttributes(private val attributes: Sequence<RsAttr>) {
    fun hasCfgAttr(): Boolean = hasAttribute("cfg")

    fun hasAttribute(attributeName: String) = metaItems.any { it.referenceName == attributeName }

    fun hasAtomAttribute(attributeName: String): Boolean {
        val attr = attrByName(attributeName)
        return attr != null && (!attr.hasEq && attr.metaItemArgs == null)
    }

    fun hasAttributeWithArg(attributeName: String, arg: String): Boolean {
        val attr = attrByName(attributeName) ?: return false
        val args = attr.metaItemArgs ?: return false
        return args.metaItemList.any { it.referenceName == arg }
    }

    fun lookupStringValueForKey(key: String): String? =
        metaItems
            .filter { it.referenceName == key }
            .mapNotNull { it.value }
            .singleOrNull()

    val langAttribute: String?
        get() = getStringAttribute("lang")

    val deriveAttribute: RsMetaItem?
        get() = attrByName("derive")
        
    fun getStringAttribute(attributeName: String): String? = attrByName(attributeName)?.value

    val metaItems: Sequence<RsMetaItem>
        get() = attributes.mapNotNull { it.metaItem }

    private fun attrByName(name: String) = metaItems.find { it.referenceName == name }
}


