/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.bsp.service

import ch.epfl.scala.bsp4j.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.project.stateStore
import com.intellij.util.EnvironmentUtil
import com.intellij.util.concurrency.AppExecutorUtil
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.rust.bsp.BspClient
import org.rust.cargo.toolchain.impl.CargoMetadata
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path

class BspConnectionServiceImpl(val project: Project) : BspConnectionService {

    private var bspServer: BspServer? = null
    private var rustBspMockServer: RustBuildServer = RustBuildServer()
    private var bspClient: BspClient? = null
    private var disconnectActions: MutableList<() -> Unit> = mutableListOf()

    override fun getBspServer(): BspServer {
        createBspServerIfNull()
        return bspServer!!
    }

    override fun getBspClient(): BspClient {
        createBspServerIfNull()
        return bspClient!!
    }

    override fun connect() {
        println("Starting BSP server")
        getBspServer()
    }

    override fun doStuff() {
        try {
            val server = getBspServer()
            val initializeBuildResult =
                queryForInitialize(server).catchSyncErrors { println("Error while initializing BSP server $it") }.get()
            server.onBuildInitialized()
            println("BSP server initialized: $initializeBuildResult")

            val projectDetails = calculateProjectDetailsWithCapabilities(server, rustBspMockServer, initializeBuildResult.capabilities) {
                println("BSP server capabilities: $it")
            }

            println("BSP project details: $projectDetails")
        } catch (e: Exception) {
            println("Error while initializing BSP server: ${e.message}")
        }
    }

    override fun getProjectData(): CargoMetadata.Project {
        val server = getBspServer()
        val initializeBuildResult =
            queryForInitialize(server).catchSyncErrors { println("Error while initializing BSP server $it") }.get()
        server.onBuildInitialized()

        return calculateProjectDetailsWithCapabilities(server, rustBspMockServer, initializeBuildResult.capabilities) {
            println("BSP server capabilities: $it")
        }
    }

    private fun createBspServerIfNull() {
        if (bspServer == null) {
            bspServer = getBspConnectionDetailsFile()
                ?.let { parseBspConnectionDetails(it) }
                ?.let { createBspServer(it) }!!
        }
    }

    private fun queryForInitialize(server: BspServer): CompletableFuture<InitializeBuildResult> {
        val buildParams = createInitializeBuildParams()
        return server.buildInitialize(buildParams)
    }

    private fun createInitializeBuildParams(): InitializeBuildParams {
        val projectBaseDir = project.basePath
        val params = InitializeBuildParams(
            "IntelliJ-Rust",
            "0.4.0",
            "2.0.0",
            projectBaseDir.toString(),
            BuildClientCapabilities(listOf("java"))
        )
        val dataJson = JsonObject()
        dataJson.addProperty("clientClassesRootDir", "$projectBaseDir/out")
        params.data = dataJson

        return params
    }

    override fun disconnect() {
        val exceptions = disconnectActions.mapNotNull { executeDisconnectActionAndReturnThrowableIfFailed(it) }
        disconnectActions.clear()
        throwExceptionWithSuppressedIfOccurred(exceptions)

        bspServer = null
        bspClient = null
    }

    private fun executeDisconnectActionAndReturnThrowableIfFailed(disconnectAction: () -> Unit): Throwable? =
        try {
            disconnectAction()
            null
        } catch (e: Exception) {
            e
        }

    private fun throwExceptionWithSuppressedIfOccurred(exceptions: List<Throwable>) {
        val firstException = exceptions.firstOrNull()

        if (firstException != null) {
            exceptions
                .drop(1)
                .forEach { firstException.addSuppressed(it) }

            throw firstException
        }
    }

    private fun createLauncher(bspIn: InputStream, bspOut: OutputStream, client: BuildClient): Launcher<BspServer> =
        Launcher.Builder<BspServer>()
            .setRemoteInterface(BspServer::class.java)
            .setExecutorService(AppExecutorUtil.getAppExecutorService())
            .setInput(bspIn)
            .setOutput(bspOut)
            .setLocalService(client)
            .create()

    private fun createAndStartProcess(bspConnectionDetails: BspConnectionDetails): Process =
        ProcessBuilder(bspConnectionDetails.argv)
            .directory(project.stateStore.projectBasePath.toFile())
            .withRealEnvs()
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

    private fun createBspClient(): BspClient {
        return BspClient()
    }

    private fun createBspServer(bspConnectionDetails: BspConnectionDetails): BspServer {
        val process = createAndStartProcess(bspConnectionDetails)

        disconnectActions.add { bspServer?.buildShutdown() }
        disconnectActions.add { bspServer?.onBuildExit() }

        disconnectActions.add { process.waitFor(3, TimeUnit.SECONDS) }
        disconnectActions.add { process.destroy() }

        val bspClient = createBspClient()

        val bspIn = process.inputStream
        disconnectActions.add { bspIn.close() }

        val bspOut = process.outputStream
        disconnectActions.add { bspOut.close() }

        val launcher = createLauncher(bspIn, bspOut, bspClient)
        val listening = launcher.startListening()
        disconnectActions.add { listening.cancel(true) }

        this.bspClient = bspClient

        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(BspServer::class.java)) { _, method, args ->
            println("Calling method: ${method.name} with args: ${args?.joinToString()}")
            method.invoke(launcher.remoteProxy, *args.orEmpty())
        } as BspServer
    }

    private fun parseBspConnectionDetails(file: VirtualFile): BspConnectionDetails? =
        try {
            Gson().fromJson(VfsUtil.loadText(file), BspConnectionDetails::class.java)
        } catch (e: Exception) {
            println("Parsing file '$file' to BspConnectionDetails failed! ${e.message}")
            null
        }

    private fun getBspConnectionDetailsFile(): VirtualFile? =
        "${project.stateStore.projectBasePath}/.bsp/bazelbsp.json".toVirtualFile()

    private fun String.toVirtualFile(): VirtualFile? =
        VirtualFileManager.getInstance().findFileByNioPath(Path(this))
}

interface BspServer : BuildServer


//TODO - that should be implemented in build-server-protocol
class RustBuildServer {

    fun projectPackages(): CompletableFuture<List<CargoMetadata.Package>> {
        return CompletableFuture.completedFuture(listOf())
    }

    fun projectDependencies(): CompletableFuture<List<CargoMetadata.ResolveNode>> {
        return CompletableFuture.completedFuture(listOf())
    }

    fun version(): CompletableFuture<Int> {
        return CompletableFuture.completedFuture(1)
    }

    fun workspaceMembers(): CompletableFuture<List<String>> {
        return CompletableFuture.completedFuture(listOf())
    }

    fun workspaceRoot(): CompletableFuture<String> {
        return CompletableFuture.completedFuture("")
    }
}

fun ProcessBuilder.withRealEnvs(): ProcessBuilder {
    val env = environment()
    env.clear()
    env.putAll(EnvironmentUtil.getEnvironmentMap())

    return this
}


fun calculateProjectDetailsWithCapabilities(
    server: BspServer,
    rustBspMockServer: RustBuildServer,
    buildServerCapabilities: BuildServerCapabilities,
    errorCallback: (Throwable) -> Unit
): CargoMetadata.Project {
    val projectPackages = queryForPackages(rustBspMockServer).get()

    val resolve = queryForResolve(rustBspMockServer).get()
    val version = queryForVersion(rustBspMockServer).get()
    val workspaceMembers = queryForMembers(rustBspMockServer).get()
    val workspaceRoot = queryForRoot(rustBspMockServer).get()

    return CargoMetadata.Project(projectPackages, resolve, version, workspaceMembers, workspaceRoot)
}

fun queryForPackages(server: RustBuildServer): CompletableFuture<List<CargoMetadata.Package>> {
    return server.projectPackages()
}

fun queryForResolve(server: RustBuildServer): CompletableFuture<CargoMetadata.Resolve> {
    return server.projectDependencies().thenApply { CargoMetadata.Resolve(it) }
}

fun queryForVersion(server: RustBuildServer): CompletableFuture<Int> {
    return server.version()
}

fun queryForMembers(server: RustBuildServer): CompletableFuture<List<String>> {
    return server.workspaceMembers()
}

fun queryForRoot(server: RustBuildServer): CompletableFuture<String> {
    return server.workspaceRoot()
}


private fun <T> CompletableFuture<T>.catchSyncErrors(errorCallback: (Throwable) -> Unit): CompletableFuture<T> =
    this.whenComplete { _, exception ->
        exception?.let { errorCallback(it) }
    }

