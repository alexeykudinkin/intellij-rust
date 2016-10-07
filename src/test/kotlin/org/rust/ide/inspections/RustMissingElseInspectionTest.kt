package org.rust.ide.inspections

/**
 * Tests for Missing Else inspection.
 */
class RustMissingElseInspectionTest : RustInspectionsTestBase() {

    override val dataPath = ""

    fun testSimple() = checkByText<RustMissingElseInspection>("""
        fn main() {
            if true {
            }<warning descr="Suspicious if. Did you mean `else if`?"> if </warning>true {
            }
        }
    """)

    fun testNoSpaces() = checkByText<RustMissingElseInspection>("""
        fn main() {
            let a = 10;
            if true {
            }<warning descr="Suspicious if. Did you mean `else if`?">if</warning>(a > 10){
            }
        }
    """)

    fun testWideSpaces() = checkByText<RustMissingElseInspection>("""
        fn main() {
            let a = 10;
            if true {
            }<warning descr="Suspicious if. Did you mean `else if`?">   if    </warning>(a > 10) {
            }
        }
    """)

    fun testComments() = checkByText<RustMissingElseInspection>("""
        fn main() {
            let a = 10;
            if true {
            }<warning descr="Suspicious if. Did you mean `else if`?"> /* commented  */ /* else */ if </warning>a > 10 {
            }
        }
    """)

    fun testNotLastExpr() = checkByText<RustMissingElseInspection>("""
        fn main() {
            let a = 10;
            if a > 5 {
            }<warning descr="Suspicious if. Did you mean `else if`?"> if </warning>a > 10{
            }
            let b = 20;
        }
    """)

    fun testHandlesBlocksWithNoSiblingsCorrectly() = checkByText<RustMissingElseInspection>("""
        fn main() {if true {}}
    """)

    fun testNotAppliedWhenLineBreakExists() = checkByText<RustMissingElseInspection>("""
        fn main() {
            if true {}
            if true {}
        }
    """)

    fun testNotAppliedWhenTheresNoSecondIf() = checkByText<RustMissingElseInspection>("""
        fn main() {
            if {
                92;
            }
            {}
        }
    """)

    fun testFix() = checkFixByText<RustMissingElseInspection>("Change to `else if`", """
        fn main() {
            let a = 10;
            if a > 7 {
            }<warning descr="Suspicious if. Did you mean `else if`?"> i<caret>f </warning>a > 14 {
            }
        }
    """, """
        fn main() {
            let a = 10;
            if a > 7 {
            } else if a > 14 {
            }
        }
    """)

    fun testFixPreservesComments() = checkFixByText<RustMissingElseInspection>("Change to `else if`", """
        fn main() {
            let a = 10;
            if a > 7 {
            }<warning descr="Suspicious if. Did you mean `else if`?"> /* comment */<caret> if /* ! */ </warning>a > 14 {
            }
        }
    """, """
        fn main() {
            let a = 10;
            if a > 7 {
            } /* comment */ else if /* ! */ a > 14 {
            }
        }
    """)
}
