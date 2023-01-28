/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils

import org.rust.ide.presentation.render
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.emptySubstitution
import org.rust.lang.core.types.infer.type
import org.rust.lang.core.types.inference
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyFunction
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type

class CallInfo private constructor(
    val methodName: String?,
    val selfParameter: String?,
    val parameters: List<Parameter>
) {
    class Parameter(val typeRef: RsTypeReference?, val pattern: String? = null, val type: Ty? = null) {
        fun renderType(): String {
            return if (type != null && type !is TyUnknown) {
                type.render(includeLifetimeArguments = true)
            } else {
                typeRef?.substAndGetText(emptySubstitution) ?: "_"
            }
        }
    }

    companion object {
        fun resolve(call: RsCallExpr): CallInfo? {
            val fn = (call.expr as? RsPathExpr)?.path?.reference?.resolve() ?: return null
            val ty = call.expr.type as? TyFunction ?: return null

            return when (fn) {
                is RsFunction -> buildFunctionParameters(fn, ty)
                else -> buildFunctionLike(fn, ty)
            }
        }

        fun muiltResolve(call: RsCallExpr): List<CallInfo>? {
            val fns = (call.expr as? RsPathExpr)?.path?.reference?.multiResolve() ?: return null
            return fns.map {
                when (it) {
                    is RsFunction -> buildFunctionParameters(it, it.type)
                    else -> {
                        return null
                    }
                }
            }
        }

        fun resolve(methodCall: RsMethodCall): CallInfo? {
            val function = (methodCall.reference.resolve() as? RsFunction) ?: return null
            val type = methodCall.inference?.getResolvedMethodType(methodCall) ?: return null
            return buildFunctionParameters(function, type)
        }

        fun muiltResolve(methodCall: RsMethodCall): List<CallInfo>? {
            val functions = (methodCall.reference.multiResolve() as? List<*>) ?: return null // List<RsFunction>
            return functions.map {
                when (it) {
                    is RsFunction -> {
                        val type = methodCall.inference?.getResolvedMethodType(methodCall) ?: return null
                        buildFunctionParameters(it, type)
                    }
                    else -> {
                        return null
                    }
                }
            }
        }

        private fun buildFunctionLike(fn: RsElement, ty: TyFunction): CallInfo? {
            val parameters = getFunctionLikeParameters(fn) ?: return null
            return CallInfo(buildParameters(ty.paramTypes, parameters))
        }

        private fun getFunctionLikeParameters(element: RsElement): List<Pair<String?, RsTypeReference?>>? {
            return when {
                element is RsEnumVariant -> element.positionalFields.map { null to it.typeReference }
                element is RsStructItem && element.isTupleStruct ->
                    element.tupleFields?.tupleFieldDeclList?.map { null to it.typeReference }
                element is RsPatBinding -> {
                    val decl = element.ancestorStrict<RsLetDecl>() ?: return null
                    val lambda = decl.expr as? RsLambdaExpr ?: return null
                    lambda.valueParameters.map { (it.patText ?: "_") to it.typeReference }
                }
                else -> null
            }
        }

        private fun buildFunctionParameters(function: RsFunction, ty: TyFunction): CallInfo {
            val types = run {
                val types = ty.paramTypes
                if (function.isMethod) {
                    types.drop(1)
                } else {
                    types
                }
            }
            val parameters = function.valueParameters.map {
                val pattern = it.patText ?: "_"
                val type = it.typeReference
                pattern to type
            }
            return CallInfo(function, buildParameters(types, parameters))
        }

        private fun buildParameters(
            argumentTypes: List<Ty>,
            parameters: List<Pair<String?, RsTypeReference?>>,
        ): List<Parameter> {
            return argumentTypes.zip(parameters).map { (type, param) ->
                val (name, parameterType) = param
                Parameter(parameterType, name, type)
            }
        }
    }

    private constructor(fn: RsFunction, parameters: List<Parameter>) : this(
        fn.name,
        fn.selfParameter?.let { self ->
            buildString {
                if (self.isRef) append("&")
                if (self.mutability.isMut) append("mut ")
                append("self")
            }
        },
        parameters
    )

    private constructor(parameters: List<Parameter>) : this(
        null,
        null,
        parameters
    )
}
