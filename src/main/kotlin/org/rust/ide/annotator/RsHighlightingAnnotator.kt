package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.rust.ide.colors.RsColor
import org.rust.ide.highlight.RsHighlighter
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.RsFunctionRole
import org.rust.lang.core.psi.impl.mixin.isMut
import org.rust.lang.core.psi.impl.mixin.isStatic
import org.rust.lang.core.psi.impl.mixin.role
import org.rust.lang.core.psi.util.elementType
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.types.isPrimitive
import org.rust.lang.core.types.util.resolvedType

// Highlighting logic here should be kept in sync with tags in RustColorSettingsPage
class RustHighlightingAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val (partToHighlight, color) = if (element is RsReferenceElement) {
            highlightReference(element)
        } else {
            highlightNotReference(element)
        } ?: return

        holder.createInfoAnnotation(partToHighlight, null).textAttributes = color.textAttributesKey
    }

    private fun highlightReference(element: RsReferenceElement): Pair<PsiElement, RsColor>? {
        val parent = element.parent
        val isPrimitiveType = element is RsPath && parent is RsBaseType &&
            parent.resolvedType.isPrimitive

        val color = if (isPrimitiveType) {
            RsColor.PRIMITIVE_TYPE
        } else {
            val ref = element.reference.resolve() ?: return null
            // Highlight the element dependent on what it's referencing.
            colorFor(ref)
        }
        return color?.let { element.referenceNameElement to it }
    }

    private fun highlightNotReference(element: PsiElement): Pair<PsiElement, RsColor>? {
        if (element is RsLitExpr) {
            if (element.parent is RsMetaItem) {
                val literal = element.firstChild
                val color = RsHighlighter.map(literal.elementType)
                    ?: return null // FIXME: `error` here perhaps?
                return literal to color
            }
            return null
        }

        // Although we remap tokens from identifier to keyword, this happens in the
        // parser's pass, so we can't use HighlightingLexer to color these
        if (element.elementType in RsTokenElementTypes.CONTEXTUAL_KEYWORDS) {
            return element to RsColor.KEYWORD
        }

        if (element is RsCompositeElement) {
            val color = colorFor(element)
            val part = partToHighlight(element)
            if (color != null && part != null) {
                return part to color
            }
        }
        return null
    }
}

// If possible, this should use only stubs because this will be called
// on elements in other files when highlighting references.
private fun colorFor(element: RsCompositeElement): RsColor? = when (element) {
    is RsAttr -> RsColor.ATTRIBUTE
    is RsMacroInvocation -> RsColor.MACRO
    is RsSelfParameter -> RsColor.SELF_PARAMETER
    is RsTryExpr -> RsColor.Q_OPERATOR
    is RsTraitRef -> RsColor.TRAIT

    is RsEnumItem -> RsColor.ENUM
    is RsEnumVariant -> RsColor.ENUM_VARIANT
    is RsExternCrateItem -> RsColor.CRATE
    is RsFieldDecl -> RsColor.FIELD
    is RsFunction -> when (element.role) {
        RsFunctionRole.FOREIGN, RsFunctionRole.FREE -> RsColor.FUNCTION
        RsFunctionRole.TRAIT_METHOD, RsFunctionRole.IMPL_METHOD ->
            if (element.isStatic) RsColor.ASSOC_FUNCTION else RsColor.METHOD
    }
    is RsMethodCallExpr -> RsColor.METHOD
    is RsModDeclItem -> RsColor.MODULE
    is RsModItem -> RsColor.MODULE
    is RsPatBinding -> when {
        element.parentOfType<RsValueParameter>() != null -> RsColor.PARAMETER
        element.isMut -> RsColor.MUT_BINDING
        else -> null
    }
    is RsStructItem -> RsColor.STRUCT
    is RsTraitItem -> RsColor.TRAIT
    is RsTypeAlias -> RsColor.TYPE_ALIAS
    is RsTypeParameter -> RsColor.TYPE_PARAMETER
    else -> null
}

private fun partToHighlight(element: RsCompositeElement): PsiElement? = when (element) {
    is RsAttr -> element
    is RsMacroInvocation -> element
    is RsSelfParameter -> element.self
    is RsTryExpr -> element.q
    is RsTraitRef -> element.path.identifier

    is RsEnumItem -> element.identifier
    is RsEnumVariant -> element.identifier
    is RsExternCrateItem -> element.identifier
    is RsFieldDecl -> element.identifier
    is RsFunction -> element.identifier
    is RsMethodCallExpr -> element.identifier
    is RsModDeclItem -> element.identifier
    is RsModItem -> element.identifier
    is RsPatBinding -> element.identifier
    is RsStructItem -> element.identifier
    is RsTraitItem -> element.identifier
    is RsTypeAlias -> element.identifier
    is RsTypeParameter -> element.identifier
    else -> null
}
