/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

class RsGenericExpressionTypeInferenceTest : RsTypificationTestBase() {
    fun testGenericField() = testExpr("""
        struct S<T> { field: T }

        fn foo(s: S<f64>) {
            let x = s.field;
            x
          //^ f64
        }
    """)

    fun testGenericFieldReference() = testExpr("""
        struct S<'a, T> { field: &'a T }

        fn foo(s: S<'static, f64>) {
            let x = s.field;
            x
          //^ &f64
        }
    """)

    fun testNestedGenericField() = testExpr("""
        struct A<T> { field: T }
        struct B<S> { field: S }

        fn foo(s: A<B<f64>>) {
            let x = s.field.field;
            x
          //^ f64
        }
    """)

    fun testNestedGenericField2() = testExpr("""
        struct S<T> { field: T }

        fn foo(s: S<S<u64>>) {
            let x = s.field.field;
            x
          //^ u64
        }
    """)

    fun testGenericArray() = testExpr("""
        struct S<T> { field: [T; 1] }

        fn foo(s: S<f64>) {
            let x = s.field;
            x
          //^ [f64; 1]
        }
    """)

    fun testGenericSlice() = testExpr("""
        struct S<T: 'static> { field: &'static [T] }

        fn foo(s: S<f64>) {
            let x = s.field;
            x
          //^ &[f64]
        }
    """)

    fun testGenericConstPtr() = testExpr("""
        struct S<T> { field: *const T }

        fn foo(s: S<f64>) {
            let x = s.field;
            x
          //^ *const f64
        }
    """)

    fun testGenericMutPtr() = testExpr("""
        struct S<T> { field: *mut T }

        fn foo(s: S<f64>) {
            let x = s.field;
            x
          //^ *mut f64
        }
    """)

    fun testGenericMethod() = testExpr("""
        struct B<T> { field: T }

        impl<T> B<T> {
            fn unwrap(self) -> T { self.field }
        }

        fn foo(s: B<i32>) {
            let x = s.unwrap();
            x
          //^ i32
        }
    """)

    fun testTwoParameters() = testExpr("""
         enum Result<T, E> { Ok(T), Err(E)}
         impl<T, E> Result<T, E> {
             fn unwrap(self) -> T { unimplemented!() }
         }

         fn foo(r: Result<(u32, u32), ()>) {
             let x = r.unwrap();
             x
           //^ (u32, u32)
         }
    """)

    fun testParamSwap() = testExpr("""
        struct S<A, B> { a: A, b: B}

        impl<X, Y> S<Y, X> {
            fn swap(self) -> (X, Y) { (self.b, self.a) }
        }

        fn f(s: S<i32, ()>) {
            let x = s.swap();
            x
          //^ ((), i32)
        }
    """)

    fun testParamRepeat() = testExpr("""
        struct S<A, B> { a: A, b: B}

        impl <T> S<T, T> {
            fn foo(self) -> (T, T) { (self.a, self.b) }
        }

        fn f(s: S<i32, i32>) {
            let x = s.foo();
            x
          //^ (i32, i32)
        }
    """)

    fun testPartialSpec() = testExpr("""
        struct S<A, B> { a: A, b: B}

        impl <T> S<i32, T> {
            fn foo(self) -> T { self.b }
        }

        fn f(s: S<i32, ()>) {
            let x = s.foo();
            x
          //^ ()
        }
    """)

    fun testGenericFunction() = testExpr("""
        fn f<T>(t: T) -> T { t }

        fn main() {
            let a = f(0i32);
            a
          //^ i32
        }
    """)

    fun testGenericFunction2() = testExpr("""
        fn f<T2, T1>(t1: T1, t2: T2) -> (T1, T2) { (t1, t2) }

        fn main() {
            let a = f(0u8, 1u16);
            a
          //^ (u8, u16)
        }
    """)

    fun testGenericFunction3() = testExpr("""
        fn f<T>(t: T) -> T { t }

        fn main() {
            let a = f::<u8>(1);
            a
          //^ u8
        }
    """)

    fun testGenericFunctionPointer() = testExpr("""
        fn f<T>(t: T) -> T { t }

        fn main() {
            let f = f::<u8>;
            let r = f(1);
            r
          //^ u8
        }
    """)

    fun testGenericFunctionPointer2() = testExpr("""
        fn f<T1, T2>(t1: T1, t2: T2) -> (T1, T2) { (t1, t2) }

        fn main() {
            let f = f::<u8, _>;
            let r = f(1, 2);
            r
          //^ (u8, i32)
        }
    """)

    fun testStaticMethod() = testExpr("""
        struct S<T> { value: T }
        impl<T> S<T> {
            fn new(t: T) -> S<T> { S {value: t} }
        }

        fn main() {
            let a = S::new(0i32);
            a.value
            //^ i32
        }
    """)

    fun testRecursiveType() = testExpr("""
        struct S<T> {
            rec: S<T>,
            field: T
        }

        fn foo(s: S<char>) {
            let x = s.rec.field;
            x
          //^ char
        }
    """)

    fun testSelfType() = testExpr("""
        trait T {
            fn foo(&self) { self; }
                            //^ &Self
        }
    """)

    fun `test struct expr`() = testExpr("""
        struct S<T> { a: T }
        fn main() {
            let x = S { a: 5u16 };
            x.a
            //^ u16
        }
    """)

    fun `test struct expr with 2 fields of same type integer 1`() = testExpr("""
        struct S<T> { a: T, b: T }
        fn main() {
            let x = S { a: 5u16, b: 0 };
            x.b
            //^ u16
        }
    """)

    fun `test struct expr with 2 fields of same type integer 2`() = testExpr("""
        struct S<T> { a: T, b: T }
        fn main() {
            let x = S { a: 0, b: 5u16 };
            x.a
            //^ u16
        }
    """)

    fun `test struct expr with 2 fields of same type float 2`() = testExpr("""
        struct S<T> { a: T, b: T }
        fn main() {
            let x = S { a: 0.0, b: 5f32 };
            x.a
            //^ f32
        }
    """)

    fun `test struct expr with 2 fields of different types`() = testExpr("""
        struct S<T1, T2> { a: T1, b: T2 }
        fn main() {
            let x = S { a: 5u16, b: 5u8 };
            (x.a, x.b)
          //^ (u16, u8)
        }
    """)

    fun `test struct expr with explicit type parameter`() = testExpr("""
        struct S<T> {a: T}
        fn main() {
            let x = S::<u8>{a: 1};
            x.a
            //^ u8
        }
    """)

    fun `test struct expr with explicit and omitted type parameter`() = testExpr("""
        struct S<T1, T2> {a: T1, b: T2}
        fn main() {
            let x = S::<u8, _>{a: 1, b: 2};
            (x.a, x.b)
          //^ (u8, i32)
        }
    """)

    fun testTupleStructExpression() = testExpr("""
        struct S<T> (T);
        fn main() {
            let x = S(5u16);
            x.0
            //^ u16
        }
    """)

    fun `test tuple struct expr with explicit type parameter`() = testExpr("""
        struct S<T> (T);
        fn main() {
            let x = S::<u8>(1);
            x.0
            //^ u8
        }
    """)

    fun `test tuple struct expr with explicit and omitted type parameter`() = testExpr("""
        struct S<T1, T2> (T1, T2);
        fn main() {
            let x = S::<u8, _>(1, 2);
            (x.0, x.1)
          //^ (u8, i32)
        }
    """)

    fun `test reference to generic tuple constructor`() = testExpr("""
        struct S<T>(T);
        fn main() {
            let f = S::<u8>;
            f(1).0
        }      //^ u8
    """)

    fun testGenericAlias() = testExpr("""
        struct S1<T>(T);
        struct S3<T1, T2, T3>(T1, T2, T3);

        type A<T1, T2> = S3<T2, S1<T1>, S3<S1<T2>, T2, T2>>;
        type B = A<u16, u8>;

        fn f(b: B) {
            (b.0, (b.1).0, ((b.2).0).0)
          //^ (u8, u16, u8)
        }
    """)

    fun testGenericStructArg() = testExpr("""
        struct Foo<F>(F);
        fn foo<T>(xs: Foo<T>) -> T { unimplemented!() }
        fn main() {
            let x = foo(Foo(123));
            x
          //^ i32
        }
    """)

    fun testGenericEnumArg() = testExpr("""
        enum Foo<F> { V(F) }
        fn foo<T>(xs: Foo<T>) -> T { unimplemented!() }
        fn main() {
            let x = foo(Foo::V(123));
            x
          //^ i32
        }
    """)

    fun testGenericTupleArg() = testExpr("""
        fn foo<T, F>(xs: (T, F)) -> F { unimplemented!() }
        fn main() {
            let x = foo((123, "str"));
            x
          //^ &str
        }
    """)

    fun testGenericReferenceArg() = testExpr("""
        fn foo<T>(xs: &T) -> T { unimplemented!() }
        fn main() {
            let x = foo(&8u64);
            x
          //^ u64
        }
    """)

    fun testGenericPointerArg() = testExpr("""
        fn foo<T>(xs: *const T) -> T { unimplemented!() }
        fn main() {
            let x = foo(&8u16 as *const u16);
            x
          //^ u16
        }
    """)

    fun testGenericArrayArg() = testExpr("""
        fn foo<T>(xs: [T; 4]) -> T { unimplemented!() }
        fn main() {
            let x = foo([1, 2, 3, 4]);
            x
          //^ i32
        }
    """)

    fun testGenericSliceArg() = testExpr("""
        fn foo<T>(xs: &[T]) -> T { unimplemented!() }
        fn main() {
            let slice: &[&str] = &["foo", "bar"];
            let x = foo(slice);
            x
          //^ &str
        }
    """)

    fun testComplexGenericArg() = testExpr("""
        struct Foo<T1, T2>(T1, T2);
        enum Bar<T3, T4> { V(T3, T4) }
        struct FooBar<T5, T6>(T5, T6);

        fn foo<F1, F2, F3, F4>(x: FooBar<Foo<F1, F2>, Bar<F3, F4>>) -> (Bar<F4, F1>, Foo<F3, F2>) { unimplemented!() }
        fn main() {
            let x = foo(FooBar(Foo(123, "foo"), Bar::V([0.0; 3], (0, false))));
            x
          //^ (Bar<(i32, bool), i32>, Foo<[f64; 3], &str>)
        }
    """)

    fun testArrayToSlice() = testExpr("""
        fn foo<T>(xs: &[T]) -> T { unimplemented!() }
        fn main() {
            let x = foo(&[1, 2, 3]);
            x
          //^ i32
        }
    """)

    fun testGenericStructMethodArg() = testExpr("""
        struct Foo<F>(F);
        struct Bar<B>(B);
        impl<T1> Foo<T1> {
            fn foo<T2>(&self, bar: Bar<T2>) -> (T1, T2) { unimplemented!() }
        }
        fn main() {
            let x = Foo("foo").foo(Bar(123));
            x
          //^ (&str, i32)
        }
    """)

    fun testGenericEnumMethodArg() = testExpr("""
        struct Foo<F>(F);
        enum Bar<B> { V(B) }
        impl<T1> Foo<T1> {
            fn foo<T2>(&self, bar: Bar<T2>) -> (T1, T2) { unimplemented!() }
        }
        fn main() {
            let x = Foo(0.0).foo(Bar::V("bar"));
            x
          //^ (f64, &str)
        }
    """)

    fun testGenericTupleMethodArg() = testExpr("""
        struct Foo<F>(F);
        impl<T1> Foo<T1> {
            fn foo<T2, T3>(&self, xs: (T2, T3)) -> (T1, T2, T3) { unimplemented!() }
        }
        fn main() {
            let x = Foo(123).foo((true, "str"));
            x
          //^ (i32, bool, &str)
        }
    """)

    fun testGenericReferenceMethodArg() = testExpr("""
        struct Foo<F>(F);
        impl<T1> Foo<T1> {
            fn foo<T2>(&self, xs: &T2) -> (T2, T1) { unimplemented!() }
        }
        fn main() {
            let x = Foo((1, 2)).foo(&8u64);
            x
          //^ (u64, (i32, i32))
        }
    """)

    fun testGenericPointerMethodArg() = testExpr("""
        struct Foo<F>(F);
        impl<T1> Foo<T1> {
            fn foo<T2>(&self, xs: *const T2) -> (T2, T1) { unimplemented!() }
        }
        fn main() {
            let x = Foo("foo").foo(&8u16 as *const u16);
            x
          //^ (u16, &str)
        }
    """)

    fun testGenericArrayMethodArg() = testExpr("""
        struct Foo<F>(F);
        impl<T1> Foo<T1> {
            fn foo<T2>(&self, xs: [T2; 4]) -> (T2, T1) { unimplemented!() }
        }
        fn main() {
            let x = Foo(0.0).foo([1, 2, 3, 4]);
            x
          //^ (i32, f64)
        }
    """)

    fun testGenericSliceMethodArg() = testExpr("""
        struct Foo<F>(F);
        impl<T1> Foo<T1> {
            fn foo<T2>(&self, xs: &[T2]) -> (T2, T1) { unimplemented!() }
        }
        fn main() {
            let slice: &[&str] = &["foo", "bar"];
            let x = Foo(64u8).foo(slice);
            x
          //^ (&str, u8)
        }
    """)

    fun testComplexGenericMethodArg() = testExpr("""
        struct Foo<T1, T2>(T1, T2);
        enum Bar<T3, T4> { V(T3, T4) }
        struct FooBar<T5>(T5);

        impl<F1, F2> Foo<F1, F2> {
            fn foo<F3, F4>(&self, x: FooBar<Bar<F3, F4>>) -> (Bar<F4, F1>, Foo<F3, F2>) { unimplemented!() }
        }
        fn main() {
            let x = Foo(123, "foo").foo(FooBar(Bar::V([0.0; 3], (0, false))));
            x
          //^ (Bar<(i32, bool), i32>, Foo<[f64; 3], &str>)
        }
    """)

    fun `test infer generic argument from trait bound`() = testExpr("""
        struct S<X>(X);
        trait Tr<Y> { fn foo(&self) -> Y; }
        impl<Z> Tr<Z> for S<Z> { fn foo(&self) -> Z { unimplemented!() } }
        fn bar<A, B: Tr<A>>(b: B) -> A { b.foo() }
        fn main() {
            let a = bar(S(1));
            a
        } //^ i32
    """)

    fun `test Self substitution to trait method`() = testExpr("""
        trait Tr<A> { fn wrap(self) -> S<Self> where Self: Sized { unimplemented!() } }
        struct X;
        struct S<C>(C);
        impl<D> Tr<D> for S<D> {}
        fn main() {
            let a = S(X).wrap().wrap().wrap();
            a
        } //^ S<S<S<S<X>>>>
    """)

    fun `test Self substitution to impl method`() = testExpr("""
        trait Tr<A> { fn wrap(self) -> S<Self> where Self: Sized { unimplemented!() } }
        struct X;
        struct S<C>(C);
        impl<D> Tr<D> for S<D> { fn wrap(self) -> S<Self> where Self: Sized { unimplemented!() } }
        fn main() {
            let a = S(X).wrap().wrap().wrap();
            a
        } //^ S<S<S<S<X>>>>
    """)

    fun `test recursive receiver substitution`() = testExpr("""
        trait Tr<A> {
            fn wrap(self) -> S<Self> where Self: Sized { unimplemented!() }
            fn fold(self) -> A where Self: Sized { unimplemented!() }
        }

        struct X;
        struct S1<B>(B);
        struct S<C>(C);

        impl<D> Tr<D> for S1<D> {}
        impl<Src, Dst> Tr<Dst> for S<Src> where Src: Tr<Dst> {}

        fn main() {
            let a = S1(X).wrap().wrap().wrap().fold();
            a
        } //^ X
    """)
}
