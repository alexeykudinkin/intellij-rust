/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig

import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess
import com.jetbrains.cidr.execution.debugger.backend.DebuggerCommandException
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver
import com.jetbrains.cidr.execution.debugger.backend.gdb.GDBDriver
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBDriver
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.tools.rustc
import org.rust.cargo.toolchain.wsl.RsWslToolchain
import org.rust.debugger.*
import org.rust.debugger.settings.RsDebuggerSettings
import org.rust.ide.notifications.showBalloon
import java.nio.file.InvalidPathException

class RsDebugProcessConfigurationHelper(
    private val process: CidrDebugProcess,
    cargoProject: CargoProject?,
    private val isCrossLanguage: Boolean = false
) {
    private val settings = RsDebuggerSettings.getInstance()
    private val project = process.project
    private val toolchain = process.project.toolchain
    private val threadId = process.currentThreadId
    private val frameIndex = process.currentFrameIndex

    private val commitHash = cargoProject?.rustcInfo?.version?.commitHash

    private val prettyPrintersPath: String? = toolchain?.toRemotePath(PP_PATH)

    private val sysroot: String? by lazy {
        cargoProject?.workingDirectory
            ?.let { toolchain?.rustc()?.getSysroot(it) }
            ?.let { toolchain?.toRemotePath(it) }
    }

    fun configure() {
        process.postCommand { driver ->
            try {
                driver.loadRustcSources()
                driver.loadPrettyPrinters()
                if (settings.breakOnPanic) {
                    driver.setBreakOnPanic()
                }
                driver.setSteppingFilters()
            } catch (e: DebuggerCommandException) {
                process.printlnToConsole(e.message)
                LOG.warn(e)
            } catch (e: InvalidPathException) {
                LOG.warn(e)
            }
        }
    }

    private fun DebuggerDriver.setBreakOnPanic() {
        val commands = when (this) {
            is LLDBDriver -> listOf("breakpoint set -n rust_panic")
            is GDBDriver -> listOf("set breakpoint pending on", "break rust_panic")
            else -> return
        }
        for (command in commands) {
            executeInterpreterCommand(threadId, frameIndex, command)
        }
    }

    private fun DebuggerDriver.setSteppingFilters() {
        val regexes = mutableListOf<String>()
        if (settings.skipStdlibInStepping) {
            regexes.add("^(std|core|alloc)::.*")
        }

        val command = when (this) {
            is LLDBDriver -> "settings set target.process.thread.step-avoid-regexp"
            is GDBDriver -> "skip -rfu"
            else -> return
        }
        for (regex in regexes) {
            executeInterpreterCommand(threadId, frameIndex, "$command $regex")
        }
    }

    private fun DebuggerDriver.loadRustcSources() {
        if (commitHash == null) return

        val sysroot = checkSysroot(sysroot, "Cannot load rustc sources") ?: return
        val sourceMapCommand = when (this) {
            is LLDBDriver -> "settings set target.source-map"
            is GDBDriver -> "set substitute-path"
            else -> return
        }
        val rustcHash = "/rustc/$commitHash/".systemDependentAndEscaped()
        val rustcSources = "$sysroot/lib/rustlib/src/rust/".systemDependentAndEscaped()
        val fullCommand = """$sourceMapCommand "$rustcHash" "$rustcSources" """
        executeInterpreterCommand(threadId, frameIndex, fullCommand)
    }

    private fun DebuggerDriver.loadPrettyPrinters() {
        when (this) {
            is LLDBDriver -> loadPrettyPrinters()
            is GDBDriver -> loadPrettyPrinters()
        }
    }

    private fun LLDBDriver.loadPrettyPrinters() {
        when (settings.lldbRenderers) {
            LLDBRenderers.COMPILER -> {
                val sysroot = checkSysroot(sysroot, "Cannot load rustc renderers") ?: return
                val basePath = "$sysroot/lib/rustlib/etc"
                val lldbLookupPath = "$basePath/$LLDB_LOOKUP.py".systemDependentAndEscaped()
                val lldbCommandsPath = "$basePath/lldb_commands".systemDependentAndEscaped()
                executeInterpreterCommand(threadId, frameIndex, """command script import "$lldbLookupPath" """)
                executeInterpreterCommand(threadId, frameIndex, """command source "$lldbCommandsPath" """)
            }

            LLDBRenderers.BUNDLED -> {
                val path = prettyPrintersPath?.systemDependentAndEscaped() ?: return
                executeInterpreterCommand(threadId, frameIndex, """command script import "$path/$LLDB_LOOKUP.py" """)

                // In case of cross-language projects, lldb pretty-printers should be enabled
                // only for specific std types (but should not be enabled for arbitrary struct/enums),
                // because `type synthetic add ... -x ".*"` overrides C++ STL pretty-printers
                val enabledTypes = if (isCrossLanguage) RUST_STD_TYPES else listOf(".*")
                for (type in enabledTypes) {
                    executeInterpreterCommand(threadId, frameIndex, """type synthetic add -l $LLDB_LOOKUP.synthetic_lookup -x "$type" --category Rust""")
                    executeInterpreterCommand(threadId, frameIndex, """type summary add -F $LLDB_LOOKUP.summary_lookup  -e -x -h "$type" --category Rust""")
                }

                executeInterpreterCommand(threadId, frameIndex, """type category enable Rust""")
            }

            LLDBRenderers.NONE -> {
            }
        }
    }

    private fun GDBDriver.loadPrettyPrinters() {
        val path = when (settings.gdbRenderers) {
            GDBRenderers.COMPILER -> {
                val sysroot = checkSysroot(sysroot, "Cannot load rustc renderers") ?: return
                "$sysroot/lib/rustlib/etc".systemDependentAndEscaped()
            }
            GDBRenderers.BUNDLED -> {
                prettyPrintersPath?.systemDependentAndEscaped() ?: return
            }
            GDBRenderers.NONE -> return
        }

        // Avoid multiline Python scripts due to https://youtrack.jetbrains.com/issue/CPP-9090
        val command = """python """ +
            """sys.path.insert(0, "$path"); """ +
            """import $GDB_LOOKUP; """ +
            """$GDB_LOOKUP.register_printers(gdb); """
        executeInterpreterCommand(threadId, frameIndex, command)
    }

    private fun checkSysroot(sysroot: String?, @Suppress("UnstableApiUsage") @NotificationContent message: String): String? {
        if (sysroot == null) {
            project.showBalloon(message, NotificationType.WARNING)
        }
        return sysroot
    }

    private fun String.systemDependentAndEscaped(): String {
        val path = if (toolchain is RsWslToolchain) {
            FileUtil.toSystemIndependentName(this)
        } else {
            FileUtil.toSystemDependentName(this)
        }
        return StringUtil.escapeStringCharacters(path)
    }

    companion object {
        private val LOG: Logger = logger<RsDebugProcessConfigurationHelper>()

        /**
         * Should be synchronized with `rust_types.py`
         *
         * `([a-z_]+::)+)` part is used instead of a specific path to make these regexes
         * more immune to changes in Rust stdlib module structure. Note that `\w` metacharacter
         * may be not supported by LLDB so it should not be used there
         */
        private val RUST_STD_TYPES: List<String> = listOf(
            "^(alloc::([a-z_]+::)+)String$",
            "^(&|&mut |\\*const |\\*mut )str$",
            "^(str)|((ptr_const|ptr_mut)\\$<str>)$",
            "^(str\\$)|((ref|ref_mut|ptr_const|ptr_mut)\\$<str\\$>)$",
            "^(&|&mut |\\*const |\\*mut )?\\[.*\\]$",
            "^(slice\\$<.+>)|((ptr_const|ptr_mut)\\$<slice\\$<.+> >)$",
            "^(slice2\\$<.+>)|((ref|ref_mut|ptr_const|ptr_mut)\\$<slice2\\$<.+> >)$",
            "^(std::ffi::([a-z_]+::)+)OsString$",
            "^((&|&mut )?std::ffi::([a-z_]+::)+)OsStr( \\*)?$",
            "^(std::([a-z_]+::)+)PathBuf$",
            "^(&?std::([a-z_]+::)+)Path( \\*)?$",
            "^((std|alloc)::ffi::([a-z_]+::)+)CString$",
            "^(&?(std|core)::ffi::([a-z_]+::)+)CStr( \\*)?$",
            "^(alloc::([a-z_]+::)+)Vec<.+>$",
            "^(alloc::([a-z_]+::)+)VecDeque<.+>$",
            "^(alloc::([a-z_]+::)+)BTreeSet<.+>$",
            "^(alloc::([a-z_]+::)+)BTreeMap<.+>$",
            "^(std::collections::([a-z_]+::)+)HashMap<.+>$",
            "^(std::collections::([a-z_]+::)+)HashSet<.+>$",
            "^alloc::rc::Rc<.+>$",
            "^alloc::rc::Weak<.+>$",
            "^alloc::(sync|arc)::Arc<.+>$",
            "^alloc::(sync|arc)::Weak<.+>$",
            "^(core::([a-z_]+::)+)Cell<.+>$",
            "^(core::([a-z_]+::)+)Ref<.+>$",
            "^(core::([a-z_]+::)+)RefMut<.+>$",
            "^(core::([a-z_]+::)+)RefCell<.+>$",
            "^core::num::([a-z_]+::)*NonZero.+$",
            "^core::ops::range::Range(From|Inclusive|To|ToInclusive)?<.+>$"
        )
    }
}
