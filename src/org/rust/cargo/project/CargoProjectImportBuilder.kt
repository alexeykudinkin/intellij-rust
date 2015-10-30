package org.rust.cargo.project

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportBuilder
import com.intellij.openapi.project.Project
import org.rust.cargo.Cargo
import java.io.File
import javax.swing.Icon

class CargoProjectImportBuilder(projectDataManager: ProjectDataManager) :
        AbstractExternalProjectImportBuilder<CargoImportControl>(
                projectDataManager, CargoImportControl(), CargoProjectSystem.ID) {

    override fun getName(): String {
        return Cargo.NAME
    }

    override fun getIcon(): Icon {
        return Cargo.ICON
    }

    override fun doPrepare(wizardContext: WizardContext) {
    }

    override fun beforeCommit(dataNode: DataNode<ProjectData>, project: Project) {
    }

    override fun getExternalProjectConfigToUse(file: File): File {
        return file
    }

    override fun applyExtraSettings(wizardContext: WizardContext) {
    }
}
