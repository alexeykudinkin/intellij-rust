package org.rust.lang.core.completion

import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import org.assertj.core.api.Assertions.assertThat

class RsCompletionAutoPopupTest : RsCompletionTestBase() {
    override val dataPath: String get() = ""

    private lateinit var tester: CompletionAutoPopupTester

    override fun setUp() {
        super.setUp()
        tester = CompletionAutoPopupTester(myFixture)
    }

    override fun invokeTestRunnable(runnable: Runnable) {
        tester.runWithAutoPopupEnabled(runnable)
    }

    override fun runInDispatchThread(): Boolean = false


    fun testPathAutoPopup() {
        val tester = CompletionAutoPopupTester(myFixture)
        myFixture.configureByText("main.rs", """
            enum Foo { Bar, Baz}
            fn main() {
                let _ = Foo<caret>
            }
        """)
        tester.typeWithPauses("::")
        assertThat(tester.lookup).isNotNull()
    }

}

