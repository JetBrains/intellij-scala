package org.jetbrains.bsp.protocol.session

import ch.epfl.scala.bsp4j.BspConnectionDetails
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.platform.eel.provider.utils.EelPathUtils
import org.jetbrains.bsp.protocol.session.BspServerConnector.{BspCapabilities, ProcessBsp}
import org.jetbrains.bsp.protocol.session.BspSession.Builder
import org.jetbrains.bsp.protocol.session.GenericConnector.RemoteProcessPid
import org.jetbrains.bsp.{BspBundle, BspError, BspErrorMessage, BspSessionCreationError}
import org.jetbrains.plugins.scala.build.BuildReporter
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class GenericConnector(base: Path, compilerOutput: Path, capabilities: BspCapabilities, methods: List[ProcessBsp]) extends BspServerConnector() {

  override def connect(using reporter: BuildReporter, indicator: Option[ProgressIndicator]): Either[BspError, Builder] = {
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
      .withWorkingDirectory(base)
    val process = commandLine.createProcess()

    val cleanup = () => {
      // When the process is remote (e.g., com.intellij.execution.ijent.IjentChildProcessAdapter when running inside Docker),
      // calling #destroy sends SIGTERM to the entire process group, which kills not only the sbt/bsp server
      // but also the child sbt process that sbt spawns.
      // For example, the processes on the remote host may look like this:
      //    PID PGID
      //    137 137 /opt/java/openjdk/bin/java ... -bsp
      //    166 137 java -Dfile.encoding=UTF-8 -Dsbt.io.virtual=true -Dsbt.script=/usr/local/bin/sbt ... --detach-stdio
      // When SIGTERM is sent to the entire group (kill -TERM -137), both processes are killed.
      // It was implemented in https://youtrack.jetbrains.com/issue/IJPL-220691
      process.destroy()
    }

    val rootUri = EelPathUtils.getUriLocalToEel(base.toCanonicalPath)
    val compilerOutputUri = EelPathUtils.getUriLocalToEel(compilerOutput.toCanonicalPath)
    val initializeBuildParams = BspServerConnector.createInitializeBuildParams(rootUri, compilerOutputUri, capabilities)

    val isLocalProcess = OSProcessHandler.processCanBeKilledByOS(process)
    val pid =
      if (isLocalProcess) process.pid()
      else RemoteProcessPid

    BspSession.builder(pid, process.getInputStream, process.getErrorStream, process.getOutputStream, initializeBuildParams, cleanup)
  }
}

object GenericConnector {
  /**
   * Let's treat a PID of -1 as an indicator of a remote process (e.g., running inside EEL).
   *
   * Remote processes intentionally do not expose their PID,
   * because the [[java.lang.Process]] API assumes that the process runs locally,
   * and the pid may be used for manipulations with a process running
   * on a wrong machine.
   */
  private[session] val RemoteProcessPid = -1L
}
