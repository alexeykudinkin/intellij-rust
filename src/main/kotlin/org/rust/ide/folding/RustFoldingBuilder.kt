package org.rust.ide.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.util.descendentsOfType
import java.util.*

class RustFoldingBuilder() : FoldingBuilderEx(), DumbAware {
    override fun getPlaceholderText(node: ASTNode): String = "{...}"

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<out FoldingDescriptor> {
        if (root !is RustFile) return emptyArray()

        val descriptors: MutableList<FoldingDescriptor> = ArrayList()

        root.descendentsOfType<RustBlockExpr>().forEach {
            val block = it.block
            if (block != null) {
                descriptors += FoldingDescriptor(it.node, block.textRange)
            }
        }
        root.descendentsOfType<RustImplItem>().forEach {
            val implBody = it.implBody
            if (implBody != null) {
                descriptors += FoldingDescriptor(it.node, implBody.textRange)
            }
        }
        root.descendentsOfType<RustStructItem>().forEach {
            val structDeclArgs = it.structDeclArgs
            if (structDeclArgs != null) {
                descriptors += FoldingDescriptor(it.node, structDeclArgs.textRange)
            }
        }
        root.descendentsOfType<RustStructExpr>().forEach {
            descriptors += FoldingDescriptor(it.node, it.structExprBody.textRange)
        }
        root.descendentsOfType<RustEnumItem>().forEach {
            val enumBody = it.enumBody
            descriptors += FoldingDescriptor(it.node, enumBody.textRange)
        }
        root.descendentsOfType<RustTraitItem>().forEach {
            val traitBody = it.traitBody
            descriptors += FoldingDescriptor(it.node, traitBody.textRange)
        }
        root.descendentsOfType<RustEnumVariant>().forEach {
            val structDeclArgs = it.enumStructArgs
            if (structDeclArgs != null) {
                descriptors += FoldingDescriptor(it.node, structDeclArgs.textRange)
            }
        }
        root.descendentsOfType<RustFnItem>().forEach {
            val fnBody = it.block
            if (fnBody != null) {
                descriptors += FoldingDescriptor(it.node, fnBody.textRange)
            }
        }
        root.descendentsOfType<RustImplMethodMember>().forEach {
            val methodBody = it.block
            if (methodBody != null) {
                descriptors += FoldingDescriptor(it.node, methodBody.textRange)
            }
        }
        root.descendentsOfType<RustTraitMethodMember>().forEach {
            val methodBody = it.block
            if (methodBody != null) {
                descriptors += FoldingDescriptor(it.node, methodBody.textRange)
            }
        }
        root.descendentsOfType<RustModItem>().forEach {
            val rbrace = it.rbrace;
            if (rbrace != null) {
                descriptors += FoldingDescriptor(it.node, TextRange(it.lbrace.textOffset, rbrace.textOffset + 1))
            }
        }
        return descriptors.toTypedArray()
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
