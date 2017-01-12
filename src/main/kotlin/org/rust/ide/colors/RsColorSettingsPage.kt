package org.rust.ide.colors

import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import org.rust.ide.highlight.RsHighlighter
import org.rust.ide.icons.RsIcons
import org.rust.ide.utils.loadCodeSampleResource

class RsColorSettingsPage : ColorSettingsPage {
    private val ATTRS = RsColor.values().map { it.attributesDescriptor }.toTypedArray()

    // This tags should be kept in sync with RustHighlightingAnnotator highlighting logic
    // TODO: Figure out how to namespace menu elements a la
    //       https://github.com/JetBrains/kotlin/blob/8b30e7ef4e48494ba245b441a5b23142f1d6ae33/idea/idea-analysis/src/org/jetbrains/kotlin/idea/KotlinBundle.properties
    private val ANNOTATOR_TAGS = RsColor.values().associateBy({ it.name }, { it.textAttributesKey })

    private val DEMO_TEXT by lazy {
        // TODO: The annotations in this file should be generable, and would be more accurate for it.
        loadCodeSampleResource("org/rust/ide/colors/highlighterDemoText.rs")
    }

    override fun getDisplayName() = "Rust"
    override fun getIcon() = RsIcons.RUST
    override fun getAttributeDescriptors() = ATTRS
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getHighlighter() = RsHighlighter()
    override fun getAdditionalHighlightingTagToDescriptorMap() = ANNOTATOR_TAGS
    override fun getDemoText() = DEMO_TEXT
}
