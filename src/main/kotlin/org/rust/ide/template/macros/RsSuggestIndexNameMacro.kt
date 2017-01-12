package org.rust.ide.template.macros

import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import com.intellij.codeInsight.template.macro.MacroBase
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RsCompositeElement
import org.rust.lang.core.psi.RsItemElement
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.innerDeclarations

class RsSuggestIndexNameMacro : MacroBase("rustSuggestIndexName", "rustSuggestIndexName()") {
    override fun calculateResult(params: Array<out Expression>, context: ExpressionContext, quick: Boolean): Result? {
        if (params.isNotEmpty()) return null
        val pivot = context.psiElementAtStartOffset?.parentOfType<RsCompositeElement>() ?: return null
        val pats = getPatBindingNamesVisibleAt(pivot)
        return ('i'..'z')
            .map(Char::toString)
            .find { it !in pats }
            ?.let(::TextResult)
    }
}

private fun getPatBindingNamesVisibleAt(pivot: RsCompositeElement): Set<String> =
    innerDeclarations(pivot,
        // we are only interested in local scopes
        stop = { scope -> scope is RsItemElement }
    )
        .mapNotNull { (it.element as? RsPatBinding)?.name }
        .toHashSet()
