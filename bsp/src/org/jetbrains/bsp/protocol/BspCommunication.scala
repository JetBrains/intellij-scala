package org.jetbrains.bsp.protocol

import java.io.File
import java.net.URI
import java.nio.file._

import ch.epfl.scala.bsp._
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.net.NetUtils
import monix.eval.Task
import monix.execution.Scheduler
import org.jetbrains.bsp.BspError
import org.jetbrains.bsp.protocol.BspServerConnector._
import org.jetbrains.bsp.settings.BspExecutionSettings

import scala.util.Random

class BspCommunication(project: Project) extends ProjectComponent {
  // TODO support persistent sessions for more features!
  // * quicker response times
  // * background project update notifications
  // * background compilation and error reporting/highlighting
}


object BspCommunication {


  def prepareSession(base: File, bspExecutionSettings: BspExecutionSettings)(implicit scheduler: Scheduler): Task[Either[BspError, BspSession]] = {

    // .bsp directory -> use GenericConnector (once we have an agreement how that works)

    val initParams = InitializeBuildParams(
      rootUri = Uri(base.getCanonicalFile.toURI.toString),
      BuildClientCapabilities(List("scala","java"), providesFileWatching = false) // TODO we can provide file watching
    )

    val id = java.lang.Long.toString(Random.nextLong(), Character.MAX_RADIX)

    val tcpMethod = TcpBsp(new URI("localhost"), findFreePort(5001))

    val preferredMethod =
      if (SystemInfo.isWindows) WindowsLocalBsp(id)
      else if (SystemInfo.isUnix) {
        val tempDir = Files.createTempDirectory("bsp-")
        val socketFilePath = tempDir.resolve(s"$id.socket")
        val socketFile = socketFilePath.toFile
        socketFile.deleteOnExit()
        UnixLocalBsp(socketFile)
      }
      else tcpMethod


    val bloopConfigDir = new File(base, ".bloop").getCanonicalFile

    val connector =
      if (bloopConfigDir.exists()) new BloopConnector(bspExecutionSettings.bloopExecutable, base, initParams)
      else {
        // TODO need a protocol to detect generic bsp server
        new GenericConnector(base, initParams)
      }

    connector.connect(preferredMethod, tcpMethod)
  }


  private def findFreePort(port: Int): Int = {
    val port = 5001
    if (NetUtils.canConnectToSocket("localhost", port)) port
    else NetUtils.findAvailableSocketPort()
  }

}
