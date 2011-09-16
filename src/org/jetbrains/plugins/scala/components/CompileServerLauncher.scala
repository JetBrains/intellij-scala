package org.jetbrains.plugins.scala
package components

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import java.io.File
import io.Source
import compiler.ScalacSettings
import config.Libraries
import com.intellij.openapi.components.ProjectComponent

/**
 * Pavel Fatin
 */

class CompileServerLauncher(project: Project) extends ProjectComponent {
  private var instance: Option[ServerInstance] = None

  private val watcher = new ProcesWatcher()

  def initComponent() {}

  def disposeComponent() {}

  def projectOpened() {}

  def projectClosed() {
    println("Closing")
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
    val options = "-v" :: settings.FSC_OPTIONS.split(" ").toList

    val environment = toEnvironment(project)
    val process = runProcess(environment, "scala.tools.nsc.CompileServer", vmParameters, options)
    val port = readPort(process)

    instance = Some(ServerInstance(environment, process, port))

    watcher.watch(process)
  }

  def stop() {
    instance.foreach { it =>
      val process = runProcess(it.environment, "scala.tools.nsc.CompileClient", Nil, List(it.port.toString, "-shutdown"))
      process.waitFor();
      it.process.destroy()
    }
  }

  def running: Boolean = watcher.running

  def port: Int = instance.map(_.port).getOrElse(-1)

  private def toEnvironment(project: Project): Environment = {
    val sdk = Option(ProjectRootManager.getInstance(project).getProjectSdk).getOrElse(throw new RuntimeException())
    val home = Option(sdk.getHomePath).getOrElse(throw new RuntimeException())

    val settings = ScalacSettings.getInstance(project)
    val lib = Libraries.findBy(settings.COMPILER_LIBRARY_NAME, settings.COMPILER_LIBRARY_LEVEL, project).getOrElse(throw new RuntimeException())

    val java = new File(new File(home, "bin"), "java").getPath

    Environment(java, lib.files.toList)
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
      source.getLines().next().takeRight(5).toInt
    } finally {
      source.close()
    }
  }

  def getComponentName = getClass.getSimpleName


  private case class Environment(java: String, libraries: Seq[File])

  private case class ServerInstance(environment: Environment, process: Process, port: Int)
}