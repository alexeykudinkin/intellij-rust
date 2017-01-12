package org.rust.lang.core.resolve

class RsUseResolveTest : RsResolveTestBase() {

    fun testViewPath() = checkByCode("""
        mod foo {
            use ::bar::hello;
                       //^
        }

        pub mod bar {
            pub fn hello() { }
                   //X
        }
    """)

    fun testUsePath() = checkByCode("""
        fn foo() { }
          //X

        mod inner {
            use foo;

            fn inner() {
                foo();
               //^
            }
        }
    """)

    fun testChildFromParent() = checkByCode("""
        mod foo {
            // This visits `mod foo` twice during resolve
            use foo::bar::baz;

            pub mod bar {
                pub fn baz() {}
                     //X
            }

            fn main() {
                baz();
               //^
            }
        }
    """)

    fun testPathRename() = checkByCode("""
        fn foo() {}
          //X

        mod inner {
            use foo as bar;

            fn main() {
                bar();
               //^ 3
            }
        }
    """)

    fun testDeepRedirection() = checkByCode("""
        mod foo {
            pub fn a() {}
                 //X
            pub use bar::b as c;
            pub use bar::d as e;
        }

        mod bar {
            pub use foo::a as b;
            pub use foo::c as d;
        }

        fn main() {
            foo::e();
               //^ 21
        }
    """)

    fun testRelativeChild() = checkByCode("""
        mod a {
            use self::b::foo;
                        //^

            mod b {
                pub fn foo() {}
                      //X
            }
        }
    """)

    fun testViewPathGlobIdent() = checkByCode("""
        mod foo {
            use bar::{hello as h};
                      //^
        }

        pub mod bar {
            pub fn hello() { }
                  //X
        }
    """)

    fun testViewPathGlobSelf() = checkByCode("""
        mod foo {
            use bar::{self};
                     //^ 62
        }

        pub mod bar { }
               //X
    """)

    fun testViewPathGlobSelfFn() = checkByCode("""
        fn f() {}
         //X

        mod foo {
            // This looks strange, but is allowed by the Rust Language
            use f::{self};
                   //^
        }
    """)

    fun testUseGlobIdent() = checkByCode("""
        mod foo {
            pub fn hello() {}
                   //X
        }

        mod bar {
            use foo::{hello};

            fn main() {
                hello();
                //^
            }
        }
    """)

    fun testUseGlobSelf() = checkByCode("""
        mod foo {
            pub fn hello() {}
                   //X
        }

        mod bar {
            use foo::{self};

            fn main() {
                foo::hello();
                    //^
            }
        }
    """)

    fun testUseGlobAlias() = checkByCode("""
        mod foo {
            pub fn hello() {}
                   //X
        }

        mod bar {
            use foo::{hello as spam};

            fn main() {
                spam();
               //^
            }
        }
    """)

    fun testUseGlobRedirection() = checkByCode("""
        mod foo {
            pub fn a() {}
                 //X
            pub use bar::{b as c, d as e};
        }

        mod bar {
            pub use foo::{a as b, c as d};
        }

        use foo::e;

        fn main() {
            e();
          //^
        }
    """)

    fun testEnumVariant() = checkByCode("""
        mod foo {
            use bar::E::{X};
                       //^
        }

        mod bar {
            pub enum E {
                X
              //X
            }
        }
    """)

    fun testSuperPart() = checkByCode("""
        // resolve to the whole file
        //X

        fn foo() {}

        mod inner {
            use super::foo;
               //^
        }
    """)

    fun testWildcard() = checkByCode("""
        mod a {
            fn foo() {}
              //X
            fn bar() {}
        }

        mod b {
            use a::*;

            fn main() {
                foo();
                //^
            }
        }
    """)

    fun testSuperWildcard() = checkByCode("""
        fn foo() {}
          //X

        #[cfg(test)]
        mod tests {
            use super::*;

            fn test_foo() {
                foo();
               //^
            }
        }
    """)

    fun testTwoWildcards() = checkByCode("""
        mod a {
            pub fn foo() {}
        }

        mod b {
            pub fn bar() {}
                  //X
        }

        mod c {
            use a::*;
            use b::*;

            fn main() {
                bar()
               //^
            }
        }
    """)

    fun testNestedWildcards() = checkByCode("""
        mod a {
            pub fn foo(){}
                  //X
        }

        mod b {
            pub use a::*;
        }

        mod c {
            use b::*;

            fn main() {
                foo()
               //^
            }
        }
    """)

    fun testOnlyBraces() = checkByCode("""
        struct Spam;
              //X

        mod foo {
            use {Spam};

            fn main() {
                let _: Spam = unimplemented!();
                      //^
            }
        }
    """)

    fun testColonBraces() = checkByCode("""
        struct Spam;
              //X

        mod foo {
            use ::{Spam};

            fn main() {
                let _: Spam = unimplemented!();
                      //^
            }
        }
    """)

    fun testLocalUse() = checkByCode("""
        mod foo {
            pub struct Bar;
                     //X
        }

        fn main() {
            use foo::Bar;

            let _ = Bar;
                   //^
        }
    """)

    fun testWildcardPriority() = checkByCode("""
        mod a {
            pub struct S;
        }

        mod b {
            pub struct S;
                     //X
        }

        mod c {
            pub struct S;
        }

        use a::*;
        use b::S;
        use c::*;

        fn main() {
            let _ = S;
                  //^
        }
    """)

    //
    fun testUseSelfCycle() = checkByCode("""
         //X
         use self;
            //^
    """)

    fun testImportFromSelf() = checkByCode("""
         use self::{foo as bar};
         fn foo() {}
           //X
         fn main() { bar() }
                    //^
    """)

    fun testNoUse() = checkByCode("""
        fn foo() { }

        mod inner {
            fn inner() {
                foo();
               //^ unresolved
            }
        }
    """)

    fun testCycle() = checkByCode("""
        // This is a loop: it simultaneously defines and imports `foo`.
        use foo;
        use bar::baz;

        fn main() {
            foo();
           //^ unresolved
        }
    """)

    fun testUseGlobCycle() = checkByCode("""
        mod foo {
            pub use bar::{b as a};
        }

        mod bar {
            pub use foo::{a as b};

            fn main() {
                b();
              //^ unresolved
            }
        }
    """)

    fun testEmptyGlobList() = checkByCode("""
        mod foo {
            pub fn f() {}
        }

        mod inner {
            use foo::{};

            fn main() {
                foo::f();
               //^ unresolved
            }
        }
    """)

    fun testWildcardCycle() = checkByCode("""
        use inner::*;

        mod inner {
            use super::*;

            fn main() {
                foo()
               //^ unresolved
            }
        }
    """)

    fun testStarImportsDoNotLeak() = checkByCode("""
        fn foo() {}
        mod m {
            use super::*;
        }

        fn bar() {
            m::foo();
             //^ unresolved
        }
    """)

    fun testCircularMod() = checkByCode("""
        use baz::bar;
               //^ unresolved

        // This "self declaration" should not resolve
        // but it once caused a stack overflow in the resolve.
        mod circular_mod;
    """)

    // This won't actually fail if the resolve is O(N^2) or worse,
    // but this is a helpful test for debugging!
    fun testQuadraticBehavior() = checkByCode("""
        use self::a::*;
        use self::b::*;
        use self::c::*;
        use self::d::*;

        const X1: i32 = 0;
        mod a {
            pub const X2: i32 = ::X1;
        }
        mod b {
            pub const X3: i32 = ::X1;
        }
        mod c {
            pub const X4: i32 = ::X1;
        }
        mod d {
            pub const X5: i32 = ::X1;
                    //X
        }

        const Z: i32 = X5;
                     //^
    """)
}
