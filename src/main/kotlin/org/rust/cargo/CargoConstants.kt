package org.rust.cargo

object CargoConstants {
    const val ID      = "Cargo"
    const val NAME    = "Cargo"

    const val MANIFEST_FILE = "Cargo.toml"
    const val LOCK_FILE     = "Cargo.lock"

    const val RUSTC_ENV_VAR = "RUSTC"

    object Commands {
        val RUN = "run"
        val TEST = "test"
    }
}
