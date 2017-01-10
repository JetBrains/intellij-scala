package org.jetbrains.plugins.scala
package compiler

import java.io.{File, IOException}
import javax.swing.event.HyperlinkEvent

import com.intellij.notification.{Notification, NotificationListener, NotificationType, Notifications}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkVersion, ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.impl.OrderEntryUtil
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.roots.{ModuleRootManager, ProjectRootManager}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.net.NetUtils
import gnu.trove.TByteArrayList
import org.jetbrains.jps.incremental.BuilderService
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.{ProjectExt, ScalaLanguageLevel, ScalaModule}

import scala.collection.JavaConverters._
import scala.util.control.Exception._

/**
 * @author Pavel Fatin
 */
class CompileServerLauncher extends ApplicationComponent {
   private var serverInstance: Option[ServerInstance] = None

   def initComponent() {}

   def disposeComponent() {
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
    val applicationSettings = ScalaCompileServerSettings.getInstance

    jdkChangeRequired(project).foreach(applicationSettings.COMPILE_SERVER_SDK = _)

    if (applicationSettings.COMPILE_SERVER_SDK == null) {
      // Try to find a suitable JDK
      val allJdks = availableJdks(project)
      val chosen = allJdks.headOption.map(_.getName)

      chosen.foreach(applicationSettings.COMPILE_SERVER_SDK = _)

//       val message = "JVM SDK is automatically selected: " + name +
//               "\n(can be changed in Application Settings / Scala)"
//       Notifications.Bus.notify(new Notification("scala", "Scala compile server",
//         message, NotificationType.INFORMATION))
    }

    findJdkByName(applicationSettings.COMPILE_SERVER_SDK)
      .left.map(_ + "\nPlease either disable Scala compile server or configure a valid JVM SDK for it.")
      .right.flatMap(start(project, _)) match {
      case Left(error) =>
        val title = "Cannot start Scala compile server"
        val content = s"<html><body>${error.replace("\n", "<br>")} <a href=''>Configure</a></body></html>"
        Notifications.Bus.notify(new Notification("scala", title, content, NotificationType.ERROR, ConfigureLinkListener))
        false
      case Right(_) =>
        invokeLater {
          CompileServerManager.instance(project).configureWidget()
        }
        true
    }
  }

  private def start(project: Project, jdk: JDK): Either[String, Process] = {
    import org.jetbrains.plugins.scala.compiler.CompileServerLauncher.{compilerJars, jvmParameters}

    compilerJars.partition(_.exists) match {
      case (presentFiles, Seq()) =>
        val bootCp = bootClasspath(project)
        val bootClassPathLibs = bootCp.map(_.getAbsolutePath)
        val bootclasspathArg =
          if (bootClassPathLibs.isEmpty) Nil
          else Seq("-Xbootclasspath/a:" + bootClassPathLibs.mkString(File.pathSeparator))
        val classpath = (jdk.tools +: presentFiles).map(_.canonicalPath).mkString(File.pathSeparator)
        val settings = ScalaCompileServerSettings.getInstance

        val freePort = CompileServerLauncher.findFreePort
        if (settings.COMPILE_SERVER_PORT != freePort) {
          new RemoteServerStopper(settings.COMPILE_SERVER_PORT).sendStop()
          settings.COMPILE_SERVER_PORT = freePort
          ApplicationManager.getApplication.saveSettings()
        }

        val ngRunnerFqn = "org.jetbrains.plugins.scala.nailgun.NailgunRunner"
        val id = settings.COMPILE_SERVER_ID

        val shutdownDelay = settings.COMPILE_SERVER_SHUTDOWN_DELAY
        val shutdownDelayArg = if (settings.COMPILE_SERVER_SHUTDOWN_IDLE && shutdownDelay >= 0) {
          Seq(s"-Dshutdown.delay=$shutdownDelay")
        } else Nil

        val commands = jdk.executable.canonicalPath +: bootclasspathArg ++: "-cp" +: classpath +: jvmParameters ++: shutdownDelayArg ++:
                ngRunnerFqn +: freePort.toString +: id.toString +: Nil

        val builder = new ProcessBuilder(commands.asJava)

        if (settings.USE_PROJECT_HOME_AS_WORKING_DIR) {
          projectHome(project).foreach(dir => builder.directory(dir))
        }

        catching(classOf[IOException]).either(builder.start())
                .left.map(_.getMessage)
                .right.map { process =>
          val watcher = new ProcessWatcher(process, "scalaCompileServer")
          serverInstance = Some(ServerInstance(watcher, freePort, builder.directory(), withTimestamps(bootCp)))
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
      it.destroyProcess()
    }
  }

  def stop(project: Project) {
    stop()

    ApplicationManager.getApplication invokeLater new Runnable {
      override def run() {
        CompileServerManager.instance(project).configureWidget()
      }
    }
  }

  def running: Boolean = serverInstance.exists(_.running)

  def errors(): Seq[String] = serverInstance.map(_.errors()).getOrElse(Seq.empty)

  def port: Option[Int] = serverInstance.map(_.port)

  def getComponentName: String = getClass.getSimpleName
}

object CompileServerLauncher {
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
      new File(pluginRoot, "scala-nailgun-runner.jar"),
      new File(pluginRoot, "compiler-settings.jar"),
      new File(jpsRoot, "nailgun.jar"),
      new File(jpsRoot, "sbt-interface.jar"),
      new File(jpsRoot, "incremental-compiler.jar"),
      new File(jpsRoot, "scala-jps-plugin.jar"),
      dottyInterfacesJar
    )
  }

  def pluginPath: String = {
    if (ApplicationManager.getApplication.isUnitTestMode) new File(System.getProperty("plugin.path"), "lib").getCanonicalPath
    else new File(PathUtil.getJarPathForClass(getClass)).getParent
  }

  def dottyInterfacesJar: File = {
    val jpsDir = new File(pluginPath, "jps")
    new File(jpsDir, "dotty-interfaces.jar")
  }

  def bootClasspath(project: Project): Seq[File] = {
    val dottySdk = project.scalaModules.map(_.sdk).find(_.isDottySdk)
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

    val (userMaxPermSize, otherParams) = settings.COMPILE_SERVER_JVM_PARAMETERS.split(" ").partition(_.contains("-XX:MaxPermSize"))

    val defaultMaxPermSize = Some("-XX:MaxPermSize=256m")
    val needMaxPermSize = settings.COMPILE_SERVER_SDK < "1.8"
    val maxPermSize = if (needMaxPermSize) userMaxPermSize.headOption.orElse(defaultMaxPermSize) else None

    xmx ++ otherParams ++ maxPermSize
  }

  def ensureServerRunning(project: Project) {
    val launcher = CompileServerLauncher.instance

    if (needRestart(project)) launcher.stop()

    if (!launcher.running) launcher.tryToStart(project)
  }

  def needRestart(project: Project): Boolean = {
    val serverInstance = CompileServerLauncher.instance.serverInstance
    val settings = ScalaCompileServerSettings.getInstance()
    serverInstance match {
      case None => true
      case _ if jdkChangeRequired(project).nonEmpty => true
      case Some(instance) =>
        val useProjectHome = settings.USE_PROJECT_HOME_AS_WORKING_DIR
        val workingDirChanged = useProjectHome && projectHome(project) != serverInstance.map(_.workingDir)
        workingDirChanged || instance.bootClasspath != withTimestamps(bootClasspath(project))
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

  private def projectHome(project: Project): Option[File] = {
    for {
      dir <- Option(project.getBaseDir)
      path <- Option(dir.getCanonicalPath)
      file = new File(path)
      if file.exists()
    } yield file
  }

  private def availableJdks(project: Project): Seq[Sdk] = {
    val javaSdk = JavaSdk.getInstance
    Option(ProjectRootManager.getInstance(project).getProjectSdk) ++: ProjectJdkTable.getInstance.getSdksOfType(javaSdk).asScala
  }

  private def jdkChangeRequired(project: Project): Option[String] = {
    val javaSdk = JavaSdk.getInstance

    def isScala2_12(module: ScalaModule) = module.sdk.languageLevel >= ScalaLanguageLevel.Scala_2_12
    def isJava8(module: ScalaModule) = {
      val sdk = Option(ModuleRootManager.getInstance(module.module).getSdk)
      sdk.exists(javaSdk.getVersion(_).isAtLeast(JavaSdkVersion.JDK_1_8))
    }

    val requiresJdk8 = project.scalaModules.exists(m => isScala2_12(m) || isJava8(m))
    if (requiresJdk8) {
      val jdk8Names = availableJdks(project).toSet.filter(javaSdk.getVersion(_).isAtLeast(JavaSdkVersion.JDK_1_8)).map(_.getName)
      if (jdk8Names.isEmpty) {
        val title = "Scala 2.12 requires JDK 1.8 to compile"
        val content = s"<html><body><a href=''>Configure</a></body></html>"
        Notifications.Bus.notify(new Notification("scala", title, content, NotificationType.ERROR, new ConfigureJDKListener(project)))
      }
      if (!jdk8Names.contains(ScalaCompileServerSettings.getInstance().COMPILE_SERVER_SDK))
        jdk8Names.headOption
      else None
    } else None
  }

  object ConfigureLinkListener extends NotificationListener.Adapter {
    def hyperlinkActivated(notification: Notification, event: HyperlinkEvent) {
      CompileServerManager.showCompileServerSettingsDialog()
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

private case class ServerInstance(watcher: ProcessWatcher, port: Int, workingDir: File, bootClasspath: Set[(File, Long)]) {
  private var stopped = false

  def running: Boolean = !stopped && watcher.running

  def errors(): Seq[String] = watcher.errors()

  def destroyProcess() {
    stopped = true
    watcher.destroyProcess()
  }
}
