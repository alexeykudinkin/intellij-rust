package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElement.EMPTY_ARRAY
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.psi.PsiFile
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.project.workspace.cargoWorkspace
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RsFile
import org.rust.lang.core.psi.impl.mixin.asRustPath
import org.rust.lang.core.psi.impl.mixin.basePath
import org.rust.lang.core.psi.impl.mixin.valueParameters
import org.rust.lang.core.psi.util.fields
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.*
import org.rust.lang.core.symbols.RustPath
import org.rust.lang.core.types.RustTypificationEngine
import org.rust.lang.core.types.type
import org.rust.lang.core.types.stripAllRefsIfAny
import org.rust.lang.core.types.types.RustStructType
import org.rust.lang.core.types.types.RustUnknownType
import org.rust.utils.sequenceOfNotNull

object CompletionEngine {
    const val KEYWORD_PRIORITY = 10.0

    fun completePath(ref: RsPath, namespace: Namespace?): Array<out LookupElement> {
        val path = ref.asRustPath ?: return emptyArray()

        return if (path.segments.isNotEmpty()) {
            val qual = path.dropLastSegment()
            ResolveEngine.resolve(qual, ref, Namespace.Types).firstOrNull()
                .completionsFromResolveScope()
        } else {
            lexicalDeclarations(ref)
                .filterByNamespace(namespace)
                .completionsFromScopeEntries()
        }
    }

    fun completeUseGlob(glob: RsUseGlob): Array<out LookupElement> =
        glob.basePath?.reference?.resolve()
            .completionsFromResolveScope()

    fun completeFieldName(field: RsStructExprField): Array<out LookupElement> =
        field.parentOfType<RsStructExpr>()
            ?.fields.orEmpty()
            .completionsFromNamedElements()

    fun completeFieldOrMethod(field: RsFieldExpr): Array<out LookupElement> {
        val receiverType = field.expr.type.stripAllRefsIfAny()

        // Needs type ascription to please Kotlin's type checker, https://youtrack.jetbrains.com/issue/KT-12696.
        val fields: List<RsNamedElement> = (receiverType as? RustStructType)?.item?.namedFields.orEmpty()

        val methods = receiverType.getMethodsIn(field.project).toList()

        return (fields + methods).completionsFromNamedElements()
    }

    fun completeMethod(call: RsMethodCallExpr): Array<out LookupElement> {
        val receiverType = call.expr.type.stripAllRefsIfAny()
        return receiverType.getMethodsIn(call.project).toList().completionsFromNamedElements()
    }

    fun completeExternCrate(extCrate: RsExternCrateItem): Array<out LookupElement> =
        extCrate.module?.cargoWorkspace?.packages
            ?.filter { it.origin == PackageOrigin.DEPENDENCY }
            ?.mapNotNull { it.libTarget }
            ?.map { LookupElementBuilder.create(extCrate, it.normName).withIcon(extCrate.getIcon(0)) }
            ?.toTypedArray() ?: emptyArray()

    fun completeMod(mod: RsModDeclItem): Array<out LookupElement> {
        val directory = mod.containingMod.ownedDirectory ?: return EMPTY_ARRAY

        val currentModuleName = mod.parentOfType<PsiFile>()?.name?.substringBeforeLast('.')

        val modFromRsFile = directory.files
            .filter { it.name.endsWith(".rs") }
            .map { it.name.substringBeforeLast('.') }
            .filter { it != "mod" && it != currentModuleName }
            .map { LookupElementBuilder.create(it).withIcon(RsIcons.MODULE) }

        val modFromDirectoryModRsFile = directory.subdirectories
            .filter { it.findFile("mod.rs") != null }
            .map { LookupElementBuilder.create(it.name).withIcon(RsIcons.MODULE) }

        return (modFromRsFile + modFromDirectoryModRsFile).toTypedArray()
    }
}

private fun RsCompositeElement?.completionsFromResolveScope(): Array<LookupElement> =
    if (this == null)
        emptyArray()
    else
        sequenceOfNotNull(
            containingDeclarations(this),
            associatedDeclarations(this)
        ).flatten().completionsFromScopeEntries()

private fun Sequence<ScopeEntry>.completionsFromScopeEntries(): Array<LookupElement> =
    mapNotNull {
        it.element?.createLookupElement(it.name)
    }.toList().toTypedArray()

private fun Collection<RsNamedElement>.completionsFromNamedElements(): Array<LookupElement> =
    mapNotNull {
        val name = it.name ?: return@mapNotNull null
        it.createLookupElement(name)
    }.toTypedArray()

fun RsCompositeElement.createLookupElement(scopeName: String): LookupElement {
    val base = LookupElementBuilder.create(this, scopeName)
        .withIcon(if (this is RsFile) RsIcons.MODULE else getIcon(0))

    return when (this) {
        is RsConstant -> base.withTypeText(typeReference?.text)
        is RsFieldDecl -> base.withTypeText(typeReference?.text)

        is RsFunction -> base
            .withTypeText(retType?.typeReference?.text ?: "()")
            .withTailText(valueParameterList?.text?.replace("\\s+".toRegex(), " ") ?: "()")
            .withInsertHandler handler@ { context: InsertionContext, lookupElement: LookupElement ->
                if (context.isInUseBlock) return@handler
                if (context.alreadyHasParens) return@handler
                context.document.insertString(context.selectionEndOffset, "()")
                EditorModificationUtil.moveCaretRelatively(context.editor, if (valueParameters.isEmpty()) 2 else 1)
            }

        is RsStructItem -> base
            .withTailText(when {
                blockFields != null -> " { ... }"
                tupleFields != null -> tupleFields!!.text
                else -> ""
            })

        is RsEnumVariant -> base
            .withTypeText(parentOfType<RsEnumItem>()?.name ?: "")
            .withTailText(when {
                blockFields != null -> " { ... }"
                tupleFields != null ->
                    tupleFields!!.tupleFieldDeclList
                        .map { it.typeReference.text }
                        .joinToString(prefix = "(", postfix = ")")
                else -> ""
            })
            .withInsertHandler handler@ { context, lookupElement ->
                if (context.isInUseBlock) return@handler
                val (text, shift) = when {
                    tupleFields != null -> Pair("()", 1)
                    blockFields != null -> Pair(" {}", 2)
                    else -> return@handler
                }
                context.document.insertString(context.selectionEndOffset, text)
                EditorModificationUtil.moveCaretRelatively(context.editor, shift)
            }

        is RsPatBinding -> base
            .withTypeText(RustTypificationEngine.typify(this).let {
                when (it) {
                    is RustUnknownType -> ""
                    else -> it.toString()
                }
            })

        else -> base
    }
}

private fun RustPath.dropLastSegment(): RustPath {
    check(segments.isNotEmpty())
    val segments = segments.subList(0, segments.size - 1)
    return when (this) {
        is RustPath.CrateRelative -> RustPath.CrateRelative(segments)
        is RustPath.ModRelative -> RustPath.ModRelative(level, segments)
        is RustPath.Named -> RustPath.Named(head, segments)
    }
}

private val InsertionContext.isInUseBlock: Boolean
    get() = file.findElementAt(startOffset - 1)!!.parentOfType<RsUseItem>() != null

private val InsertionContext.alreadyHasParens: Boolean get() {
    val parent = file.findElementAt(startOffset)!!.parentOfType<RsExpr>()
    return (parent is RsMethodCallExpr) || parent?.parent is RsCallExpr
}
