package org.rust.ide.annotator

class RustItemAnnotatorTest : RustAnnotatorTestBase() {
    override val dataPath = "org/rust/ide/annotator/fixtures/items"

    fun testInvalidModuleDeclarations() = doTest("helper.rs")

    fun testCreateFileQuickFix() = checkByDirectory {
        openFileInEditor("mod.rs")
        applyQuickFix("Create module file")
    }

    fun testCreateFileAndExpandModuleQuickFix() = checkByDirectory {
        openFileInEditor("foo.rs")
        applyQuickFix("Create module file")
    }

    fun testInvalidTraitImpl() = doTest()
    fun testInvalidTraitImplFix() = checkQuickFix("Implement methods")

}
