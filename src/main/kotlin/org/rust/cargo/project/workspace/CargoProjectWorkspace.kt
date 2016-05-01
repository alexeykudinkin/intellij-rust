package org.rust.cargo.project.workspace

import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.toolchain.RustToolchain

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
    fun requestUpdateUsing(toolchain: RustToolchain, immediately: Boolean = false)

    /**
     * Latest version of the Cargo's project-description obtained
     */
    val projectDescription: CargoProjectDescription?

}
