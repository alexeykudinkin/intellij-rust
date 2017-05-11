package org.rust.lang.core.resolve

import org.intellij.lang.annotations.Language
import org.rust.lang.core.psi.ext.RsReferenceElement


class RsMultiResolveTest : RsResolveTestBase() {
    fun testStructExpr() = doTest("""
        struct S { foo: i32, foo: () }
        fn main() {
            let _ = S { foo: 1 };
                       //^
        }
    """)

    fun testFieldExpr() = doTest("""
        struct S { foo: i32, foo: () }
        fn f(s: S) {
            s.foo
             //^
        }
    """)

    //FIXME: should resolve to a single mut method!
    fun `test auto deref and deref mut`() = doTest("""
        #[lang = "deref"]
        trait Deref { type Target; }
        #[lang = "deref_mut"]
        trait DerefMut: Deref {  }

        struct A;
        struct B;

        impl Deref for A { type Target = B; }
        impl DerefMut for A {  }

        impl B { fn bar(&self) {} }
        impl B { fn bar(&mut self) {} }
                   //X

        fn foo(mut a: A) {
            a.bar()
            //^
        }
    """)

    fun testUseMultiReference() = doTest("""
        use m::foo;
              //^

        mod m {
            fn foo() {}
            mod foo {}
        }
    """)

    //FIXME: should resolve to a single  non ref method!
    fun testNonInherentImpl2() = doTest("""
        trait T { fn foo(&self) { println!("Hello"); } }

        struct S;

        impl T for S { fn foo(&self) { println!("non ref"); } }

        impl<'a> T for &'a S { fn foo(&self) { println!("ref"); } }
                                 //X

        fn main() {
            let x: &S = &S;
            x.foo()
              //^
        }
    """)

    private fun doTest(@Language("Rust") code: String) {
        InlineFile(code)
        val ref = findElementInEditor<RsReferenceElement>().reference
        check(ref.multiResolve().size == 2) {
            "Expected 2 variants, got ${ref.multiResolve()}"
        }
    }
}
