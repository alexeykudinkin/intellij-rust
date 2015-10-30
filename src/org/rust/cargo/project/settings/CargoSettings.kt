package org.rust.cargo.project.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtilRt
import com.intellij.util.xmlb.annotations.AbstractCollection

@State(name = "CargoSettings", storages = arrayOf(
        Storage(file = StoragePathMacros.PROJECT_FILE),
        Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/cargo.xml", scheme = StorageScheme.DIRECTORY_BASED)))
class CargoSettings(project: Project) :
        AbstractExternalSystemSettings<CargoSettings, CargoProjectSettings, CargoProjectSettingsListener>(CargoTopic.INSTANCE, project),
        PersistentStateComponent<CargoSettingsState> {

    override fun subscribe(listener: ExternalSystemSettingsListener<CargoProjectSettings>) {
        val adapter = CargoProjectSettingsListenerAdapter(listener)
        project.messageBus.connect(project).subscribe(CargoTopic.INSTANCE, adapter)
    }

    override fun getState(): CargoSettingsState {
        val state = CargoSettingsState()
        fillState(state)
        return state
    }

    override fun loadState(state: CargoSettingsState) {
        super.loadState(state)
    }

    override fun copyExtraSettingsFrom(settings: CargoSettings) {
    }

    override fun checkSettings(settings1: CargoProjectSettings, settings2: CargoProjectSettings) {
    }

    companion object {

        fun getInstance(project: Project): CargoSettings {
            return ServiceManager.getService(project, CargoSettings::class.java)
        }
    }
}

class CargoSettingsState : AbstractExternalSystemSettings.State<CargoProjectSettings> {
    private var linkedExternalProjects: Set<CargoProjectSettings> = ContainerUtilRt.newHashSet<CargoProjectSettings>()

    @AbstractCollection(surroundWithTag = false, elementTypes = arrayOf(CargoProjectSettings::class))
    override fun getLinkedExternalProjectsSettings(): Set<CargoProjectSettings> {
        return linkedExternalProjects
    }

    override fun setLinkedExternalProjectsSettings(set: Set<CargoProjectSettings>) {
        linkedExternalProjects = set
    }
}