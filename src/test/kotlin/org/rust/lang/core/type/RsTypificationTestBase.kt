package org.rust.lang.core.type

import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.types.util.resolvedType

abstract class RsTypificationTestBase : RsTestBase() {
    override val dataPath: String get() = ""

    protected fun testExpr(@Language("Rust") code: String, description: String = "") {
        InlineFile(code)
        val (expr, expectedType) = findElementAndDataInEditor<RsExpr>()

        assertThat(expr.resolvedType.toString())
            .`as`(description)
            .isEqualTo(expectedType)
    }
}
