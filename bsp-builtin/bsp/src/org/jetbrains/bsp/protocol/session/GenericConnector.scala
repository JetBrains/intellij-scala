package org.jetbrains.bsp.protocol.session

import ch.epfl.scala.bsp4j.BspConnectionDetails
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.provider.utils.EelPathUtils
import org.jetbrains.bsp.project.importing.setup.SbtOverBspVmOptions
import org.jetbrains.bsp.protocol.session.BspServerConnector.{BspCapabilities, ProcessBsp}
import org.jetbrains.bsp.protocol.session.BspSession.Builder
import org.jetbrains.bsp.protocol.session.GenericConnector.RemoteProcessPid
import org.jetbrains.bsp.{BspBundle, BspError, BspErrorMessage, BspSessionCreationError}
import org.jetbrains.plugins.scala.DependencyManagerBase.Resolver
import org.jetbrains.plugins.scala.build.BuildReporter
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.isUnitTestMode
import org.jetbrains.sbt.process.SbtRunner

import java.nio.file.Path
import scala.jdk.CollectionConverters.*

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
    val commandLine = new GeneralCommandLine(argv(details).asJava)
      .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
      .withWorkingDirectory(base)

    if isUnitTestMode then
      if SystemInfo.isWindows then
        // Add coursier environment variables when starting the sbt/BSP server in tests on Windows,
        // because the same issue with hanging sbt processes has been observed during project import in sbt/BSP tests.
        commandLine.withEnvironment(SbtRunner.defaultCoursierDirectoriesAsEnvVariables().asJava)
      end if
      // In tests, BSP servers that resolve dependencies from coursier's default repositories (`ivy2Local|central`),
      // e.g. Scala CLI when it fetches Bloop, use the JetBrains Maven Central mirror instead of Maven Central,
      // to avoid HTTP Error 429 Too Many Requests in the CI (SCL-25750)
      commandLine.withEnvironment("COURSIER_REPOSITORIES", s"ivy2Local|${Resolver.JetBrainsMavenCentralMirror.root}")
    end if

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

  /**
   * The command of the connection file, with the [[SbtOverBspVmOptions]] appended for sbt.
   *
   * The command of sbt (`... xsbt.boot.Boot -bsp`) starts the sbt BSP client, which sets the `-Dkey=value` arguments as
   * system properties and passes them on to the sbt server that it starts, so they apply to the server as well.
   */
  private def argv(details: BspConnectionDetails): Seq[String] = {
    val argv = details.getArgv.asScala.toSeq
    // The name that sbt writes to its connection file, see `sbt.internal.bsp.BuildServerConnection.name`
    if (details.getName == "sbt") argv ++ SbtOverBspVmOptions.getInstance.vmOptions
    else argv
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
