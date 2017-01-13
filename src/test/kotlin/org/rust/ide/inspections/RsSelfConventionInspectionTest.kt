package org.rust.ide.inspections

/**
 * Tests for Self Convention inspection
 */
class RsSelfConventionInspectionTest : RsInspectionsTestBase() {

    fun testFrom() = checkByText<RsSelfConventionInspection>("""
        struct Foo;
        impl Foo {
            fn from_nothing(<warning descr="methods called `from_*` usually take no self; consider choosing a less ambiguous name">self</warning>) -> T { T() }
            fn from_ok(x: i32) -> T { T() }
        }
    """)

    fun testInto() = checkByText<RsSelfConventionInspection>("""
        struct Foo;
        impl Foo {
            fn into_u32(self) -> u32 { 0 }
            fn into_u16(<warning descr="methods called `into_*` usually take self by value; consider choosing a less ambiguous name">&self</warning>) -> u16 { 0 }
            fn <warning descr="methods called `into_*` usually take self by value; consider choosing a less ambiguous name">into_without_self</warning>() -> u16 { 0 }
        }
    """)

    fun testTo() = checkByText<RsSelfConventionInspection>("""
        struct Foo;
        impl Foo {
            fn to_something(<warning descr="methods called `to_*` usually take self by reference; consider choosing a less ambiguous name">self</warning>) -> u32 { 0 }
            fn to_something_else(&self) -> u32 { 92 }
        }
    """)

    fun testIs() = checkByText<RsSelfConventionInspection>("""
        struct Foo;
        impl Foo {
            fn is_awesome(<warning descr="methods called `is_*` usually take self by reference or no self; consider choosing a less ambiguous name">self</warning>) {}
            fn is_awesome_ref(&self) {}
        }
    """)

    fun testIsSuppresedForCopyable() = checkByText<RsSelfConventionInspection>("""
        #[derive(Copy)]
        struct Copyable;
        impl Copyable {
            fn is_ok(self) {}
        }
    """)
}
