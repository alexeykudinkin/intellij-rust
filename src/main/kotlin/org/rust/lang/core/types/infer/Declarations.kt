package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.findIteratorItemType
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

fun inferDeclarationType(decl: RsNamedElement): Ty {
    return when (decl) {
        is RsStructItem -> TyStruct(decl)

        is RsEnumItem -> TyEnum(decl)
        is RsEnumVariant -> TyEnum(decl.parentEnum)

        is RsTypeAlias -> {
            val t = decl.typeReference?.type ?: TyUnknown
            (t as? TyStructOrEnumBase)
                ?.aliasTypeArguments(decl.typeParameters.map(::TyTypeParameter)) ?: t
        }

        is RsFunction -> deviseFunctionType(decl)

        is RsTraitItem -> TyTraitObject(decl)

        is RsConstant -> decl.typeReference?.type ?: TyUnknown

        is RsSelfParameter -> deviseSelfType(decl)

        is RsPatBinding -> {
            val pattern = decl.topLevelPattern
            val parent = pattern.parent
            val patternType = when (parent) {
                is RsLetDecl ->
                    // use type ascription, if present or fallback to the type of the initializer expression
                    parent.typeReference?.type ?: parent.expr?.type

                is RsValueParameter -> parent.typeReference?.type ?: inferTypeForLambdaParameter(parent)
                is RsCondition -> parent.expr.type
                is RsMatchArm -> parent.parentOfType<RsMatchExpr>()?.expr?.type
                is RsForExpr -> findIteratorItemType(decl.project, parent.expr?.type ?: TyUnknown)
                else -> null
            } ?: TyUnknown

            inferPatternBindingType(decl, pattern, patternType)
        }

        is RsTypeParameter -> TyTypeParameter(decl)

        else -> TyUnknown
    }
}

private val RsCallExpr.declaration: RsFunction?
    get() = (expr as? RsPathExpr)?.path?.reference?.resolve() as? RsFunction

private val RsMethodCallExpr.declaration: RsFunction?
    get() = reference.resolve() as? RsFunction

private fun inferTypeForMethodExpr(expr: RsExpr?, type: RsBaseType?): Ty {
    val callee = expr?.parentOfType<RsMethodCallExpr>() ?: return TyUnknown
    val method = callee.declaration ?: return TyUnknown
    return inferTypeForMethodExpr(callee, method, type)
}

fun inferTypeForLambdaParameter(parameter: RsValueParameter): Ty {
    val lambda = parameter.parentOfType<RsLambdaExpr>() ?: return TyUnknown
    val parameterPos = lambda.valueParameterList.valueParameterList.indexOf(parameter)
    val bounds = inferExpressionType(lambda) as? TyFunction ?: return TyUnknown
    return bounds.paramTypes[parameterPos]
}

fun inferTypeReferenceType(ref: RsTypeReference): Ty {
    return inferTypeReferenceType(ref, null)
}

fun inferTypeReferenceType(ref: RsTypeReference, expr: RsExpr? = null): Ty {
    return when (ref) {
        is RsTupleType -> {
            val single = ref.typeReferenceList.singleOrNull()
            if (single != null && ref.rparen.getPrevNonCommentSibling()?.elementType != RsElementTypes.COMMA) {
                return inferTypeReferenceType(single)
            }
            if (ref.typeReferenceList.isEmpty()) {
                return TyUnit
            }
            TyTuple(ref.typeReferenceList.map(::inferTypeReferenceType))
        }

        is RsBaseType -> {
            val path = ref.path ?: return TyUnknown

            val primitiveType = TyPrimitive.fromPath(path)
            if (primitiveType != null) return primitiveType

            val target = ref.path?.reference?.resolve() as? RsNamedElement
                ?: return TyUnknown
            val typeArguments = path.typeArgumentList?.typeReferenceList.orEmpty()
            val type = inferDeclarationType(target)
                .applyTypeArguments(typeArguments.map { it.type })

            when (type) {
                is TyUnknown -> inferTypeForMethodExpr(expr, ref)
                else -> type
            }
        }

        is RsRefLikeType -> {
            val base = ref.typeReference ?: return TyUnknown
            val mutable = ref.isMut
            if (ref.isRef) {
                TyReference(inferTypeReferenceType(base, expr), mutable)
            } else {
                if (ref.isPointer) { //Raw pointers
                    TyPointer(inferTypeReferenceType(base, expr), mutable)
                } else {
                    TyUnknown
                }
            }
        }

        is RsArrayType -> {
            val componentType = ref.typeReference?.type ?: TyUnknown
            val size = ref.arraySize
            if (size == null) {
                TySlice(componentType)
            } else {
                TyArray(componentType, size)
            }
        }

        else -> TyUnknown
    }
}

/**
 * Devises type for the given (implicit) self-argument
 */
private fun deviseSelfType(self: RsSelfParameter): Ty {
    val impl = self.parentOfType<RsImplItem>()
    var Self: Ty = if (impl != null) {
        impl.typeReference?.type ?: return TyUnknown
    } else {
        val trait = self.parentOfType<RsTraitItem>()
            ?: return TyUnknown
        TyTypeParameter(trait)
    }

    if (self.isRef) {
        Self = TyReference(Self, mutable = self.isMut)
    }

    return Self
}

private fun deviseFunctionType(fn: RsFunction): TyFunction {
    val paramTypes = mutableListOf<Ty>()

    val self = fn.selfParameter
    if (self != null) {
        paramTypes += deviseSelfType(self)
    }

    paramTypes += fn.valueParameters.map { it.typeReference?.type ?: TyUnknown }

    return TyFunction(paramTypes, fn.returnType)
}

