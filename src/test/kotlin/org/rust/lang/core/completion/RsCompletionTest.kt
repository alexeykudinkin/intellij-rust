/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsCompletionTest : RsCompletionTestBase() {

    override val dataPath = "org/rust/lang/core/completion/fixtures"

    fun testLocalVariable() = checkSingleCompletion("quux", """
        fn foo(quux: i32) { qu/*caret*/ }
    """)

    fun testFunctionCall() = checkSingleCompletion("frobnicate()", """
        fn frobnicate() {}

        fn main() { frob/*caret*/ }
    """)

    fun testFunctionWithParens() = checkSingleCompletion("frobnicate", """
        fn frobnicate() {}

        fn main() { frob/*caret*/() }
    """)

    fun testPath() = checkSingleCompletion("foo::bar::frobnicate()", """
        mod foo {
            mod bar {
                fn frobnicate() {}
            }
        }

        fn frobfrobfrob() {}

        fn main() {
            foo::bar::frob/*caret*/
        }
    """)

    fun testAnonymousItemDoesNotBreakCompletion() = checkSingleCompletion("frobnicate()", """
        extern "C" { }

        fn frobnicate() {}

        fn main() {
            frob/*caret*/
        }
    """)

    fun `test use glob`() = checkSingleCompletion("quux", """
        mod foo {
            pub fn quux() {}
        }

        use self::foo::{q/*caret*/};

        fn main() {}
    """)

    fun `test use glob global`() = checkSingleCompletion("Foo", """
        pub struct Foo;

        mod m {
            use {F/*caret*/};
        }
    """)

    fun testUseItem() = checkSingleCompletion("quux", """
        mod foo {
            pub fn quux() {}
        }

        use self::foo::q/*caret*/;

        fn main() {}
    """)

    fun testWildcardImports() = checkSingleCompletion("transmogrify()", """
        mod foo {
            fn transmogrify() {}
        }

        fn main() {
            use self::foo::*;

            trans/*caret*/
        }
    """)

    fun testShadowing() = checkSingleCompletion("foobar", """
        fn main() {
            let foobar = "foobar";
            let foobar = foobar.to_string();
            foo/*caret*/
        }
    """)

    fun testCompleteAlias() = checkSingleCompletion("frobnicate()", """
        mod m {
            pub fn transmogrify() {}
        }

        use self::m::{transmogrify as frobnicate};

        fn main() {
            frob/*caret*/
        }
    """)

    fun testCompleteSelfType() = checkSingleCompletion("Self", """
        trait T {
            fn foo() -> Se/*caret*/
        }
    """)

    fun testStructField() = checkSingleCompletion("foobarbaz", """
        struct S {
            foobarbaz: i32
        }
        fn main() {
            let _ = S { foo/*caret*/ };
        }
    """)

    fun testEnumField() = checkSingleCompletion("bazbarfoo", """
        enum E {
            X {
                bazbarfoo: i32
            }
        }
        fn main() {
            let _ = E::X { baz/*caret*/ }
        }
    """)

    fun testLocalScope() = checkNoCompletion("""
        fn foo() {
            let x = spam/*caret*/;
            let spamlot = 92;
        }
    """)

    fun testWhileLet() = checkNoCompletion("""
        fn main() {
            while let Some(quazimagnitron) = quaz/*caret*/ { }
        }
    """)


    fun testAliasShadowsOriginalName() = checkNoCompletion("""
        mod m {
            pub fn transmogrify() {}
        }

        use self::m::{transmogrify as frobnicate};

        fn main() {
            trans/*caret*/
        }
    """)

    fun testCompletionRespectsNamespaces() = checkNoCompletion("""
        fn foobar() {}

        fn main() {
            let _: foo/*caret*/ = unimplemented!();
        }
    """)

    fun testChildFile() = checkByDirectory {
        openFileInEditor("main.rs")
        executeSoloCompletion()
    }

    fun testParentFile() = checkByDirectory {
        openFileInEditor("foo.rs")
        executeSoloCompletion()
    }

    fun testParentFile2() = checkByDirectory {
        openFileInEditor("foo/mod.rs")
        executeSoloCompletion()
    }

    fun testCallCaretPositionNoArguments() = checkByText("""
        fn frobnicate() {}
        fn main() {
            frob/*caret*/
        }
    """, """
        fn frobnicate() {}
        fn main() {
            frobnicate()/*caret*/
        }
    """) { executeSoloCompletion() }

    fun testCallCaretPositionWithArguments() = checkByText("""
        fn frobnicate(foo: i32, bar: String) {}
        fn main() {
            frob/*caret*/
        }
    """, """
        fn frobnicate(foo: i32, bar: String) {}
        fn main() {
            frobnicate(/*caret*/)
        }
    """) { executeSoloCompletion() }

    fun testCallStructMethod() = checkContainsCompletion("some_fn", """
        struct SomeStruct { }
        impl SomeStruct {
            fn some_fn(&self) {}
        }
        fn main() {
            let v = SomeStruct { };
            v./*caret*/
        }
    """)

    fun testCallEnumMethod() = checkContainsCompletion("some_fn", """
        enum SomeEnum { Var1, Var2 }
        impl SomeEnum {
            fn some_fn(&self) {}
        }
        fn main() {
            let v = SomeEnum::Var1 ;
            v./*caret*/
        }
    """)

    fun testCallTraitImplForStructMethod() = checkContainsCompletion("some_fn", """
        trait SomeTrait { fn some_fn(&self); }
        struct SomeStruct { }
        impl SomeTrait for SomeStruct {
            fn some_fn(&self) {}
        }
        fn main() {
            let v = SomeStruct { };
            v./*caret*/
        }
    """)

    fun testCallTraitImplForEnumMethod() = checkContainsCompletion("some_fn", """
        trait SomeTrait { fn some_fn(&self); }
        enum SomeEnum { Var1, Var2 }
        impl SomeTrait for SomeEnum {
            fn some_fn(&self) {}
        }
        fn main() {
            let v = SomeEnum::Var1 ;
            v./*caret*/
        }
    """)

    fun testEnumVariant() = checkSingleCompletion("BAZBAR", """
        enum Foo {
            BARBOO,
            BAZBAR
        }
        fn main() {
            let _ = Foo::BAZ/*caret*/
        }
    """)

    fun testEnumVariantWithTupleFields() = checkSingleCompletion("Foo::BARBAZ()", """
        enum Foo {
            BARBAZ(f64)
        }
        fn main() {
            let _ = Foo::BAR/*caret*/
        }
    """)

    fun testEnumVariantWithTupleFieldsInUseBlock() = checkSingleCompletion("BARBAZ", """
        enum Foo {
            BARBAZ(f64)
        }
        fn main() {
            use Foo::BAR/*caret*/
        }
    """)

    fun testEnumVariantWithBlockFields() = checkSingleCompletion("Foo::BARBAZ {}", """
        enum Foo {
            BARBAZ {
                foo: f64
            }
        }
        fn main() {
            let _ = Foo::BAR/*caret*/
        }
    """)

    fun testEnumVariantWithBlockFieldsInUseBlock() = checkSingleCompletion("BARBAZ", """
        enum Foo {
            BARBAZ {
                foo: f64
            }
        }
        fn main() {
            use Foo::{BAR/*caret*/}
        }
    """)

    fun testTypeNamespaceIsCompletedForPathHead() = checkSingleCompletion("FooBar", """
        struct FooBar { f: i32 }

        fn main() {
            Foo/*caret*/
        }
    """)

    // issue #1182
    fun testAssociatedTypeCompletion() = checkSingleCompletion("Bar", """
        trait Foo {
            type Bar;
            fn foo(bar: Self::Ba/*caret*/);
        }
    """)

    // issue #1182
    fun testAssociatedTypeSuggestion() = checkContainsCompletion("Bar", """
        trait Foo {
            type Bar;
            fn foo(bar: Self::/*caret*/);
        }
    """)

    // issue #1182
    fun testAssociatedTypeSuggestionWithReference() = checkContainsCompletion("Bar", """
        trait Foo {
            type Bar;
            fn foo(bar: &mut Self::/*caret*/);
        }
    """)

    fun `test complete enum variants 1`() = checkSingleCompletion("BinOp", """
        enum Expr { Unit, BinOp(Box<Expr>, Box<Expr>) }
        fn foo(e: Expr) {
            use self::Expr::*;
            match e {
                Bi/*caret*/
            }
        }
    """)

    fun `test complete enum variants 2`() = checkSingleCompletion("Unit", """
        enum Expr { Unit, BinOp(Box<Expr>, Box<Expr>) }
        fn foo(e: Expr) {
            use self::Expr::*;
            match e {
                Un/*caret*/
            }
        }
    """)

    fun `test should not complete for test functions`() = checkNoCompletion("""
        #[test]
        fn foobar() {}

        fn main() {
            foo/*caret*/
        }
    """)

    fun `test complete macro`() = checkSingleCompletion("foo_bar", """
        macro_rules! foo_bar { () => () }
        fn main() {
            fo/*caret*/
        }
    """)

    fun `test complete outer macro`() = checkSingleCompletion("foo_bar", """
        macro_rules! foo_bar { () => () }
        fo/*caret*/
        fn main() {
        }
    """)
}
