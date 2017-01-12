package org.rust.ide.idea

import backcompat.ui.layout.panel
import com.intellij.ide.util.importProject.ProjectDescriptor
import com.intellij.ide.util.projectWizard.ModuleBuilder.ModuleConfigurationUpdater
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel
import javax.swing.JComponent

class CargoConfigurationWizardStep(
    private val context: WizardContext,
    private val projectDescriptor: ProjectDescriptor? = null
) : ModuleWizardStep(), Disposable {

    private val rustProjectSettings = RustProjectSettingsPanel()

    override fun getComponent(): JComponent = panel {
        rustProjectSettings.attachTo(this)
    }

    override fun disposeUIResources() = rustProjectSettings.disposeUIResources()

    override fun updateDataModel() {
        ConfigurationUpdater.data = rustProjectSettings.data

        val projectBuilder = context.projectBuilder
        if (projectBuilder is RsModuleBuilder) {
            projectBuilder.rustProjectData = rustProjectSettings.data
            projectBuilder.addModuleConfigurationUpdater(ConfigurationUpdater)
        } else {
            projectDescriptor?.modules?.firstOrNull()?.addConfigurationUpdater(ConfigurationUpdater)
        }
    }

    @Throws(ConfigurationException::class)
    override fun validate(): Boolean {
        rustProjectSettings.validateSettings()
        return true
    }

    override fun dispose() = rustProjectSettings.disposeUIResources()

    private object ConfigurationUpdater : ModuleConfigurationUpdater() {
        var data: RustProjectSettingsPanel.Data? = null

        override fun update(module: Module, rootModel: ModifiableRootModel) {
            data?.applyTo(module.project.rustSettings)

            val contentEntry = rootModel.contentEntries.singleOrNull()
            if (contentEntry != null) {
                val projectRoot = contentEntry.file ?: return
                val makeVfsUrl = { dirName: String -> FileUtil.join(projectRoot.url, dirName) }
                CargoConstants.ProjectLayout.sources.map(makeVfsUrl).forEach {
                    contentEntry.addSourceFolder(it, /* test = */ false)
                }
                CargoConstants.ProjectLayout.tests.map(makeVfsUrl).forEach {
                    contentEntry.addSourceFolder(it, /* test = */ true)
                }
                contentEntry.addExcludeFolder(makeVfsUrl(CargoConstants.ProjectLayout.target))
            }
        }
    }

}
