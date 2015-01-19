package org.jetbrains.plugins.scala
package compiler

import java.io.{File, IOException}
import javax.swing.event.HyperlinkEvent

import com.intellij.notification.{Notification, NotificationListener, NotificationType, Notifications}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.PathUtil
import com.intellij.util.net.NetUtils
import org.jetbrains.plugins.scala.extensions._

import scala.collection.JavaConverters._
import scala.util.control.Exception._

/**
 * @author Pavel Fatin
 */
class CompileServerLauncher extends ApplicationComponent {
   private var instance: Option[ServerInstance] = None

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
          case e: Exception =>
        }
      }
      started
    }
    else true
  }

  private def start(project: Project): Boolean = {
     val applicationSettings = ScalaCompileServerSettings.getInstance

     if (applicationSettings.COMPILE_SERVER_SDK == null) {
       // Try to find a suitable JDK
       val choice = Option(ProjectRootManager.getInstance(project).getProjectSdk).orElse {
         val all = ProjectJdkTable.getInstance.getSdksOfType(JavaSdk.getInstance()).asScala
         all.headOption
       }

       choice.foreach(sdk => applicationSettings.COMPILE_SERVER_SDK = sdk.getName)

//       val message = "JVM SDK is automatically selected: " + name +
//               "\n(can be changed in Application Settings / Scala)"
//       Notifications.Bus.notify(new Notification("scala", "Scala compile server",
//         message, NotificationType.INFORMATION))
     }

    findJdkByName(applicationSettings.COMPILE_SERVER_SDK)
            .left.map(_ + "\nPlease either disable Scala compile server or configure a valid JVM SDK for it.")
            .right.flatMap(start) match {
      case Left(error) =>
        val title = "Cannot start Scala compile server"
        val content = s"<html><body>${error.replace("\n", "<br>")} <a href=''>Configure</a></body></html>"
        Notifications.Bus.notify(new Notification("scala", title, content, NotificationType.ERROR, ConfigureLinkListener))
        false
      case Right(_) =>
        ApplicationManager.getApplication invokeLater new Runnable {
          override def run() {
            CompileServerManager.instance(project).configureWidget()
          }
        }
        
        true
    }
  }

  private def start(jdk: JDK): Either[String, Process] = {
    import org.jetbrains.plugins.scala.compiler.CompileServerLauncher.{compilerJars, jvmParameters}

    compilerJars.partition(_.exists) match {
      case (presentFiles, Seq()) =>
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

        val commands = jdk.executable.canonicalPath +: "-cp" +: classpath +: jvmParameters ++:
                ngRunnerFqn +: freePort.toString +: id.toString +: Nil

        val builder = new ProcessBuilder(commands.asJava)

        catching(classOf[IOException]).either(builder.start())
                .left.map(_.getMessage)
                .right.map { process =>
          val watcher = new ProcessWatcher(process)
          instance = Some(ServerInstance(watcher, freePort))
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
    instance.foreach { it =>
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

  def running: Boolean = instance.exists(_.running)

  def errors(): Seq[String] = instance.map(_.errors()).getOrElse(Seq.empty)

  def port: Option[Int] = instance.map(_.port)

  def getComponentName = getClass.getSimpleName
}

object CompileServerLauncher {
  def instance = ApplicationManager.getApplication.getComponent(classOf[CompileServerLauncher])
  
  def compilerJars = {
    val ideaRoot = new File(PathUtil.getJarPathForClass(classOf[ApplicationManager])).getParent
    val pluginRoot = new File(PathUtil.getJarPathForClass(getClass)).getParent
    val jpsRoot = new File(pluginRoot, "jps")

    Seq(
      new File(ideaRoot, "jps-server.jar"),
      new File(ideaRoot, "trove4j.jar"),
      new File(ideaRoot, "util.jar"),
      new File(pluginRoot, "scala-library.jar"),
      new File(pluginRoot, "scala-nailgun-runner.jar"),
      new File(pluginRoot, "compiler-settings.jar"),
      new File(jpsRoot, "nailgun.jar"),
      new File(jpsRoot, "sbt-interface.jar"),
      new File(jpsRoot, "incremental-compiler.jar"),
      new File(jpsRoot, "jline.jar"),
      new File(jpsRoot, "scala-jps-plugin.jar"))
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
    if (!launcher.running) CompileServerLauncher.instance tryToStart project
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
}

private case class ServerInstance(watcher: ProcessWatcher, port: Int) {
  def running: Boolean = watcher.running

  def errors(): Seq[String] = watcher.errors()

  def destroyProcess() {
    watcher.destroyProcess()
  }
}

private object ConfigureLinkListener extends NotificationListener.Adapter {
  def hyperlinkActivated(notification: Notification, event: HyperlinkEvent) {
    CompileServerManager.showCompileServerSettingsDialog()
    notification.expire()
  }
}