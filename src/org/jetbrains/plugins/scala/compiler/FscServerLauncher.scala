package org.jetbrains.plugins.scala
package compiler

import java.io.File
import java.net.InetAddress
import java.util

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.plugins.scala.config.{Libraries, Version}

import scala.collection.JavaConverters._
import scala.io.Source

/**
 * Pavel Fatin
 */

class FscServerLauncher(project: Project) extends ProjectComponent {
  private val PortPattern = "\\d+$".r

  private var instance: Option[ServerInstance] = None

  def initComponent() {}

  def disposeComponent() {}

  def projectOpened() {}

  def projectClosed() {
    if (running) stop()
  }

  def init() {
    if (!running) start()
  }

  private def start() {
    val settings = ScalacSettings.getInstance(project)

    val xmx = if (settings.MAXIMUM_HEAP_SIZE.isEmpty) Nil else List("-Xmx%sm".format(settings.MAXIMUM_HEAP_SIZE))
    val vmParameters = localeOptions.toList ::: xmx ::: settings.VM_PARAMETERS.split(" ").toList

    val environment = toEnvironment(project)
    val reportsPort: Boolean = environment.compilerVersion >= Version("2.9")

    val additionalOptions = settings.FSC_OPTIONS.split(" ").toList
    val options = if (reportsPort) "-v" :: additionalOptions else additionalOptions

    val process = runProcess(environment, "scala.tools.nsc.CompileServer", vmParameters, options)
    val port = if (reportsPort) readPort(process) else None

    val watcher = new ProcessWatcher(process)
    instance = Some(ServerInstance(environment, watcher, port))
    watcher.startNotify()
  }

  private def localeOptions: Seq[String] = {
    val list = new util.LinkedList[String]()
    list.asScala
  }

  def stop() {
    instance.foreach { it =>
      sendCommandTo(it, "-shutdown")
      it.destroyProcess()
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
//    process.waitFor(); // avoid SCL-3646 (Latest version of Plugin Hangs IDEA on project close or exit)
  }

  def running: Boolean = instance.exists(_.running)

  def errors(): Seq[String] = instance.map(_.errors()).getOrElse(Seq.empty)

  def port: Int = instance.flatMap(_.port).getOrElse(-1)

  def compilerVersion: Option[String] = instance.map(_.environment.compilerVersion.text)

  private def toEnvironment(project: Project): Environment = {
    val sdk = Option(ProjectRootManager.getInstance(project).getProjectSdk)
            .getOrElse(throw new RuntimeException("No project SDK specified"))

    val sdkType = sdk.getSdkType.asInstanceOf[JavaSdkType]

    val settings = ScalacSettings.getInstance(project)
    val lib = Libraries.findBy(settings.COMPILER_LIBRARY_NAME, settings.COMPILER_LIBRARY_LEVEL, project)
            .getOrElse(throw new RuntimeException("No FSC instantiation library specified"))

    Environment(sdkType.getVMExecutablePath(sdk), lib.files.toList, new Version(lib.version.get))
  }

  private def runProcess(environment: Environment, className: String,
                         vmParameters: Seq[String], options: Seq[String]): Process = {
    val classpath = environment.libraries.mkString(File.pathSeparator)
    val args = Array(environment.java) ++ vmParameters ++ List("-cp", classpath, className) ++ options
    new ProcessBuilder(args: _*).start()
  }

  private def readPort(process: Process): Option[Int] = {
    val source = Source.fromInputStream(process.getInputStream)
    try {
      source.getLines().toStream.headOption.flatMap { line =>
        PortPattern.findFirstIn(line).map(_.toInt)
      }
    } finally {
      source.close()
    }
  }

  def getComponentName = getClass.getSimpleName


  private case class Environment(java: String, libraries: Seq[File], compilerVersion: Version)

  private case class ServerInstance(environment: Environment, watcher: ProcessWatcher, port: Option[Int])  {
    def running: Boolean = watcher.running

    def errors(): Seq[String] = watcher.errors()

    def destroyProcess() {
      watcher.destroyProcess()
    }
  }
}