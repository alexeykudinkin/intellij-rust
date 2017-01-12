package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.postfix.templates.LanguagePostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import org.assertj.core.api.Assertions
import org.intellij.lang.annotations.Language
import org.rust.lang.RsLanguage
import org.rust.lang.RsTestBase

abstract class PostfixTemplateTest(val postfixTemplate: PostfixTemplate) : RsTestBase() {
    override val dataPath = ""

    protected fun doTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        checkSyntaxErrors: Boolean = true
    ) {

        InlineFile(before).withCaret()
        checkApplicability(before, true)
        myFixture.type('\t')
        if (checkSyntaxErrors) myFixture.checkHighlighting(false, false, false)

        myFixture.checkResult(replaceCaretMarker(after))
    }

    protected fun doTestNotApplicable(@Language("Rust") testCase: String) {
        InlineFile(testCase).withCaret()
        checkApplicability(testCase, false)
    }

    private fun checkApplicability(testCase: String, isApplicable: Boolean) {
        val provider = LanguagePostfixTemplate.LANG_EP.allForLanguage(RsLanguage)
            .first { descriptor ->
                descriptor.templates.any { template ->
                    template.javaClass == this.postfixTemplate.javaClass
                }
            }

        Assertions.assertThat(
            PostfixLiveTemplate.isApplicableTemplate(
                provider,
                postfixTemplate.key,
                myFixture.file,
                myFixture.editor
            )
        )
            .withFailMessage(
                "postfixTemplate %s be applicable to given case:\n\n%s",
                if (isApplicable) "should" else "shouldn't",
                testCase
            )
            .isEqualTo(isApplicable)
    }
}
