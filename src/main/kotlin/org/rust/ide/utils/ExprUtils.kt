package org.rust.ide.utils

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.ANDAND
import org.rust.lang.core.psi.RsElementTypes.OROR

/**
 * Returns `true` if all elements are `true`, `false` if there exists
 * `false` element and `null` otherwise.
 */
private fun <T> List<T>.allMaybe(predicate: (T) -> Boolean?): Boolean? {
    val values = map(predicate)
    val nullsTrue = values.all {
        it ?: true
    }
    val nullsFalse = values.all {
        it ?: false
    }
    return if (nullsTrue == nullsFalse) nullsTrue else null
}

/**
 * Check if an expression is functionally pure
 * (has no side-effects and throws no errors).
 *
 * @return `true` if the expression is pure, `false` if
 *        > it is not pure (has side-effects / throws errors)
 *        > or `null` if it is unknown.
 */
fun RsExpr.isPure(): Boolean? {
    return when (this) {
        is RsArrayExpr -> when (semicolon) {
            null -> exprList.allMaybe(RsExpr::isPure)
            else -> exprList[0].isPure() // Array literal of form [expr; size],
        // size is a compile-time constant, so it is always pure
        }
        is RsStructExpr -> when (structExprBody.dotdot) {
            null -> structExprBody.structExprFieldList
                .map { it.expr }
                .allMaybe { it?.isPure() } // TODO: Why `it` can be null?
            else -> null // TODO: handle update case (`Point{ y: 0, z: 10, .. base}`)
        }
        is RsBinaryExpr -> when (operatorType) {
            ANDAND, OROR -> listOf(left, right!!).allMaybe(RsExpr::isPure)
            else -> null // Have to search if operation is overloaded
        }
        is RsTupleExpr -> exprList.allMaybe(RsExpr::isPure)
        is RsFieldExpr -> expr.isPure()
        is RsParenExpr -> expr.isPure()
        is RsBreakExpr, is RsContExpr, is RsRetExpr, is RsTryExpr -> false   // Changes execution flow
        is RsPathExpr, is RsQualPathExpr, is RsLitExpr, is RsUnitExpr -> true

    // TODO: more complex analysis of blocks of code and search of implemented traits
        is RsBlockExpr, // Have to analyze lines, very hard case
        is RsCastExpr,  // `expr.isPure()` maybe not true, think about side-effects, may panic while cast
        is RsCallExpr,  // All arguments and function itself must be pure, very hard case
        is RsForExpr,   // Always return (), if pure then can be replaced with it
        is RsIfExpr,
        is RsIndexExpr, // Index trait can be overloaded, can panic if out of bounds
        is RsLambdaExpr,
        is RsLoopExpr,
        is RsMacroExpr,
        is RsMatchExpr,
        is RsMethodCallExpr,
        is RsRangeExpr,
        is RsUnaryExpr, // May be overloaded
        is RsWhileExpr -> null
        else -> null
    }
}

/**
 * Enum class representing unary operator in rust.
 */
enum class UnaryOperator {
    REF, // `&a`
    REF_MUT, // `&mut a`
    DEREF, // `*a`
    MINUS, // `-a`
    NOT, // `!a`
    BOX, // `box a`
}

/**
 * Operator of current psi node with unary operation.
 *
 * The result can be [REF] (`&`), [REF_MUT] (`&mut`),
 * [DEREF] (`*`), [MINUS] (`-`), [NOT] (`!`),
 * [BOX] (`box`) or `null` if none of these.
 */
val RsUnaryExpr.operatorType: UnaryOperator?
    get() = when {
        this.and != null -> UnaryOperator.REF
        this.mut != null -> UnaryOperator.REF_MUT
        this.mul != null -> UnaryOperator.DEREF
        this.minus != null -> UnaryOperator.MINUS
        this.excl != null -> UnaryOperator.NOT
        this.box != null -> UnaryOperator.BOX
        else -> null
    }

/**
 * Simplifies a boolean expression if can.
 *
 * @return `(expr, result)` where `expr` is a resulting expression,
 *         `result` is true if an expression was actually simplified.
 */
fun RsExpr.simplifyBooleanExpression(): Pair<RsExpr, Boolean> {
    if (this is RsLitExpr)
        return this to false
    return this.evalBooleanExpression()?.let {
        createPsiElement(project, it) to true
    } ?: when (this) {
        is RsBinaryExpr -> {
            val (leftExpr, leftSimplified) = left.simplifyBooleanExpression()
            val (rightExpr, rightSimplified) = right!!.simplifyBooleanExpression()
            if (leftExpr is RsLitExpr) {
                simplifyBinaryOperation(this, leftExpr, rightExpr, project)?.let {
                    return it to true
                }
            }
            if (rightExpr is RsLitExpr) {
                simplifyBinaryOperation(this, rightExpr, leftExpr, project)?.let {
                    return it to true
                }
            }
            if (leftSimplified)
                this.left.replace(leftExpr)
            if (rightSimplified)
                this.right!!.replace(rightExpr)
            this to (leftSimplified || rightSimplified)
        }
        else ->
            this to false
    }
}

private fun simplifyBinaryOperation(op: RsBinaryExpr, const: RsLitExpr, expr: RsExpr, project: Project): RsExpr? {
    return const.boolLiteral?.let {
        when (op.operatorType) {
            ANDAND ->
                when (it.text) {
                    "true" -> expr
                    "false" -> createPsiElement(project, "false")
                    else -> null
                }
            OROR ->
                when (it.text) {
                    "true" -> createPsiElement(project, "true")
                    "false" -> expr
                    else -> null
                }
            else ->
                null
        }
    }
}

/**
 * Evaluates a boolean expression if can.
 *
 * @return result of evaluation or `null` if can't simplify or
 *         if it is not a boolean expression.
 */
fun RsExpr.evalBooleanExpression(): Boolean? {
    return when (this) {
        is RsLitExpr ->
            (kind as? RsLiteralKind.Boolean)?.value

        is RsBinaryExpr -> when (operatorType) {
            ANDAND -> {
                val lhs = left.evalBooleanExpression() ?: return null
                if (!lhs) return false
                val rhs = right?.evalBooleanExpression() ?: return null
                lhs && rhs
            }
            OROR -> {
                val lhs = left.evalBooleanExpression() ?: return null
                if (lhs) return true
                val rhs = right?.evalBooleanExpression() ?: return null
                lhs || rhs
            }
            RsElementTypes.XOR -> {
                val lhs = left.evalBooleanExpression() ?: return null
                val rhs = right?.evalBooleanExpression() ?: return null
                lhs xor rhs
            }
            else -> null
        }

        is RsUnaryExpr -> when (operatorType) {
            UnaryOperator.NOT -> expr?.evalBooleanExpression()?.let { !it }
            else -> null
        }

        is RsParenExpr -> expr.evalBooleanExpression()

        else -> null
    }
}

private fun createPsiElement(project: Project, value: Any) = RsPsiFactory(project).createExpression(value.toString())
