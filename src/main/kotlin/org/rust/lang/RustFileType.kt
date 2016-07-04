package org.rust.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.VirtualFile
import org.rust.ide.icons.RustIcons
import javax.swing.Icon

object RustFileType : LanguageFileType(RustLanguage) {

    object DEFAULTS {
        val EXTENSION: String = "rs"
    }

    override fun getName(): String = "Rust"

    override fun getIcon(): Icon = RustIcons.RUST_FILE

    override fun getDefaultExtension(): String = DEFAULTS.EXTENSION

    override fun getCharset(file: VirtualFile, content: ByteArray): String = "UTF-8"

    override fun getDescription(): String = "Rust Files"
}

