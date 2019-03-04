/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.codeInsight.daemon.impl.HighlightRangeExtension
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.annotator.fixes.AddFeatureAttributeFix
import org.rust.ide.annotator.fixes.AddModuleFileFix
import org.rust.ide.annotator.fixes.AddTurbofishFix
import org.rust.ide.refactoring.RsNamesValidator.Companion.RESERVED_LIFETIME_NAMES
import org.rust.lang.core.*
import org.rust.lang.core.FeatureAvailability.CAN_BE_ADDED
import org.rust.lang.core.FeatureAvailability.NOT_AVAILABLE
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve.namespaces
import org.rust.lang.core.types.inference
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.ty.isSelf
import org.rust.lang.core.types.ty.isSized
import org.rust.lang.core.types.type
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.RsErrorCode
import org.rust.lang.utils.addToHolder

class RsErrorAnnotator : RsAnnotatorBase(), HighlightRangeExtension {
    override fun isForceHighlightParents(file: PsiFile): Boolean = file is RsFile

    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        val visitor = object : RsVisitor() {
            override fun visitBaseType(o: RsBaseType) = checkBaseType(holder, o)
            override fun visitCondition(o: RsCondition) = checkCondition(holder, o)
            override fun visitConstant(o: RsConstant) = checkConstant(holder, o)
            override fun visitValueArgumentList(o: RsValueArgumentList) = checkValueArgumentList(holder, o)
            override fun visitStructItem(o: RsStructItem) = checkDuplicates(holder, o)
            override fun visitEnumItem(o: RsEnumItem) = checkDuplicates(holder, o)
            override fun visitEnumVariant(o: RsEnumVariant) = checkDuplicates(holder, o)
            override fun visitFunction(o: RsFunction) = checkFunction(holder, o)
            override fun visitImplItem(o: RsImplItem) = checkImpl(holder, o)
            override fun visitLabel(o: RsLabel) = checkLabel(holder, o)
            override fun visitLifetime(o: RsLifetime) = checkLifetime(holder, o)
            override fun visitModDeclItem(o: RsModDeclItem) = checkModDecl(holder, o)
            override fun visitModItem(o: RsModItem) = checkDuplicates(holder, o)
            override fun visitPatBox(o: RsPatBox)  = checkPatBox(holder, o)
            override fun visitPatField(o: RsPatField) = checkPatField(holder, o)
            override fun visitPatBinding(o: RsPatBinding) = checkPatBinding(holder, o)
            override fun visitPath(o: RsPath) = checkPath(holder, o)
            override fun visitNamedFieldDecl(o: RsNamedFieldDecl) = checkDuplicates(holder, o)
            override fun visitRetExpr(o: RsRetExpr) = checkRetExpr(holder, o)
            override fun visitTraitItem(o: RsTraitItem) = checkDuplicates(holder, o)
            override fun visitTypeAlias(o: RsTypeAlias) = checkTypeAlias(holder, o)
            override fun visitTypeParameter(o: RsTypeParameter) = checkDuplicates(holder, o)
            override fun visitLifetimeParameter(o: RsLifetimeParameter) = checkLifetimeParameter(holder, o)
            override fun visitVis(o: RsVis) = checkVis(holder, o)
            override fun visitVisRestriction(o: RsVisRestriction) = checkVisRestriction(holder, o)
            override fun visitUnaryExpr(o: RsUnaryExpr) = checkUnary(holder, o)
            override fun visitBinaryExpr(o: RsBinaryExpr) = checkBinary(holder, o)
            override fun visitExternCrateItem(o: RsExternCrateItem) = checkExternCrate(holder, o)
            override fun visitDotExpr(o: RsDotExpr) = checkDotExpr(holder, o)
            override fun visitYieldExpr(o: RsYieldExpr) = checkYieldExpr(holder, o)
            override fun visitArrayType(o: RsArrayType) = collectDiagnostics(holder, o)
            override fun visitVariantDiscriminant(o: RsVariantDiscriminant) = collectDiagnostics(holder, o)
            override fun visitPolybound(o: RsPolybound) = checkPolybound(holder, o)
            override fun visitTraitRef(o: RsTraitRef) = checkTraitRef(holder, o)
        }

        element.accept(visitor)
    }

    private fun checkTraitRef(holder: AnnotationHolder, o: RsTraitRef) {
        val item = o.path.reference.resolve() as? RsItemElement ?: return
        if (item !is RsTraitItem) {
            RsDiagnostic.NotTraitError(o, item).addToHolder(holder)
        }
    }

    private fun checkDotExpr(holder: AnnotationHolder, o: RsDotExpr) {
        val field = o.fieldLookup ?: o.methodCall ?: return
        checkReferenceIsPublic(field, o, holder)
    }

    private fun checkYieldExpr(holder: AnnotationHolder, o: RsYieldExpr) {
        checkFeature(holder, o.yield, GENERATORS, "`yield` syntax")
    }

    private fun checkReferenceIsPublic(ref: RsReferenceElement, o: PsiElement, holder: AnnotationHolder) {
        val element = ref.reference.resolve() as? RsVisible ?: return
        val oMod = o.contextStrict<RsMod>() ?: return
        if (element.isVisibleFrom(oMod)) return

        val error = when {
            element is RsNamedFieldDecl -> {
                val structName = element.ancestorStrict<RsStructItem>()?.crateRelativePath?.removePrefix("::") ?: ""
                RsDiagnostic.StructFieldAccessError(ref, ref.referenceName, structName)
            }
            ref is RsMethodCall -> RsDiagnostic.AccessError(ref.identifier, RsErrorCode.E0624, "Method")
            else -> {
                val itemType = when (element) {
                    is RsMod -> "Module"
                    is RsConstant -> "Constant"
                    is RsFunction -> "Function"
                    is RsStructItem -> "Struct"
                    is RsEnumItem -> "Enum"
                    is RsTraitItem -> "Trait"
                    is RsTypeAlias -> "Type alias"
                    else -> "Item"
                }
                RsDiagnostic.AccessError(ref, RsErrorCode.E0603, itemType)
            }
        }
        error.addToHolder(holder)
    }

    private fun checkBaseType(holder: AnnotationHolder, type: RsBaseType) {
        if (type.underscore == null) return
        val owner = type.owner.parent
        if ((owner is RsValueParameter && owner.parent.parent is RsFunction)
            || (owner is RsRetType && owner.parent is RsFunction) || owner is RsConstant) {
            RsDiagnostic.TypePlaceholderForbiddenError(type).addToHolder(holder)
        }
    }

    private fun checkPatBox(holder: AnnotationHolder, box: RsPatBox) {
        checkFeature(holder, box.box, BOX_PATTERNS, "`box` pattern syntax")
    }

    private fun checkPatField(holder: AnnotationHolder, field: RsPatField) {
        val box = field.box ?: return
        checkFeature(holder, box, BOX_PATTERNS, "`box` pattern syntax")
    }

    private fun checkPatBinding(holder: AnnotationHolder, binding: RsPatBinding) {
        binding.ancestorStrict<RsValueParameterList>()?.let { checkDuplicates(holder, binding, it, recursively = true) }
    }

    private fun checkPath(holder: AnnotationHolder, path: RsPath) {
        val qualifier = path.path
        if ((qualifier == null || isValidSelfSuperPrefix(qualifier)) && !isValidSelfSuperPrefix(path)) {
            holder.createErrorAnnotation(path.referenceNameElement, "Invalid path: self and super are allowed only at the beginning")
            return
        }

        val parent = path.parent
        if (path.self != null && parent !is RsPath && parent !is RsUseSpeck && parent !is RsVisRestriction) {
            val function = path.ancestorStrict<RsFunction>()
            if (function == null) {
                holder.createErrorAnnotation(path, "self value is not available in this context")
                return
            }

            if (function.selfParameter == null) {
                RsDiagnostic.SelfInStaticMethodError(path, function).addToHolder(holder)
            }
        }

        val crate = path.crate
        val useSpeck = path.ancestorStrict<RsUseSpeck>()
        val edition = path.containingCargoTarget?.edition

        // `pub(crate)` should be annotated
        if (crate != null && (qualifier != null || path.ancestorStrict<RsVisRestriction>() == null)) {
            if (qualifier != null || useSpeck != null && useSpeck.qualifier != null) {
                RsDiagnostic.UndeclaredTypeOrModule(crate).addToHolder(holder)
            } else if (edition == Edition.EDITION_2015) {
                checkFeature(holder, crate, CRATE_IN_PATHS, "`crate` in paths")
            }
        }

        checkReferenceIsPublic(path, path, holder)
    }

    private fun checkLifetimeParameter(holder: AnnotationHolder, lifetimeParameter: RsLifetimeParameter) {
        checkReservedLifetimeName(holder, lifetimeParameter)
        checkDuplicates(holder, lifetimeParameter)
    }

    private fun checkReservedLifetimeName(holder: AnnotationHolder, lifetimeParameter: RsLifetimeParameter) {
        val lifetimeName = lifetimeParameter.quoteIdentifier.text
        if (lifetimeName in RESERVED_LIFETIME_NAMES) {
            RsDiagnostic.ReservedLifetimeNameError(lifetimeParameter, lifetimeName).addToHolder(holder)
        }
    }

    private fun checkVis(holder: AnnotationHolder, vis: RsVis) {
        if (vis.parent is RsImplItem || vis.parent is RsForeignModItem || isInTraitImpl(vis) || isInEnumVariantField(vis)) {
            RsDiagnostic.UnnecessaryVisibilityQualifierError(vis).addToHolder(holder)
        }
        checkCrateVisibilityModifier(holder, vis)
    }

    private fun checkCrateVisibilityModifier(holder: AnnotationHolder, vis: RsVis) {
        val crateModifier = vis.crate ?: return
        checkFeature(holder, crateModifier, CRATE_VISIBILITY_MODIFIER, "`crate` visibility modifier")
    }

    private fun checkVisRestriction(holder: AnnotationHolder, visRestriction: RsVisRestriction) {
        val path = visRestriction.path
        // pub(foo) or pub(super::bar)
        if (visRestriction.`in` == null && (path.path != null || path.kind == PathKind.IDENTIFIER)) {
            RsDiagnostic.IncorrectVisibilityRestriction(visRestriction).addToHolder(holder)
        }
    }

    private fun checkFeature(
        holder: AnnotationHolder,
        element: PsiElement,
        feature: CompilerFeature,
        presentableFeatureName: String,
        vararg fixes: LocalQuickFix
    ) {
        checkFeature(holder, element, null, feature, "$presentableFeatureName is experimental", *fixes)
    }

    private fun checkFeature(
        holder: AnnotationHolder,
        startElement: PsiElement,
        endElement: PsiElement?,
        feature: CompilerFeature,
        message: String,
        vararg fixes: LocalQuickFix
    ) {
        val availability = feature.availability(startElement)
        val diagnostic = when (availability) {
            NOT_AVAILABLE -> RsDiagnostic.ExperimentalFeature(startElement, endElement, message, fixes.toList())
            CAN_BE_ADDED -> {
                val fix = AddFeatureAttributeFix(feature.name, startElement)
                RsDiagnostic.ExperimentalFeature(startElement, endElement, message, listOf(*fixes, fix))
            }
            else -> return
        }

        diagnostic.addToHolder(holder)
    }

    private fun checkLabel(holder: AnnotationHolder, label: RsLabel) {
        if (!hasResolve(label)) return
        RsDiagnostic.UndeclaredLabelError(label).addToHolder(holder)
    }

    private fun checkLifetime(holder: AnnotationHolder, lifetime: RsLifetime) {
        if (lifetime.isPredefined || !hasResolve(lifetime)) return
        RsDiagnostic.UndeclaredLifetimeError(lifetime).addToHolder(holder)
    }

    private fun checkModDecl(holder: AnnotationHolder, modDecl: RsModDeclItem) {
        checkDuplicates(holder, modDecl)
        val pathAttribute = modDecl.pathAttribute

        // mods inside blocks require explicit path attribute
        // https://github.com/rust-lang/rust/pull/31534
        if (modDecl.isLocal && pathAttribute == null) {
            val message = "Cannot declare a non-inline module inside a block unless it has a path attribute"
            holder.createErrorAnnotation(modDecl, message)
            return
        }

        if (!modDecl.containingMod.ownsDirectory && pathAttribute == null) {
            val featureAvailability = NON_MODRS_MODS.availability(modDecl)
            if (featureAvailability == NOT_AVAILABLE || featureAvailability == CAN_BE_ADDED) {
                // We don't want to show the warning if there is no cargo project
                // associated with the current module. Without it we can't know for
                // sure that a mod is not a directory owner.
                if (modDecl.cargoWorkspace != null) {
                    val addModule = AddModuleFileFix(modDecl, expandModuleFirst = true)
                    checkFeature(holder, modDecl, NON_MODRS_MODS, "mod statements in non-mod.rs files", addModule)
                }
                return
            }
        }

        if (modDecl.reference.resolve() == null && modDecl.semicolon != null) {
            RsDiagnostic.ModuleNotFound(modDecl).addToHolder(holder)
        }
    }

    private fun checkImpl(holder: AnnotationHolder, impl: RsImplItem) {
        val traitRef = impl.traitRef ?: return
        val trait = traitRef.resolveToTrait ?: return
        val traitName = trait.name ?: return

        fun mayDangleOnTypeOrLifetimeParameters(impl: RsImplItem): Boolean {
            return impl.typeParameters.any { it.queryAttributes.hasAtomAttribute("may_dangle") } ||
                impl.lifetimeParameters.any { it.queryAttributes.hasAtomAttribute("may_dangle") }
        }

        val attrRequiringUnsafeImpl = if (mayDangleOnTypeOrLifetimeParameters(impl)) "may_dangle" else null
        when {
            impl.isUnsafe && impl.excl != null ->
                RsDiagnostic.UnsafeNegativeImplementationError(traitRef).addToHolder(holder)

            impl.isUnsafe && !trait.isUnsafe && attrRequiringUnsafeImpl == null ->
                RsDiagnostic.UnsafeTraitImplError(traitRef, traitName).addToHolder(holder)

            !impl.isUnsafe && trait.isUnsafe && impl.excl == null ->
                RsDiagnostic.TraitMissingUnsafeImplError(traitRef, traitName).addToHolder(holder)

            !impl.isUnsafe && !trait.isUnsafe && impl.excl == null && attrRequiringUnsafeImpl != null ->
                RsDiagnostic.TraitMissingUnsafeImplAttributeError(traitRef, attrRequiringUnsafeImpl).addToHolder(holder)
        }
    }

    private fun checkTypeAlias(holder: AnnotationHolder, ta: RsTypeAlias) {
        checkDuplicates(holder, ta)
    }

    private fun checkUnary(holder: AnnotationHolder, o: RsUnaryExpr) {
        val box = o.box ?: return
        checkFeature(holder, box, BOX_SYNTAX, "`box` expression syntax")
    }

    private fun checkBinary(holder: AnnotationHolder, o: RsBinaryExpr) {
        if (o.isComparisonBinaryExpr() && (o.left.isComparisonBinaryExpr() || o.right.isComparisonBinaryExpr())) {
            val annotator = holder.createErrorAnnotation(o, "Chained comparison operator require parentheses")
            annotator.registerFix(AddTurbofishFix())
        }
    }

    private fun checkValueArgumentList(holder: AnnotationHolder, args: RsValueArgumentList) {
        val parent = args.parent
        val (expectedCount, variadic) = when (parent) {
            is RsCallExpr -> parent.expectedParamsCount()
            is RsMethodCall -> parent.expectedParamsCount()
            else -> null
        } ?: return
        val realCount = args.exprList.size
        if (variadic && realCount < expectedCount) {
            RsDiagnostic.TooFewParamsError(args, expectedCount, realCount).addToHolder(holder)
        } else if (!variadic && realCount != expectedCount) {
            RsDiagnostic.TooManyParamsError(args, expectedCount, realCount).addToHolder(holder)
        }
    }

    private fun checkCondition(holder: AnnotationHolder, element: RsCondition) {
        val patList = element.patList
        val pat = patList.singleOrNull()
        if (pat != null && pat.isIrrefutable) {
            checkFeature(holder, pat, IRREFUTABLE_LET_PATTERNS, "irrefutable let pattern")
        }
        if (patList.size > 1) {
            checkFeature(holder, patList.first(), patList.last(),
                IF_WHILE_OR_PATTERNS, "multiple patterns in `if let` and `while let` are unstable")
        }
    }

    private fun checkConstant(holder: AnnotationHolder, element: RsConstant) {
        collectDiagnostics(holder, element)
        checkDuplicates(holder, element)
    }

    private fun checkFunction(holder: AnnotationHolder, fn: RsFunction) {
        collectDiagnostics(holder, fn)
        checkDuplicates(holder, fn)
        checkTypesAreSized(holder, fn)
    }

    private fun collectDiagnostics(holder: AnnotationHolder, element: RsInferenceContextOwner) {
        for (it in element.inference.diagnostics) {
            if (it.inspectionClass == javaClass) it.addToHolder(holder)
        }
    }

    private fun checkRetExpr(holder: AnnotationHolder, ret: RsRetExpr) {
        if (ret.expr != null) return
        val fn = ret.ancestors.find { it is RsFunction || it is RsLambdaExpr } as? RsFunction ?: return
        val retType = fn.retType?.typeReference?.type ?: return
        if (retType is TyUnit) return
        RsDiagnostic.ReturnMustHaveValueError(ret).addToHolder(holder)
    }

    private fun checkExternCrate(holder: AnnotationHolder, el: RsExternCrateItem) {
        if (el.reference.multiResolve().isNotEmpty() || el.containingCargoPackage?.origin != PackageOrigin.WORKSPACE) return
        RsDiagnostic.CrateNotFoundError(el, el.identifier.text).addToHolder(holder)
    }

    private fun checkPolybound(holder: AnnotationHolder, o: RsPolybound) {
        if (o.lparen != null && o.bound.lifetime != null) {
            holder.createErrorAnnotation(o, "Parenthesized lifetime bounds are not supported")
        }
    }

    private fun isInTraitImpl(o: RsVis): Boolean {
        val impl = o.parent?.parent?.parent
        return impl is RsImplItem && impl.traitRef != null
    }

    private fun isInEnumVariantField(o: RsVis): Boolean {
        val field = o.parent as? RsNamedFieldDecl
            ?: o.parent as? RsTupleFieldDecl
            ?: return false
        return field.parent.parent is RsEnumVariant
    }

    private fun hasResolve(el: RsReferenceElement): Boolean =
        !(el.reference.resolve() != null || el.reference.multiResolve().size > 1)
}

private fun RsExpr?.isComparisonBinaryExpr(): Boolean {
    val op = (this as? RsBinaryExpr)?.operatorType ?: return false
    return op is ComparisonOp || op is EqualityOp
}

private fun checkDuplicates(holder: AnnotationHolder, element: RsNameIdentifierOwner, scope: PsiElement = element.parent, recursively: Boolean = false) {
    val owner = if (scope is RsMembers) scope.parent else scope
    val duplicates = holder.currentAnnotationSession.duplicatesByNamespace(scope, recursively)
    val ns = element.namespaces.find { element in duplicates[it].orEmpty() }
        ?: return
    val name = element.name!!
    val identifier = element.nameIdentifier ?: element
    val message = when {
        element is RsNamedFieldDecl -> RsDiagnostic.DuplicateFieldError(identifier, name)
        element is RsEnumVariant -> RsDiagnostic.DuplicateEnumVariantError(identifier, name)
        element is RsLifetimeParameter -> RsDiagnostic.DuplicateLifetimeError(identifier, name)
        element is RsPatBinding && owner is RsValueParameterList -> RsDiagnostic.DuplicateBindingError(identifier, name)
        element is RsTypeParameter -> RsDiagnostic.DuplicateTypeParameterError(identifier, name)
        owner is RsImplItem -> RsDiagnostic.DuplicateDefinitionError(identifier, name)
        else -> {
            val scopeType = when (owner) {
                is RsBlock -> "block"
                is RsMod, is RsForeignModItem -> "module"
                is RsTraitItem -> "trait"
                else -> "scope"
            }
            RsDiagnostic.DuplicateItemError(identifier, ns.itemName, name, scopeType)
        }
    }
    message.addToHolder(holder)
}

private fun AnnotationSession.duplicatesByNamespace(owner: PsiElement, recursively: Boolean): Map<Namespace, Set<PsiElement>> {
    val fileMap = fileDuplicatesMap()
    fileMap[owner]?.let { return it }

    val duplicates: Map<Namespace, Set<PsiElement>> =
        owner.namedChildren(recursively)
            .filter { it !is RsExternCrateItem } // extern crates can have aliases.
            .filter { it.name != null }
            .flatMap { it.namespaced }
            .groupBy { it.first }       // Group by namespace
            .map { entry ->
                val (namespace, items) = entry
                namespace to items.asSequence()
                    .map { it.second }
                    .groupBy { it.name }
                    .map { it.value }
                    .filter {
                        it.size > 1 &&
                            it.any { !(it is RsDocAndAttributeOwner && it.queryAttributes.hasCfgAttr()) }
                    }
                    .flatten()
                    .toSet()
            }
            .toMap()

    fileMap[owner] = duplicates
    return duplicates
}

private fun PsiElement.namedChildren(recursively: Boolean): Sequence<RsNamedElement> =
    if (recursively) {
        descendantsOfType<RsNamedElement>().asSequence()
    } else {
        children.asSequence().filterIsInstance<RsNamedElement>()
    }

private val DUPLICATES_BY_SCOPE = Key<MutableMap<
    PsiElement,
    Map<Namespace, Set<PsiElement>>>>("org.rust.ide.annotator.RsErrorAnnotator.duplicates")

private fun AnnotationSession.fileDuplicatesMap(): MutableMap<PsiElement, Map<Namespace, Set<PsiElement>>> {
    var map = getUserData(DUPLICATES_BY_SCOPE)
    if (map == null) {
        map = mutableMapOf()
        putUserData(DUPLICATES_BY_SCOPE, map)
    }
    return map
}

private val RsNamedElement.namespaced: Sequence<Pair<Namespace, RsNamedElement>>
    get() = namespaces.asSequence().map { Pair(it, this) }

private fun RsCallExpr.expectedParamsCount(): Pair<Int, Boolean>? {
    val path = (expr as? RsPathExpr)?.path ?: return null
    val el = path.reference.resolve()
    if (el is RsDocAndAttributeOwner && el.queryAttributes.hasCfgAttr()) return null
    return when (el) {
        is RsFieldsOwner -> el.tupleFields?.tupleFieldDeclList?.size?.let { Pair(it, false) }
        is RsFunction -> {
            val owner = el.owner
            if (owner.isTraitImpl) return null
            val count = el.valueParameterList?.valueParameterList?.size ?: return null
            // We can call foo.method(1), or Foo::method(&foo, 1), so need to take coloncolon into account
            val s = if (path.coloncolon != null && el.selfParameter != null) 1 else 0
            Pair(count + s, el.isVariadic)
        }
        else -> null
    }
}

private fun RsMethodCall.expectedParamsCount(): Pair<Int, Boolean>? {
    val fn = reference.resolve() as? RsFunction ?: return null
    if (fn.queryAttributes.hasCfgAttr()) return null
    return fn.valueParameterList?.valueParameterList?.size?.let { Pair(it, fn.isVariadic) }
        .takeIf { fn.owner.isInherentImpl }
}

private fun isValidSelfSuperPrefix(path: RsPath): Boolean {
    if (path.self == null && path.`super` == null) return true
    if (path.path == null && path.coloncolon != null) return false
    if (path.self != null && path.path != null) return false
    if (path.`super` != null) {
        val q = path.path ?: return true
        return q.self != null || q.`super` != null
    }
    return true
}

private fun checkTypesAreSized(holder: AnnotationHolder, fn: RsFunction) {
    val arguments = fn.valueParameterList?.valueParameterList.orEmpty()
    val retType = fn.retType
    if (arguments.isEmpty() && retType == null) return

    val owner = fn.owner

    fun isError(ty: Ty): Boolean = !ty.isSized() &&
        // Self type in trait method is not an error
        !(owner is RsAbstractableOwner.Trait && ty.isSelf)

    for (arg in arguments) {
        val typeReference = arg.typeReference ?: continue
        val ty = typeReference.type
        if (isError(ty)) {
            RsDiagnostic.SizedTraitIsNotImplemented(typeReference, ty).addToHolder(holder)
        }
    }

    val typeReference = retType?.typeReference ?: return
    val ty = typeReference.type
    if (isError(ty)) {
        RsDiagnostic.SizedTraitIsNotImplemented(typeReference, ty).addToHolder(holder)
    }
}
