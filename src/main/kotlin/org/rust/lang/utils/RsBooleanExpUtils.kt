package org.rust.lang.utils

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*

fun RsBinaryExpr.negateToString(): String {
    val lhs = left.text
    val rhs = right?.text ?: ""
    val op = when (operatorType) {
        RsTokenElementTypes.EQEQ -> "!="
        RsTokenElementTypes.EXCLEQ -> "=="
        RsTokenElementTypes.GT -> "<="
        RsTokenElementTypes.LT -> ">="
        RsTokenElementTypes.GTEQ -> "<"
        RsTokenElementTypes.LTEQ -> ">"
        else -> null
    }
    return if (op != null) "$lhs $op $rhs" else "!($text)"
}

fun PsiElement.isNegation(): Boolean =
    this is RsUnaryExpr && excl != null

fun PsiElement.negate(): PsiElement {
    val psiFactory = RustPsiFactory(project)
    return when {
        isNegation() -> {
            val inner = (this as RsUnaryExpr).expr!!
            (inner as? RsParenExpr)?.expr ?: inner
        }

        this is RsBinaryExpr ->
            psiFactory.createExpression(negateToString())

        this is RsParenExpr || this is RsPathExpr || this is RsCallExpr ->
            psiFactory.createExpression("!$text")

        else ->
            psiFactory.createExpression("!($text)")
    }
}
