/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.toolwindow

import com.intellij.ui.tree.BaseTreeModel
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.toolwindow.CargoProjectStructure.Node.*
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspace.TargetKind.*
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.runconfig.command.workingDirectory
import javax.swing.Icon
import javax.swing.tree.DefaultMutableTreeNode

class CargoProjectStructure(private var cargoProjects: List<CargoProject> = emptyList()) : BaseTreeModel<DefaultMutableTreeNode>() {

    private val root = DefaultMutableTreeNode(Node.Root)

    fun updateCargoProjects(cargoProjects: List<CargoProject>) {
        this.cargoProjects = cargoProjects
        treeStructureChanged(null, null, null)
    }

    override fun getChildren(parent: Any): List<DefaultMutableTreeNode>? {
        val childrenObjects = when (val userObject = (parent as? DefaultMutableTreeNode)?.userObject) {
            is Root -> cargoProjects.map(::Project)
            is Project -> {
                val cargoProject = userObject.cargoProject
                val (ourPackage, workspaceMembers) = cargoProject.workspace
                    ?.packages
                    ?.filter { it.origin == PackageOrigin.WORKSPACE }
                    .orEmpty()
                    .partition { it.rootDirectory == cargoProject.workingDirectory }
                val childrenNodes = mutableListOf<Node>()
                ourPackage.mapTo(childrenNodes) { Targets(it.targets) }
                childrenNodes.add(Features(cargoProject.workspace
                    ?.packages
                    ?.filter { it.origin != PackageOrigin.STDLIB }
                    ?.sortedBy { it.name } ?: emptyList()))
                workspaceMembers.mapTo(childrenNodes, ::WorkspaceMember)
                childrenNodes
            }
            is WorkspaceMember -> listOf(Targets(userObject.pkg.targets))
            is Targets -> userObject.targets.map { Node.Target(it) }
            is Node.Target -> emptyList()
            is Features -> userObject.packages.map { Node.Package(it) }
            is Package -> userObject.pkg.features
                .sortedWith(compareBy({ it.state }, { it.name }))
                .map { Node.Feature(it) }
            is Feature -> emptyList()
            else -> null
        }
        return childrenObjects?.map(::DefaultMutableTreeNode)
    }

    override fun getRoot(): Any = root

    override fun toString(): String = "CargoProjectStructure(workspaces = $cargoProjects)"

    sealed class Node {
        object Root : Node() {
            override fun toString(): String = "Root"
        }

        data class Project(val cargoProject: CargoProject) : Node() {
            override fun toString(): String = "Project"
        }

        data class WorkspaceMember(val pkg: CargoWorkspace.Package) : Node() {
            override fun toString(): String = "WorkspaceMember($name)"
        }

        data class Targets(val targets: Collection<CargoWorkspace.Target>) : Node() {
            override fun toString(): String = "Targets"
        }

        data class Target(val target: CargoWorkspace.Target) : Node() {
            override fun toString(): String = "Target($name[${target.kind.name.toLowerCase()}])"
        }

        data class Features(val packages: Collection<CargoWorkspace.Package>) : Node() {
            override fun toString(): String = "Features"
        }

        data class Package(val pkg: CargoWorkspace.Package) : Node() {
            override fun toString(): String = "Package($name)"
        }

        data class Feature(val feature: CargoWorkspace.Feature) : Node() {
            override fun toString(): String {
                val enabledDisabled = when (feature.state) {
                    CargoWorkspace.FeatureState.Enabled -> "enabled"
                    CargoWorkspace.FeatureState.Disabled -> "disabled"
                    else -> "unknown"
                }
                return "Feature($name,$enabledDisabled)"
            }
        }

        val name: String
            get() = when (this) {
                Root -> ""
                is Project -> cargoProject.presentableName
                is WorkspaceMember -> pkg.name
                is Targets -> "targets"
                is Target -> target.name
                is Features -> "features"
                is Package -> {
                    if (!pkg.version.isEmpty())
                        pkg.name + "-" + pkg.version
                    else
                        pkg.name
                }
                is Feature -> feature.name
            }

        val icon: Icon?
            get() = when (this) {
                is Project -> CargoIcons.ICON
                is WorkspaceMember -> CargoIcons.ICON
                is Targets -> CargoIcons.TARGETS
                is Target -> target.icon
                is Features -> null
                is Feature -> when (feature.state) {
                    CargoWorkspace.FeatureState.Enabled -> CargoIcons.FEATURE_ENABLED
                    CargoWorkspace.FeatureState.Disabled -> CargoIcons.FEATURE_DISABLED
                    else -> CargoIcons.FEATURE_UNKNOWN
                }
                else -> null
            }

        private val CargoWorkspace.Target.icon: Icon?
            get() = when (kind) {
                is Lib -> CargoIcons.LIB_TARGET
                Bin -> CargoIcons.BIN_TARGET
                Test -> CargoIcons.TEST_TARGET
                Bench -> CargoIcons.BENCH_TARGET
                ExampleBin, is ExampleLib -> CargoIcons.EXAMPLE_TARGET
                Unknown -> null
            }
    }
}
