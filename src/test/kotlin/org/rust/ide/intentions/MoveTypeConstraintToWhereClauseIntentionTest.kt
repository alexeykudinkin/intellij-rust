package org.rust.ide.intentions

class MoveTypeConstraintToWhereClauseIntentionTest : RsIntentionTestBase(MoveTypeConstraintToWhereClauseIntention()) {
    fun testFunctionWithReturn() = doAvailableTest(
        """ fn foo<T: Send,/*caret*/ F: Sync>(t: T, f: F) -> i32 { 0 } """,
        """ fn foo<T, F>(t: T, f: F) -> i32 where T: Send, F: Sync/*caret*/ { 0 } """
    )

    fun testLifetimesAndTraits() = doAvailableTest(
        """ fn foo<'a, 'b: 'a, T: Send,/*caret*/ F: Sync>(t: &'a T, f: &'b F) { } """,
        """ fn foo<'a, 'b, T, F>(t: &'a T, f: &'b F) where 'b: 'a, T: Send, F: Sync/*caret*/ { } """
    )

    fun testMultipleBounds() = doAvailableTest(
        """ fn foo<T: /*caret*/Send + Sync>(t: T, f: F) { } """,
        """ fn foo<T>(t: T, f: F) where T: Send + Sync/*caret*/ { } """
    )

    fun testMultipleLifetimes() = doAvailableTest(
        """ fn foo<'a, /*caret*/'b: 'a>(t: &'a i32, f: &'b i32) { } """,
        """ fn foo<'a, 'b>(t: &'a i32, f: &'b i32) where 'b: 'a/*caret*/ { } """
    )

    fun testMultipleTraits() = doAvailableTest(
        """ fn foo<T: Send,/*caret*/ F: Sync>(t: T, f: F) { } """,
        """ fn foo<T, F>(t: T, f: F) where T: Send, F: Sync/*caret*/ { } """
    )

    fun testTypeItemElement() = doAvailableTest(
        """ type O<T: /*caret*/Copy> = Option<T>; """,
        """ type O<T> where T: Copy/*caret*/ = Option<T>; """
    )

    fun testImplItemElement() = doAvailableTest(
        """ impl<T: /*caret*/Copy> Foo<T> {} """,
        """ impl<T> Foo<T> where T: Copy/*caret*/ {} """
    )

    fun testTraitItemElement() = doAvailableTest(
        """ trait Foo<T:/*caret*/ Copy> {} """,
        """ trait Foo<T> where T: Copy/*caret*/ {} """
    )

    fun testStructItemElement() = doAvailableTest(
        """ struct Foo<T:/*caret*/ Copy> { x: T } """,
        """ struct Foo<T> where T: Copy/*caret*/ { x: T } """
    )

    fun testTupleStructItemElement() = doAvailableTest(
        """ struct Foo<T:/*caret*/ Copy>(T); """,
        """ struct Foo<T>(T) where T: Copy/*caret*/; """
    )

    fun testEnumItemElement() = doAvailableTest(
        """ enum Foo<T:/*caret*/ Copy> { X(T) } """,
        """ enum Foo<T> where T: Copy/*caret*/ { X(T) } """
    )

    fun testNoLifetimeBounds() = doUnavailableTest(""" fn foo<'a, /*caret*/'b>(t: &'a i32, f: &'b i32) { } """)

    fun testNoTraitBounds() = doUnavailableTest(""" fn foo<T, /*caret*/F>(t: T, f: F) { } """)
}
