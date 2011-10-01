package org.jetbrains.plugins.scala
package components

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import java.io.File
import io.Source
import compiler.ScalacSettings
import config.Libraries
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.projectRoots.JavaSdkType
import java.net.InetAddress

/**
 * Pavel Fatin
 */

class CompileServerLauncher(project: Project) extends ProjectComponent {
  private val PortPattern = "\\d+$".r

  private var instance: Option[ServerInstance] = None

  private val watcher = new ProcesWatcher()

  def initComponent() {}

  def disposeComponent() {}

  def projectOpened() {}

  def projectClosed() {
    if (running) stop()
    watcher.stop()
  }

  def init() {
    if (!running) start()
  }

  private def start() {
    val settings = ScalacSettings.getInstance(project)

    val xmx = if (settings.MAXIMUM_HEAP_SIZE.isEmpty) Nil else List("-Xmx%sm".format(settings.MAXIMUM_HEAP_SIZE))
    val vmParameters = xmx ::: settings.VM_PARAMETERS.split(" ").toList
    
    val environment = toEnvironment(project)
    val reportsPort: Boolean = environment.compilerVersion.startsWith("2.9")

    val additionalOptions = settings.FSC_OPTIONS.split(" ").toList
    val options = if (reportsPort) "-v" :: additionalOptions else additionalOptions

    val process = runProcess(environment, "scala.tools.nsc.CompileServer", vmParameters, options)
    val port = if (reportsPort) Some(readPort(process)) else None

    instance = Some(ServerInstance(environment, process, port))

    watcher.watch(process)
  }

  def stop() {
    instance.foreach { it =>
      sendCommandTo(it, "-shutdown")
      it.process.destroy()
    }
  }

  def reset() {
    instance.foreach(sendCommandTo(_, "-reset"))
  }

  private def sendCommandTo(instance: ServerInstance, command: String) {
    val options = instance.port.map { port =>
      val server = String.format("%s:%s", InetAddress.getLocalHost.getHostAddress, port.toString)
      List("-server", server, command)
    } getOrElse {
      List(command)
    }

    val process = runProcess(instance.environment, "scala.tools.nsc.CompileClient", Nil, options)
    process.waitFor();
  }

  def running: Boolean = watcher.running

  def port: Int = instance.flatMap(_.port).getOrElse(-1)

  def compilerVersion: Option[String] = instance.map(_.environment.compilerVersion)

  private def toEnvironment(project: Project): Environment = {
    val sdk = Option(ProjectRootManager.getInstance(project).getProjectSdk)
            .getOrElse(throw new RuntimeException("No project SDK specified"))

    val sdkType = sdk.getSdkType.asInstanceOf[JavaSdkType]

    val settings = ScalacSettings.getInstance(project)
    val lib = Libraries.findBy(settings.COMPILER_LIBRARY_NAME, settings.COMPILER_LIBRARY_LEVEL, project)
            .getOrElse(throw new RuntimeException("No FSC instantiation library specified"))

    Environment(sdkType.getVMExecutablePath(sdk), lib.files.toList, lib.version.get)
  }

  private def runProcess(environment: Environment, className: String,
                         vmParameters: Seq[String], options: Seq[String]): Process = {
    val classpath = environment.libraries.mkString(File.pathSeparator)
    val args = Array(environment.java) ++ vmParameters ++ List("-cp", classpath, className) ++ options
    new ProcessBuilder(args: _*).redirectErrorStream(true).start();
  }

  private def readPort(process: Process): Int = {
    val source = Source.fromInputStream(process.getInputStream)
    try {
      val line = source.getLines().next()
      PortPattern.findFirstIn(line).get.toInt
    } finally {
      source.close()
    }
  }

  def getComponentName = getClass.getSimpleName


  private case class Environment(java: String, libraries: Seq[File], compilerVersion: String)

  private case class ServerInstance(environment: Environment, process: Process, port: Option[Int])
}