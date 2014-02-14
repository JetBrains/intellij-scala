package org.jetbrains.plugins.scala
package compiler

import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable}
import collection.JavaConverters._
import com.intellij.util.PathUtil
import java.io.{IOException, File}
import com.intellij.openapi.application.ApplicationManager
import extensions._
import com.intellij.notification.{NotificationListener, Notifications, NotificationType, Notification}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.project.Project
import scala.util.control.Exception._
import javax.swing.event.HyperlinkEvent
import com.intellij.openapi.options.ShowSettingsUtil

/**
 * @author Pavel Fatin
 */
class CompileServerLauncher extends ApplicationComponent {
   private var instance: Option[ServerInstance] = None

   def initComponent() {}

   def disposeComponent() {
     if (running) stop()
   }

  def tryToStart(project: Project): Boolean = running || start(project)

  private def start(project: Project): Boolean = {
     val applicationSettings = ScalaApplicationSettings.getInstance

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
     import CompileServerLauncher.{compilerJars, jvmParameters}
     val settings = ScalaApplicationSettings.getInstance
     

     compilerJars.partition(_.exists) match {
       case (presentFiles, Seq()) =>
         val classpath = (jdk.tools +: presentFiles).map(_.canonicalPath).mkString(File.pathSeparator)

         val commands = jdk.executable.canonicalPath +: "-cp" +: classpath +: jvmParameters :+
                 "org.jetbrains.plugins.scala.nailgun.NailgunRunner" :+ settings.COMPILE_SERVER_PORT

         val builder = new ProcessBuilder(commands.asJava)

         catching(classOf[IOException]).either(builder.start())
                 .left.map(_.getMessage)
                 .right.map { process =>
           val watcher = new ProcessWatcher(process)
           instance = Some(ServerInstance(watcher, settings.COMPILE_SERVER_PORT.toInt))
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

  def jvmParameters = {
    val settings = ScalaApplicationSettings.getInstance
    val xmx = settings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE |> { size =>
      if (size.isEmpty) Nil else List("-Xmx%sm".format(size))
    }

    xmx ++ settings.COMPILE_SERVER_JVM_PARAMETERS.split(" ").toSeq
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
    ShowSettingsUtil.getInstance().showSettingsDialog(null, "Scala")
    notification.expire()
  }
}