/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.structure

import com.intellij.ide.navigationToolbar.StructureAwareNavBarModelExtension
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.RsAbstractable
import org.rust.lang.core.psi.ext.RsElement
import javax.swing.Icon

/** Shows nav bar for items from structure view [RsStructureViewModel] */
class RsNavBarModelExtension : StructureAwareNavBarModelExtension() {
    override val language: Language = RsLanguage

    override fun createModel(file: PsiFile, editor: Editor?): StructureViewModel? {
        if (file !is RsFile) return null
        return RsStructureViewModel(editor, file, expandMacros = false)
    }

    override fun getPresentableText(item: Any?): String? {
        val element = item as? RsElement ?: return null
        if (element is RsFile) {
            return element.name
        }

        val provider = RsBreadcrumbsInfoProvider()
        return provider.getBreadcrumb(element)
    }

    /** When [getPresentableText] returns null, [PsiElement.getText] will be used, and we want to avoid it */
    override fun getLeafElement(dataContext: DataContext): PsiElement? {
        val leafElement = super.getLeafElement(dataContext) as? RsElement ?: return null
        if (RsBreadcrumbsInfoProvider().getBreadcrumb(leafElement) == null) return null
        return leafElement
    }

    override fun getIcon(obj: Any): Icon? {
        return if (obj is RsAbstractable) {
            // The code mostly copied from `NavBarPresentation.getIcon`. The only reason to override it here
            // is setting `allowNameResolution = false` in order to avoid UI freezes
            var icon = ReadAction.compute<Icon?, RuntimeException> {
                if (obj.isValid) obj.getIcon(0, allowNameResolution = false) else null
            }

            val maxDimension = JBUI.scale(16 * 2)
            if (icon != null && (icon.iconHeight > maxDimension || icon.iconWidth > maxDimension)) {
                icon = IconUtil.cropIcon(icon, maxDimension, maxDimension)
            }
            return icon
        } else {
            super.getIcon(obj)
        }
    }
}
