package org.jetbrains.plugins.scala
package compiler

import java.io.{File, IOException}

import com.intellij.compiler.server.BuildManager
import com.intellij.ide.plugins.{IdeaPluginDescriptor, PluginManager}
import com.intellij.notification.{Notification, NotificationListener, NotificationType, Notifications}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.net.NetUtils
import gnu.trove.TByteArrayList
import javax.swing.event.HyperlinkEvent
import org.jetbrains.jps.incremental.BuilderService
import org.jetbrains.plugins.hydra.compiler.HydraCompilerSettingsManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.{Platform, ProjectExt}

import scala.collection.JavaConverters._
import scala.util.control.Exception._

/**
 * @author Pavel Fatin
 */
object CompileServerLauncher {
  private var serverInstance: Option[ServerInstance] = None
  private val LOG = Logger.getInstance(getClass)

  ShutDownTracker.getInstance().registerShutdownTask(() =>
    if (running) stop()
  )

  def tryToStart(project: Project): Boolean = {
    if (!running) {
      val started = start(project)
      if (started) {
        try new RemoteServerRunner(project).send("addDisconnectListener", Seq.empty, null)
        catch {
          case _: Exception =>
        }
      }
      started
    }
    else true
  }

  private def start(project: Project): Boolean = {

    val result = compileServerJdk(project).map(start(project, _)) match {
      case None                 => Left("JDK for compiler process not found")
      case Some(Left(msg))      => Left(msg)
      case Some(Right(process)) =>
        invokeLater { CompileServerManager.configureWidget(project) }
        Right(process)
    }

    result match {
      case Right(_)     => true
      case Left(error)  =>
        val title = "Cannot start Scala compile server"
        Notifications.Bus.notify(new Notification("scala", title, error, NotificationType.ERROR))
        LOG.error(title, error)
        false
    }
  }

  private def compilerServerAddtionalCP(): Seq[File] = for {
    extension <- NailgunServerAdditionalCp.EP_NAME.getExtensions
    filesPath <- extension.getClasspath.split(";")
    pluginId: PluginId = extension.getPluginDescriptor.getPluginId
    plugin: IdeaPluginDescriptor = PluginManager.getPlugin(pluginId)
    pluginsLibs = new File(plugin.getPath, "lib")
  } yield new File(pluginsLibs, filesPath)

  private def start(project: Project, jdk: JDK): Either[String, Process] = {
    val settings = ScalaCompileServerSettings.getInstance

    settings.COMPILE_SERVER_SDK = jdk.name
    saveSettings()

    compilerJars.partition(_.exists) match {
      case (presentFiles, Seq()) =>
        val bootCp = bootClasspath(project)
        val bootClassPathLibs = bootCp.map(_.getAbsolutePath)
        val bootclasspathArg =
          if (bootClassPathLibs.isEmpty) Nil
          else Seq("-Xbootclasspath/a:" + bootClassPathLibs.mkString(File.pathSeparator))
        val classpath = (jdk.tools +: (presentFiles ++ compilerServerAddtionalCP()))
          .map(_.canonicalPath)
          .mkString(File.pathSeparator)

        val freePort = CompileServerLauncher.findFreePort
        if (settings.COMPILE_SERVER_PORT != freePort) {
          new RemoteServerStopper(settings.COMPILE_SERVER_PORT).sendStop()
          settings.COMPILE_SERVER_PORT = freePort
          saveSettings()
        }

        val ngRunnerFqn = "org.jetbrains.plugins.scala.nailgun.NailgunRunner"
        val id = settings.COMPILE_SERVER_ID

        val shutdownDelay = settings.COMPILE_SERVER_SHUTDOWN_DELAY
        val shutdownDelayArg = if (settings.COMPILE_SERVER_SHUTDOWN_IDLE && shutdownDelay >= 0) {
          Seq(s"-Dshutdown.delay=$shutdownDelay")
        } else Nil

        val commands = jdk.executable.canonicalPath +: bootclasspathArg ++: "-cp" +: classpath +: jvmParameters ++: shutdownDelayArg ++:
          HydraCompilerSettingsManager.getHydraLogJvmParameter(project) ++: ngRunnerFqn +: freePort.toString +: id.toString +: Nil

        val builder = new ProcessBuilder(commands.asJava)

        if (settings.USE_PROJECT_HOME_AS_WORKING_DIR) {
          projectHome(project).foreach(dir => builder.directory(dir))
        }

        catching(classOf[IOException]).either(builder.start())
                .left.map(_.getMessage)
                .right.map { process =>
          val watcher = new ProcessWatcher(process, "scalaCompileServer")
          serverInstance = Some(ServerInstance(watcher, freePort, builder.directory(), withTimestamps(bootCp), jdk))
          watcher.startNotify()
          process
        }
      case (_, absentFiles) =>
        val paths = absentFiles.map(_.getPath).mkString(", ")
        Left("Required file(s) not found: " + paths)
    }
  }

  // TODO stop server more gracefully
  def stop() {
    serverInstance.foreach { it =>
      it.destroyAndWait(0L)
    }
  }

  def stopAndWaitTermination(timeoutMs: Long): Boolean = {
    serverInstance.forall { it =>
      it.destroyAndWait(timeoutMs)
    }
  }

  def stop(project: Project) {
    stop()

    ApplicationManager.getApplication invokeLater (() => {
      CompileServerManager.configureWidget(project)
    })
  }

  def running: Boolean = serverInstance.exists(_.running)

  def errors(): Seq[String] = serverInstance.map(_.errors()).getOrElse(Seq.empty)

  def port: Option[Int] = serverInstance.map(_.port)

  def compileServerSdk(project: Project): Option[Sdk] = {
    def defaultSdk = BuildManager.getBuildProcessRuntimeSdk(project).first

    val settings = ScalaCompileServerSettings.getInstance()

    val sdk =
      if (settings.USE_DEFAULT_SDK) defaultSdk
      else ProjectJdkTable.getInstance().findJdk(settings.COMPILE_SERVER_SDK)

    Option(sdk)
  }

  def compileServerJdk(project: Project): Option[JDK] = {
    val sdk = compileServerSdk(project)
    sdk.flatMap(toJdk)
  }

  def compilerJars: Seq[File] = {
    val jpsBuildersJar = new File(PathUtil.getJarPathForClass(classOf[BuilderService]))
    val utilJar = new File(PathUtil.getJarPathForClass(classOf[FileUtil]))
    val trove4jJar = new File(PathUtil.getJarPathForClass(classOf[TByteArrayList]))

    val pluginRoot = pluginPath
    val jpsRoot = new File(pluginRoot, "jps")

    Seq(
      jpsBuildersJar,
      utilJar,
      trove4jJar,
      new File(pluginRoot, "scala-library.jar"),
      new File(pluginRoot, "scala-reflect.jar"),
      new File(pluginRoot, "scala-nailgun-runner.jar"),
      new File(pluginRoot, "compiler-shared.jar"),
      new File(jpsRoot, "nailgun.jar"),
      new File(jpsRoot, "sbt-interface.jar"),
      new File(jpsRoot, "incremental-compiler.jar"),
      new File(jpsRoot, "compiler-jps.jar")
    )
  }

  def pluginPath: String = {
    if (ApplicationManager.getApplication.isUnitTestMode) new File(System.getProperty("plugin.path"), "lib").getCanonicalPath
    else new File(PathUtil.getJarPathForClass(getClass)).getParent
  }

  def bootClasspath(project: Project): Seq[File] = {
    val dottySdk = project.scalaModules.map(_.sdk).find(_.platform == Platform.Dotty)
    dottySdk.toSeq.flatMap(_.compilerClasspath)
  }

  private def withTimestamps(files: Seq[File]): Set[(File, Long)] = {
    files.map(f => (f, f.lastModified())).toSet
  }

  def jvmParameters: Seq[String] = {
    val settings = ScalaCompileServerSettings.getInstance
    val xmx = settings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE |> { size =>
      if (size.isEmpty) Nil else List("-Xmx%sm".format(size))
    }

    val (_, otherParams) = settings.COMPILE_SERVER_JVM_PARAMETERS.split(" ").partition(_.contains("-XX:MaxPermSize"))

    xmx ++ otherParams
  }

  def ensureServerRunning(project: Project): Boolean = {
    if (needRestart(project)) stop()

    running || tryToStart(project)
  }

  def needRestart(project: Project): Boolean = {
    val currentInstance = serverInstance
    val settings = ScalaCompileServerSettings.getInstance()
    currentInstance match {
      case None => true
      case Some(instance) =>
        val useProjectHome = settings.USE_PROJECT_HOME_AS_WORKING_DIR
        val workingDirChanged = useProjectHome && projectHome(project) != currentInstance.map(_.workingDir)
        val jdkChanged = compileServerJdk(project) match {
          case Some(projectJdk) => projectJdk != instance.jdk
          case _ => false
        }
        workingDirChanged || instance.bootClasspath != withTimestamps(bootClasspath(project)) || jdkChanged
    }
  }

  def ensureNotRunning(project: Project): Unit = {
    if (running) stop(project)
  }

  def findFreePort: Int = {
    val port = ScalaCompileServerSettings.getInstance().COMPILE_SERVER_PORT
    if (NetUtils.canConnectToSocket("localhost", port))
      NetUtils.findAvailableSocketPort()
    else port
  }

  def saveSettings(): Unit = invokeAndWait {
    ApplicationManager.getApplication.saveSettings()
  }

  private def projectHome(project: Project): Option[File] = {
    for {
      dir <- Option(project.getBaseDir)
      path <- Option(dir.getCanonicalPath)
      file = new File(path)
      if file.exists()
    } yield file
  }


  class ConfigureLinkListener(project: Project) extends NotificationListener.Adapter {
    def hyperlinkActivated(notification: Notification, event: HyperlinkEvent) {
      CompileServerManager.showCompileServerSettingsDialog(project)
      notification.expire()
    }
  }

  class ConfigureJDKListener(project: Project) extends NotificationListener.Adapter {
    def hyperlinkActivated(notification: Notification, event: HyperlinkEvent) {
      val jdkEntries = project.modulesWithScala.flatMap { module =>
        val rootManager = ModuleRootManager.getInstance(module)
        Option(OrderEntryUtil.findJdkOrderEntry(rootManager, rootManager.getSdk))
      }
      val service = ProjectSettingsService.getInstance(project)

      if (jdkEntries.isEmpty) service.openProjectSettings()
      else service.openLibraryOrSdkSettings(jdkEntries.head)

      notification.expire()
    }
  }
}

private case class ServerInstance(watcher: ProcessWatcher,
                                  port: Int,
                                  workingDir: File,
                                  bootClasspath: Set[(File, Long)],
                                  jdk: JDK) {
  private var stopped = false

  def running: Boolean = !stopped && watcher.running

  def errors(): Seq[String] = watcher.errors()

  def destroyAndWait(timeoutMs: Long): Boolean = {
    stopped = true
    watcher.destroyAndWait(timeoutMs)
  }
}
