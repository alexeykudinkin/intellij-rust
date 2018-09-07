/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.util.Processor
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.searchForImplementations

abstract class RsImplsSearchBase : QueryExecutorBase<PsiElement, DefinitionsScopedSearch.SearchParameters>(true) {
    protected fun processQueryInner(
        queryParameters: DefinitionsScopedSearch.SearchParameters,
        consumer: Processor<PsiElement>
    ) {
        val psi = queryParameters.element
        val query = when (psi) {
            is RsTraitItem -> psi.searchForImplementations()
            is RsStructItem -> psi.searchForImplementations()
            is RsEnumItem -> psi.searchForImplementations()
            else -> return
        }
        query.forEach(Processor { consumer.process(it) })
    }
}
