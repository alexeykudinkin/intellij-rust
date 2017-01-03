package org.rust.lang.core.completion

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.lang.annotations.Language
import org.rust.lang.RustTestCaseBase

abstract class RustCompletionTestBase : RustTestCaseBase() {

    protected fun checkSingleCompletionByFile() = checkByFile {
        executeSoloCompletion()
    }

    protected fun checkSingleCompletion(target: String, @Language("Rust") code: String) {
        InlineFile(code).withCaret()
        val variants = myFixture.completeBasic()
        check(variants == null) {
            "Expected a single completion, but got ${variants.size}\n" + "${variants.toList()}"
        }
        val normName = target
            .substringBeforeLast("()")
            .substringBeforeLast(" {}")
            .substringAfterLast("::")
            .substringAfterLast(".")
        val shift = when {
            target.endsWith("()") || target.endsWith("::") -> 3
            target.endsWith(" {}") -> 4
            else -> 2
        }
        val element = myFixture.file.findElementAt(myFixture.caretOffset - shift)!!
        val skipTextCheck = normName.isEmpty() || normName.contains(' ')
        check((skipTextCheck || element.text == normName) && (element.fitsHierarchically(target) || element.fitsLinearly(target))) {
            "Wrong completion, expected `$target`, but got `${element.text}`"
        }
    }

    protected fun checkContainsCompletion(text: String, @Language("Rust") code: String) {
        InlineFile(code).withCaret()
        val variants = myFixture.completeBasic()
        checkNotNull(variants) {
            "Expected completions that contain $text, but no completions found"
        }
        variants.filter { it.lookupString == text }.forEach { return }
        error("Expected completions that contain $text, but got ${variants.toList()}")
    }

    protected fun checkNoCompletion(@Language("Rust") code: String) {
        InlineFile(code).withCaret()
        val variants = myFixture.completeBasic()
        checkNotNull(variants) {
            val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)
            "Expected zero completions, but one completion was auto inserted: `${element?.text}`."
        }
        check(variants.isEmpty()) {
            "Expected zero completions, got ${variants.size}."
        }
    }

    protected fun executeSoloCompletion() {
        val variants = myFixture.completeBasic()
        if (variants != null) {
            error("Expected a single completion, but got ${variants.size}\n" +
                "${variants.toList()}")
        }
    }

    private fun PsiElement.fitsHierarchically(target: String): Boolean = when {
            text == target -> true
            text.length > target.length -> false
            parent != null -> parent.fitsHierarchically(target)
            else -> false
        }

    private fun PsiElement.fitsLinearly(target: String) =
        checkLinearly(target, Direction.LEFT) || checkLinearly(target, Direction.RIGHT)

    private fun PsiElement.checkLinearly(target: String, direction: Direction): Boolean {
        var el = this
        var text = ""
        while (text.length < target.length) {
            text = if (direction == Direction.LEFT) el.text + text else text + el.text
            if (text == target) return true
            el = (if (direction == Direction.LEFT) PsiTreeUtil.prevVisibleLeaf(el) else PsiTreeUtil.nextVisibleLeaf(el)) ?: break
        }
        return false
    }

    private enum class Direction {
        LEFT,
        RIGHT
    }
}
