package org.rust.cargo.project.workspace

import com.intellij.execution.ExecutionException
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.util.rustLibraryName

/**
 * Cargo based project's workspace abstraction insulating inter-op with the `cargo` & `Cargo.toml`
 * itself. Uses `cargo metadata` sub-command to update IDEA module's & project's model.
 *
 * Quite low-level in its nature & follows a soft-fail policy, i.e. provides access
 * for latest obtained instance of the [CargoProjectDescription], though doesn't assure that this
 * one could be obtained (consider the case with invalid, or missing `Cargo.toml`)
 */
interface CargoProjectWorkspace {

    /**
     * Updates Rust libraries asynchronously. Consecutive requests are coalesced.
     */
    fun requestUpdate(toolchain: RustToolchain)

    /**
     * Schedules an update without debouncing. The update won't start immediately
     * if there is one currently executing. [afterCommit] callback is called on the
     * EDT thread.
     */
    fun requestImmediateUpdate(toolchain: RustToolchain, afterCommit: (UpdateResult) -> Unit)

    @TestOnly
    fun syncUpdate(toolchain: RustToolchain)

    /**
     * Latest version of the Cargo's project-description obtained
     */
    val projectDescription: CargoProjectDescription?

    sealed class UpdateResult {
        class Ok(val projectDescription: CargoProjectDescription) : UpdateResult()
        class Err(val error: ExecutionException) : UpdateResult()
    }

    companion object {
        fun forModule(module: Module): CargoProjectWorkspace =
            module.getComponent(CargoProjectWorkspace::class.java)
                ?: error("Can't retrieve CargoProjectWorkspace component for $this")
    }
}

/**
 * Extracts Cargo project description out of `Cargo.toml`
 */
val Module.cargoProject: CargoProjectDescription?
    get() = CargoProjectWorkspace.forModule(this).projectDescription?.let {
        val lib = LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraryByName(rustLibraryName)
            ?: return it
        return it.withStdlib(lib)
    }
