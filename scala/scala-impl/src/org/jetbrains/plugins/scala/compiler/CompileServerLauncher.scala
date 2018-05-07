package org.jetbrains.plugins.scala
package compiler

import java.io.{File, IOException}
import javax.swing.event.HyperlinkEvent

import com.intellij.compiler.server.BuildManager
import com.intellij.ide.plugins.{IdeaPluginDescriptor, PluginManager}
import com.intellij.notification.{Notification, NotificationListener, NotificationType, Notifications}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.net.NetUtils
import gnu.trove.TByteArrayList
import org.jetbrains.jps.incremental.BuilderService
import org.jetbrains.plugins.hydra.compiler.HydraCompilerSettingsManager
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.{Platform, ProjectExt}

import scala.collection.JavaConverters._
import scala.util.control.Exception._

/**
 * @author Pavel Fatin
 */
class CompileServerLauncher extends ApplicationComponent {
   private var serverInstance: Option[ServerInstance] = None

  override def disposeComponent(): Unit = {
     if (running) stop()
   }

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

    compileServerJdk(project) match {
      case None =>
        val title = "Cannot start Scala compile server"
        val content = "JDK for compiler process not found"
        Notifications.Bus.notify(new Notification("scala", title, content, NotificationType.ERROR))
        false
      case Some(jdk) =>
        start(project, jdk)
        invokeLater {
          CompileServerManager.configureWidget(project)
        }
        true
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
    import org.jetbrains.plugins.scala.compiler.CompileServerLauncher.{compilerJars, jvmParameters}

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

  override def getComponentName: String = getClass.getSimpleName
}

object CompileServerLauncher {
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

  def instance: CompileServerLauncher = ApplicationManager.getApplication.getComponent(classOf[CompileServerLauncher])

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
    val launcher = CompileServerLauncher.instance

    if (needRestart(project)) launcher.stop()

    launcher.running || launcher.tryToStart(project)
  }

  def needRestart(project: Project): Boolean = {
    val serverInstance = CompileServerLauncher.instance.serverInstance
    val settings = ScalaCompileServerSettings.getInstance()
    serverInstance match {
      case None => true
      case Some(instance) =>
        val useProjectHome = settings.USE_PROJECT_HOME_AS_WORKING_DIR
        val workingDirChanged = useProjectHome && projectHome(project) != serverInstance.map(_.workingDir)
        val jdkChanged = compileServerJdk(project) match {
          case Some(projectJdk) => projectJdk != instance.jdk
          case _ => false
        }
        workingDirChanged || instance.bootClasspath != withTimestamps(bootClasspath(project)) || jdkChanged
    }
  }

  def ensureNotRunning(project: Project) {
    val launcher = CompileServerLauncher.instance
    if (launcher.running) launcher.stop(project)
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
