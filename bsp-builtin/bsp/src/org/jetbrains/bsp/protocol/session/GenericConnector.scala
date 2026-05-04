package org.jetbrains.bsp.protocol.session

import ch.epfl.scala.bsp4j.BspConnectionDetails
import com.intellij.execution.configurations.GeneralCommandLine
import org.jetbrains.bsp.protocol.session.BspServerConnector.{BspCapabilities, ProcessBsp}
import org.jetbrains.bsp.protocol.session.BspSession.Builder
import org.jetbrains.bsp.{BspBundle, BspError, BspErrorMessage, BspSessionCreationError}
import org.jetbrains.plugins.scala.build.BuildReporter
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class GenericConnector(base: Path, compilerOutput: Path, capabilities: BspCapabilities, methods: List[ProcessBsp]) extends BspServerConnector() {

  override def connect(reporter: BuildReporter): Either[BspError, Builder] = {
    methods.collectFirst {
      case ProcessBsp(details: BspConnectionDetails) =>
        // TODO check bsp version compatibility
        // TODO check languages compatibility
        try {
          Right(prepareBspSession(details))
        } catch {
          case e: Exception =>
            Left(BspSessionCreationError(BspBundle.message("bsp.protocol.session.creation.failed", e.getMessage), e))
        }
    }.getOrElse(Left(BspErrorMessage(BspBundle.message("bsp.protocol.no.supported.connection.method.for.this.server"))))
  }

  private def prepareBspSession(details: BspConnectionDetails): Builder = {
    val commandLine = new GeneralCommandLine(details.getArgv)
      .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
    val builder = commandLine.toProcessBuilder.directory(base.toFile)
    val process = builder.start()

    val cleanup = () => {
      process.destroy()
    }

    val rootUri = base.toCanonicalPath.toUri
    val compilerOutputUri = compilerOutput.toCanonicalPath.toUri
    val initializeBuildParams = BspServerConnector.createInitializeBuildParams(rootUri, compilerOutputUri, capabilities)

    BspSession.builder(process.pid(), process.getInputStream, process.getErrorStream, process.getOutputStream, initializeBuildParams, cleanup)
  }
}
