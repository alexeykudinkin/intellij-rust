package org.rust.lang.core.resolve

import org.rust.lang.core.psi.ext.ArithmeticOp

class RsTypeAwareResolveTest : RsResolveTestBase() {
    fun testSelfMethodCallExpr() = checkByCode("""
        struct S;

        impl S {
            fn bar(self) { }
              //X

            fn foo(self) { self.bar() }
                              //^
        }
    """)

    fun `test trait impl method`() = checkByCode("""
        trait T { fn foo(&self); }
        struct S;
        impl T for S { fn foo(&self) {} }
                         //X
        fn foo(s: S) {
            s.foo()
        }    //^
    """)

    fun `test trait default method`() = checkByCode("""
        trait T { fn foo(&self) {} }
                    //X
        struct S;
        impl T for S { }

        fn foo(s: S) {
            s.foo()
        }    //^
    """)

    fun `test trait overriden default method`() = checkByCode("""
        trait T { fn foo(&self) {} }

        struct S;
        impl T for S { fn foo(&self) {} }
                         //X
        fn foo(s: S) {
            s.foo()
        }    //^
    """)

    fun testMethodReference() = checkByCode("""
    //- main.rs
        mod x;
        use self::x::Stdin;

        fn main() {
            Stdin::read_line;
                     //^
        }

    //- x.rs
        pub struct Stdin { }

        impl Stdin {
            pub fn read_line(&self) { }
                   //X
        }
    """)

    fun testMethodCallOnTraitObject() = stubOnlyResolve("""
    //- main.rs
        mod aux;
        use aux::T;

        fn call_virtually(obj: &T) { obj.virtual_function() }
                                                //^ aux.rs

    //- aux.rs
        trait T {
            fn virtual_function(&self) {}
        }
    """)

    fun testMethodInherentVsTraitConflict() = checkByCode("""
        struct Foo;
        impl Foo {
            fn bar(&self) {}
               //X
        }

        trait Bar {
            fn bar(&self);
        }
        impl Bar for Foo {
            fn bar(&self) {}
        }

        fn main() {
            let foo = Foo;
            foo.bar();
               //^
        }
    """)

    fun testSelfFieldExpr() = checkByCode("""
        struct S { x: f32 }
                 //X

        impl S {
            fn foo(&self) { self.x; }
                               //^
        }
    """)

    fun testFieldExpr() = stubOnlyResolve("""
    //- main.rs
        mod aux;
        use aux::S;
        fn main() {
            let s: S = S { x: 0. };
            s.x;
            //^ aux.rs
        }

    //- aux.rs
        struct S { x: f32 }
    """)

    fun testTupleFieldExpr() = checkByCode("""
        struct T;
        impl T { fn foo(&self) {} }
                  //X

        struct S(T);

        impl S {
            fn foo(&self) {
                let s = S(92.0);
                s.0.foo();
                   //^
            }
        }
    """)

    fun testTupleFieldExprOutOfBounds() = checkByCode("""
        struct S(f64);

        impl S {
            fn foo(&self) {
                let s: S = S(92.0);
                s.92;
                //^ unresolved
            }
        }
    """)

    fun testTupleFieldExprSuffix() = checkByCode("""
        struct S(f64);

        impl S {
            fn foo(&self) {
                let s: S = S(92.0);
                s.0u32;
                //^ unresolved
            }
        }
    """)

    fun testNestedFieldExpr() = checkByCode("""
        struct Foo { bar: Bar }

        struct Bar { baz: i32 }
                    //X

        fn main() {
            let foo = Foo { bar: Bar { baz: 92 } };
            foo.bar.baz;
                  //^
        }
    """)


    fun testLetDeclCallExpr() = checkByCode("""
        struct S { x: f32 }
                 //X

        fn bar() -> S {}

        impl S {
            fn foo(&self) {
                let s = bar();

                s.x;
                //^
            }
        }
    """)

    fun testLetDeclMethodCallExpr() = checkByCode("""
        struct S { x: f32 }
                 //X

        impl S {
            fn bar(&self) -> S {}
            fn foo(&self) {
                let s = self.bar();
                s.x;
                //^
            }
        }
    """)

    fun testLetDeclPatIdentExpr() = checkByCode("""
        struct S { x: f32 }
                 //X

        impl S {
            fn foo(&self) {
                let s = S { x: 0. };

                s.x;
                //^
            }
        }
    """)

    fun testLetDeclPatTupExpr() = checkByCode("""
        struct S { x: f32 }
                 //X
        impl S {
            fn foo(&self) {
                let (_, s) = ((), S { x: 0. });

                s.x;
                //^
            }
        }
    """)

    fun testLetDeclPatStructExpr() = checkByCode("""
        struct S { x: f32 }

        impl S {
            fn foo(&self) {
                let S { x: x } = S { x: 0. };
                         //X
                x;
              //^
            }
        }
    """)

    fun testLetDeclPatStructExprComplex() = checkByCode("""
        struct S { x: f32 }
                 //X

        struct X { s: S }

        impl S {
            fn foo(&self) {
                let X { s: f } = X { s: S { x: 0. } };
                f.x;
                //^
            }
        }
    """)

    fun testAssociatedFnFromInherentImpl() = checkByCode("""
        struct S;

        impl S { fn test() { } }
                    //X

        fn main() { S::test(); }
                      //^
    """)

    fun testAssociatedFunctionInherentVsTraitConflict() = checkByCode("""
        struct Foo;
        impl Foo {
            fn bar() {}
               //X
        }

        trait Bar {
            fn bar();
        }
        impl Bar for Foo {
            fn bar() {}
        }

        fn main() {
            Foo::bar();
                //^
        }
    """)

    fun testHiddenInherentImpl() = checkByCode("""
        struct S;

        fn main() {
            let s: S = S;
            s.transmogrify();
                //^
        }

        mod hidden {
            use super::S;

            impl S { pub fn transmogrify(self) -> S { S } }
                            //X
        }
    """)

    fun testWrongInherentImpl() = checkByCode("""
        struct S;

        fn main() {
            let s: S = S;

            s.transmogrify();
                //^ unresolved
        }

        mod hidden {
            struct S;

            impl S { pub fn transmogrify(self) -> S { S } }
        }
    """)

    fun testImplGenericsStripped() = checkByCode("""
        struct S<FOO> { field: FOO }

        fn main() {
            let s: S = S;

            s.transmogrify();
                //^
        }

        impl<BAR> S<BAR> {
            fn transmogrify(&self) { }
                //X
        }
    """)


    fun testNonInherentImpl1() = checkByCode("""
        struct S;

        mod m {
            trait T { fn foo(); }
        }

        mod hidden {
            use super::S;
            use super::m::T;

            impl T for S { fn foo() {} }
                            //X
        }

        fn main() {
            use m::T;

            let _ = S::foo();
                     //^
        }
    """)

    fun testGenericParamMethodCall() = checkByCode("""
        trait Spam { fn eggs(&self); }
                        //X

        fn foo<T: Spam>(x: T) { x.eggs() }
                                  //^
    """)

    fun testGenericParamMethodCallWhere() = checkByCode("""
        trait Spam { fn eggs(&self); }
                        //X

        fn foo<T>(x: T) where T: Spam { x.eggs() }
                                          //^
    """)

    fun testSelfImplementsTrait() = checkByCode("""
        trait Foo {
            fn foo(&self);
              //X

            fn bar(&self) { self.foo(); }
                                //^
        }
    """)

    fun testSelfImplementsTraitFromBound() = checkByCode("""
        trait Bar {
            fn bar(&self);
             //X
        }
        trait Foo : Bar {
            fn foo(&self) { self.bar(); }
                                //^
        }
    """)

    fun testChainedBounds() = checkByCode("""
        trait A { fn foo(&self) {} }
                    //X
        trait B : A {}
        trait C : B {}
        trait D : C {}

        struct X;
        impl D for X {}

        fn bar<T: D>(x: T) { x.foo() }
                              //^
    """)

    fun testChainedBoundsCycle() = checkByCode("""
        trait A: B {}
        trait B: A {}

        fn bar<T: A>(x: T) { x.foo() }
                              //^ unresolved
    """)

    fun testCantImportMethods() = checkByCode("""
        mod m {
            pub enum E {}

            impl E {
                pub fn foo() {}
            }
        }

        use self::m::E::foo;
                        //^ unresolved
    """)

    fun testResultTryOperator() = checkByCode("""
        enum Result<T, E> { Ok(T), Err(E)}
        struct S { field: u32 }
                    //X
        fn foo() -> Result<S, ()> { unimplemented!() }

        fn main() {
            let s = foo()?;
            s.field;
            //^
        }
    """)

    fun testResultTryMacro() = checkByCode("""
        enum Result<T, E> { Ok(T), Err(E)}
        struct S { field: u32 }
                    //X
        fn foo() -> Result<S, ()> { unimplemented!() }

        fn main() {
            let s = try!(foo());
            s.field;
            //^
        }
    """)

    fun testResultUnwrap() = checkByCode("""
        enum Result<T, E> { Ok(T), Err(E)}

        impl<T, E: fmt::Debug> Result<T, E> {
            pub fn unwrap(self) -> T { unimplemented!() }
        }

        struct S { field: u32 }
                    //X
        fn foo() -> Result<S, ()> { unimplemented!() }

        fn main() {
            let s = foo().unwrap();
            s.field;
            //^
        }
    """)

    fun testResultTryOperatorWithAlias() = checkByCode("""
        enum Result<T, E> { Ok(T), Err(E)}

        mod io {
            pub struct IoError;
            pub type IoResult<T> = super::Result<T, IoError>;

            pub struct S { field: u32 }
                          //X

            pub fn foo() -> IoResult<S> { unimplemented!() }

        }

        fn main() {
            let s = io::foo()?;
            s.field;
              //^
        }
    """)

    fun testResultUnwrapWithAlias() = checkByCode("""
        enum Result<T, E> { Ok(T), Err(E)}
        impl<T, E: fmt::Debug> Result<T, E> {
            pub fn unwrap(self) -> T { unimplemented!() }
        }

        mod io {
            pub struct Error;
            pub type Result<T> = super::Result<T, Error>;
        }

        struct S { field: u32 }
                    //X
        fn foo() -> io::Result<S> { unimplemented!() }

        fn main() {
            let s = foo().unwrap();
            s.field;
              //^
        }
    """)

    fun testGenericFunctionCall() = checkByCode("""
        struct S;
        impl S { fn m(&self) {} }
                  //X
        fn f<T>(t: T) -> T { t }
        fn main() {
            f(S).m();
               //^
        }
    """)

    fun testMatchEnumTupleVariant() = checkByCode("""
        enum E { V(S) }
        struct S;

        impl S { fn frobnicate(&self) {} }
                    //X
        impl E {
            fn foo(&self) {
                match *self {
                    E::V(ref s) => s.frobnicate()
                }                   //^
            }
        }
    """)

    fun testStatic() = checkByCode("""
        struct S { field: i32 }
                    //X
        const FOO: S = S { field: 92 };

        fn main() {
            FOO.field;
        }       //^
    """)

    fun `test string slice resolve`() = checkByCode("""
        impl<T> &str {
            fn foo(&self) {}
              //X
        }

        fn main() {
            "test".foo();
                    //^
        }
    """)

    fun `test slice resolve`() = checkByCode("""
        impl<T> [T] {
            fn foo(&self) {}
              //X
        }

        fn main() {
            let x : [i32];
            x.foo()
             //^
        }
    """)

    fun `test iterator for loop resolve`() = checkByCode("""
        trait Iterator { type Item; fn next(&mut self) -> Option<Self::Item>; }

        struct S;
        impl S { fn foo(&self) {} }
                  //X
        struct I;
        impl Iterator for I {
            type Item = S;
            fn next(&mut self) -> Option<S> { None }
        }

        fn main() {
            for s in I {
                s.foo();
            }    //^
        }
    """)

    fun `test into iterator for loop resolve`() = checkByCode("""
        trait Iterator { type Item; fn next(&mut self) -> Option<Self::Item>; }
        trait IntoIterator {
            type Item;
            type IntoIter: Iterator<Item=Self::Item>;
            fn into_iter(self) -> Self::IntoIter;
        }

        struct S;
        impl S { fn foo(&self) {} }
                  //X
        struct I;
        impl Iterator for I {
            type Item = S;
            fn next(&mut self) -> Option<S> { None }
        }

        struct II;
        impl IntoIterator for II {
            type Item = S;
            type IntoIter = I;
            fn into_iter(self) -> Self::IntoIter { I }
        }

        fn main() {
            for s in II {
                s.foo()
            }   //^
        }
    """)

    fun `test impl not resolved by accident if struct name is the same as generic type`() = checkByCode("""
        struct S;
        trait Tr1{}
        trait Tr2{ fn some_fn(&self) {} }
        impl<S: Tr1> Tr2 for S {}
        fn main(v: S) {
            v.some_fn();
            //^ unresolved
        }
    """)

    fun `test single auto deref`() = checkByCode("""
        struct A;
        struct B;
        impl B { fn some_fn(&self) { } }
                    //X
        #[lang = "deref"]
        trait Deref { type Target; }
        impl Deref for A { type Target = B; }

        fn foo(a: A) {
            a.some_fn()
            //^
        }
    """)

    fun `test multiple auto deref`() = checkByCode("""
        struct A;
        struct B;
        struct C;
        impl C { fn some_fn(&self) { } }
                    //X
        #[lang = "deref"]
        trait Deref { type Target; }
        impl Deref for A { type Target = B; }
        impl Deref for B { type Target = C; }

        fn foo(a: A) {
            a.some_fn()
            //^
        }
    """)

    fun `test recursive auto deref`() = checkByCode("""
        #[lang = "deref"]
        trait Deref { type Target; }

        struct A;
        struct B;
        struct C;

        impl C { fn some_fn(&self) { } }
                    //X

        impl Deref for A { type Target = B; }
        impl Deref for B { type Target = C; }
        impl Deref for C { type Target = A; }

        fn foo(a: A) {
            // compiler actually bails with `reached the recursion limit while auto-dereferencing B`
            a.some_fn()
            //^
        }
    """)

    fun `test propagates trait type arguments`() = checkByCode("""
        trait I<T> {
            fn foo(&self) -> T;
        }

        struct S;
        impl S {
            fn bar(&self) {}
        }     //X

        fn baz<T: I<S>>(t: T) {
            t.foo().bar()
        }         //^
    """)

    fun `test indexing`() = checkByCode("""
        #[lang = "index"]
        trait Index<Idx: ?Sized> {
            type Output: ?Sized;
            fn index(&self, index: Idx) -> &Self::Output;
        }

        struct Container;
        struct Elem;
        impl Elem { fn foo(&self) {} }
                      //X

        impl Index<usize> for Container {
            type Output = Elem;
            fn index(&self, index: usize) -> &Elem { unimplemented!() }
        }

        fn bar(c: Container) {
            c[0].foo()
                //^
        }
    """)

    fun `test indexing with multiple impls`() = checkByCode("""
        #[lang = "index"]
        trait Index<Idx: ?Sized> {
            type Output: ?Sized;
            fn index(&self, index: Idx) -> &Self::Output;
        }

        struct Container;
        struct Elem1;
        impl Elem1 { fn foo(&self) {} }
        struct Elem2;
        impl Elem2 { fn foo(&self) {} }
                       //X

        impl Index<usize> for Container {
            type Output = Elem1;
            fn index(&self, index: usize) -> &Elem1 { unimplemented!() }
        }

        impl Index<f64> for Container {
            type Output = Elem2;
            fn index(&self, index: f64) -> &Elem2 { unimplemented!() }
        }

        fn bar(c: Container) {
            c[0.0].foo()
                  //^
        }
    """)

    fun `test simple method resolve for closure`() = checkByCode("""
        struct T;
        impl T {
            fn bar(&self) {}
             //X
        }

        fn foo<F: Fn(&T) -> ()>(f: F) {}

        fn main() {
            foo(|t| { t.bar(); })
        }              //^
    """)

    fun `test wrong closure parameter`() = checkByCode("""
        struct T;
        impl T {
            fn bar(&self) {}
        }

        fn foo<F: Fn(&T) -> ()>(f: F) {}

        fn main() {
            foo(None, |t| { t.bar(); })
        }                    //^ unresolved
    """)

    fun `test simple method resolve with where for closure`() = checkByCode("""
        struct T;
        impl T {
            fn bar(&self) {}
             //X
        }

        fn foo<F>(f: F) where F: Fn(&T) -> () {}

        fn main() {
            foo(|t| { t.bar(); })
        }              //^
    """)

    fun `test simple inner resolve for closure`() = checkByCode("""
        struct T;
        impl T {
            fn bar(&self) {}
        }

        fn foo<F: Fn(&T) -> ()>(f: F) {}

        fn main() {
            foo(|t| {
               //X
                t.bar();
              //^
            })
        }
    """)

    fun `test simple self resolve for closure`() = checkByCode("""
        struct T;
        impl T {
            fn bar(&self) {}
              //X
            fn foo<F: Fn(&T) -> ()>(&self, f: F) {}
        }

        fn main() {
            let t = T;
            t.foo(|e| { e.bar(); })
        }                //^
    """)

    fun `test multi self resolve for closure`() = checkByCode("""
        struct T;
        struct S;
        impl S {
            fn bar(&self) -> T {T}
        }
        impl T {
            fn bar(&self) {}
              //X
            fn foo<F: Fn(&T) -> ()>(&self, f: F) {}
        }

        fn main() {
            let t = S;
            t.bar().foo(|e| { e.bar(); })
        }                      //^
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/1269
    fun `test tuple field`() = checkByCode("""
        struct Foo;
        impl Foo {
            fn foo(&self) { unimplemented!() }
              //X
        }
        fn main() {
            let t = (1, Foo());
            t.1.foo();
               //^
        }
    """)

    fun `test simple generic function argument`() = checkByCode("""
        struct Foo<F>(F);
        struct Bar;
        impl Bar {
            fn bar(&self) { unimplemented!() }
              //X
        }
        fn foo<T>(xs: Foo<T>) -> T { unimplemented!() }
        fn main() {
            let x = foo(Foo(Bar()));
            x.bar();
             //^
        }
    """)

    fun `test complex generic function argument`() = checkByCode("""
        struct Foo<T1, T2>(T1, T2);
        enum Bar<T3> { V(T3) }
        struct FooBar<T4, T5>(T4, T5);
        struct S;

        impl S {
            fn bar(&self) { unimplemented!() }
              //X
        }

        fn foo<F1, F2, F3>(x: FooBar<Foo<F1, F2>, Bar<F3>>) -> Foo<F2, F3> { unimplemented!() }
        fn main() {
            let x = foo(FooBar(Foo(123, "foo"), Bar::V(S())));
            x.1.bar();
              //^
        }
    """)

    fun `test generic method argument`() = checkByCode("""
        struct Foo<F>(F);
        enum Bar<B> { V(B) }
        struct FooBar<E1, E2>(E1, E2);
        struct S;

        impl<T1> Foo<T1> {
            fn foo<T2>(&self, bar: Bar<T2>) -> FooBar<T1, T2> { unimplemented!() }
        }

        impl S {
            fn bar(&self) { unimplemented!() }
              //X
        }

        fn main() {
            let x = Foo(123).foo(Bar::V(S()));
            x.1.bar();
              //^
        }
    """)

    fun `test array to slice`() = checkByCode("""
        struct Foo;
        impl Foo {
            fn foo(&self) { unimplemented!() }
              //X
        }
        fn foo<T>(xs: &[T]) -> T { unimplemented!() }
        fn main() {
            let x = foo(&[Foo(), Foo()]);
            x.foo()
             //^
        }
    """)

    fun `test arithmetic operations`() {
        for ((traitName, itemName, sign) in ArithmeticOp.values()) {
            checkByCode("""
                #[lang = "$itemName"]
                pub trait $traitName<RHS=Self> {
                    type Output;
                    fn $itemName(self, rhs: RHS) -> Self::Output;
                }

                struct Foo;
                struct Bar;

                impl Bar {
                    fn bar(&self) { unimplemented!() }
                      //X
                }

                impl $traitName<i32> for Foo {
                    type Output = Bar;
                    fn $itemName(self, rhs: i32) -> Bar { unimplemented!() }
                }

                fn foo(lhs: Foo, rhs: i32) {
                    let x = lhs $sign rhs;
                    x.bar()
                     //^
                }
            """)
        }
    }

    fun `test arithmetic operations with multiple impls`() {
        for ((traitName, itemName, sign) in ArithmeticOp.values()) {
            checkByCode("""
                #[lang = "$itemName"]
                pub trait $traitName<RHS=Self> {
                    type Output;
                    fn $itemName(self, rhs: RHS) -> Self::Output;
                }

                struct Foo;
                struct Bar;
                struct FooBar;

                impl Bar {
                    fn foo(&self) { unimplemented!() }
                }

                impl FooBar {
                    fn foo(&self) { unimplemented!() }
                      //X
                }

                impl $traitName<f64> for Foo {
                    type Output = Bar;
                    fn $itemName(self, rhs: f64) -> Bar { unimplemented!() }
                }

                impl $traitName<i32> for Foo {
                    type Output = FooBar;
                    fn $itemName(self, rhs: i32) -> FooBar { unimplemented!() }
                }

                fn foo(lhs: Foo, rhs: i32) {
                    let x = lhs $sign rhs;
                    x.foo()
                     //^
                }
            """)
        }
    }

    fun `test associated type resolve for closure`() = checkByCode("""
        trait A {
            type Item;
            fn filter<P>(self, predicate: P) -> Filter<Self, P> where Self: Sized, P: FnMut(&Self::Item) {
                unimplemented!()
            }
        }
        struct S;
        impl S {
            fn bar(&self) {}
              //X
        }
        impl A for S {
            type Item = S;
        }

        fn main() {
            let t = S;
            t.filter(|e| { e.bar(); })
        }                   //^
    """)
}
