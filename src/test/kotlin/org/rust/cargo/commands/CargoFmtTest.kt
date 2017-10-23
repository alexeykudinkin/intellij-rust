/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.commands

import org.rust.cargo.RustWithToolchainTestBase
import org.rust.cargo.project.settings.toolchain
import org.rust.fileTree

class CargoFmtTest : RustWithToolchainTestBase() {

    fun testCargoFmt() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {
                    println!("Hello, world!");
                    }
                """)
            }
        }.create()

        val cargo = project.toolchain!!.cargo()
        val main = cargoProjectDirectory.findFileByRelativePath("src/main.rs")!!
        val result = cargo.reformatFile(testRootDisposable, main)
        check(result.exitCode == 0)
    }
}
