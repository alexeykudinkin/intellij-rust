/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsPatternArgumentInForeignFunctionTest: RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {
    fun `test E0130 tuple struct pattern`() = checkByText("""
        struct MyInt(i32);

        extern "C" {
            fn foo(<error descr="Patterns aren't allowed in foreign function declarations [E0130]">MyInt(x): MyInt</error>);
        }
    """)

    fun `test E0130 array pattern`() = checkByText("""
        extern "C" {
            fn foo(<error descr="Patterns aren't allowed in foreign function declarations [E0130]">[x, ..]: [i32; 3]</error>);
        }
    """)

    fun `test E0130 allow all cases without extern`() = checkByText("""
        struct MyInt(i32);

        fn foo(MyInt(x): MyInt) {}

        struct Point {
            x: i32,
            y: i32,
        }

        fn bar(Point { x, y }: Point) {}

        fn baz([x, ..]: [i32; 3]) {}

        fn qux(a: i32) {}
    """)

    fun `test E0130 compiler test -- no-patterns-in-args`() = checkByText("""
        extern "C" {
            fn f1(<error descr="Patterns aren't allowed in foreign function declarations [E0130]">mut arg: u8</error>);
            fn f2(<error descr="Patterns aren't allowed in foreign function declarations [E0130]">&arg: u8</error>);
            fn f3(<error descr="Patterns aren't allowed in foreign function declarations [E0130]">arg @ _: u8</error>);
        }
    """)

    fun `test E0130 compiler test -- issue-10877`() = checkByText("""
        extern "C" {
            fn foo(<error descr="Patterns aren't allowed in foreign function declarations [E0130]">1: ()</error>);
            fn bar(<error descr="Patterns aren't allowed in foreign function declarations [E0130]">(): isize</error>);
            fn baz(<error descr="Patterns aren't allowed in foreign function declarations [E0130]">Foo { x }: isize</error>);
            fn qux(<error descr="Patterns aren't allowed in foreign function declarations [E0130]">(x, y): ()</error>);
            fn this_is_actually_ok(a: usize);
            fn and_so_is_this(_: usize);
        }
    """)
}
