package org.rust.lang.core.type

import com.intellij.psi.util.PsiTreeUtil
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.rust.lang.core.psi.RustTypeElement
import org.rust.lang.core.types.util.resolvedType

class RustTypeResolvingTest: RustTypificationTestBase() {
    fun testPath() = testType("""
        struct Spam;

        fn main() {
            let _: Spam = Spam;
                 //^ Spam
        }
    """)

    fun testUnit() = testType("""
        fn main() {
            let _: () = ();
                 //^ ()
        }
    """)

    fun testTuple() = testType("""
        struct S;
        struct T;
        fn main() {
            let _: (S, T) = (S, T);
                 //^ (S, T)
        }
    """)

    fun testQualifiedPath() = testType("""
        trait T {
            type Assoc;
        }

        struct S;

        impl T for S {
            type Assoc = S;
        }

        fn main() {
            let _: <S as T>::Assoc = S;
                 //^ <unknown>
        }
    """)

    fun testEnum() = testType("""
        enum E { X }

        fn main() {
            let _: E = E::X;
                 //^ E
        }
    """)

    fun testTypeItem() = testType("""
        enum E { X }

        type A = E;

        fn main() {
            let _: E = A::X;
                 //^ E
        }
    """)


    /**
     * Checks the type of the element in [code] pointed to by `//^` marker.
     */
    private fun testType(@Language("Rust") code: String) {
        val (elementAtCaret, expectedType) = configureAndFindElement(code)
        val typeAtCaret = requireNotNull(
            PsiTreeUtil.getTopmostParentOfType(elementAtCaret, RustTypeElement::class.java)
        ) { "No type at caret:\n$code" }

        assertThat(typeAtCaret.resolvedType.toString())
            .isEqualTo(expectedType)
    }
}

