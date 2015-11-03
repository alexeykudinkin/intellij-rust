package org.rust.cargo.project

import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import org.rust.cargo.Cargo

import java.io.File

class CargoAutoImport : ExternalSystemAutoImportAware {
    override fun getAffectedExternalProjectPath(changedFileOrDirPath: String, project: Project): String? {
        val changed = File(changedFileOrDirPath)
        val name = changed.name
        val base = File(project.basePath)
        if ((Cargo.BUILD_FILE == name || Cargo.LOCK_FILE == name) && VfsUtilCore.isAncestor(base, changed, true)) {
            return project.basePath
        }
        return null
    }
}
