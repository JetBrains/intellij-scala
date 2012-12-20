package org.jetbrains.plugins.scala
package compiler

import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.projectRoots.{Sdk, JavaSdkType}
import collection.JavaConverters._
import com.intellij.util.PathUtil
import java.io.{FileNotFoundException, File}
import com.intellij.openapi.application.ApplicationManager
import extensions._

/**
 * @author Pavel Fatin
 */
class CompileServerLauncher extends ApplicationComponent {
   private var instance: Option[ServerInstance] = None

   private val watcher = new ProcesWatcher()

   def initComponent() {}

   def disposeComponent() {
     if (running) stop()
     watcher.stop()
   }

   def init(sdk: Sdk) {
     if (!running) start(sdk)
   }

   private def start(sdk: Sdk) {
     val settings = ScalaApplicationSettings.getInstance

     val jvmParameters = {

       val xmx = settings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE |> { size =>
         if (size.isEmpty) Nil else List("-Xmx%sm".format(size))
       }

       xmx ++ settings.COMPILE_SERVER_JVM_PARAMETERS.split(" ").toSeq
     }

     val java = {
       val sdkType = sdk.getSdkType.asInstanceOf[JavaSdkType]

       sdkType.getVMExecutablePath(sdk)
     }

     val classpath = {
       val files = {
         val ideaRoot = (new File(PathUtil.getJarPathForClass(classOf[ApplicationManager]))).getParent
         val pluginRoot = (new File(PathUtil.getJarPathForClass(getClass))).getParent
         val jpsRoot = new File(pluginRoot, "jps")

         Seq(
           new File(ideaRoot, "jps-server.jar"),
           new File(ideaRoot, "trove4j.jar"),
           new File(ideaRoot, "util.jar"),
           new File(pluginRoot, "scala-library.jar"),
           new File(pluginRoot, "scala-plugin-runners.jar"),
           new File(pluginRoot, "nailgun.jar"),
           new File(jpsRoot, "sbt-interface.jar"),
           new File(jpsRoot, "incremental-compiler.jar"),
           new File(jpsRoot, "jline.jar"),
           new File(jpsRoot, "scala-jps-plugin.jar"))
       }

       files.foreach { file =>
         if (!file.exists) throw new FileNotFoundException(file.getCanonicalPath)
       }

       files.map(_.getCanonicalPath).mkString(File.pathSeparator)
     }

     val commands = java +: "-cp" +: classpath +: jvmParameters :+
             "org.jetbrains.plugins.scala.nailgun.NailgunRunner" :+ settings.COMPILE_SERVER_PORT

     val process = new ProcessBuilder(commands.asJava).redirectErrorStream(true).start()

     instance = Some(ServerInstance(process, settings.COMPILE_SERVER_PORT.toInt))

     watcher.watch(process)
   }

   // TODO stop server more gracefully
   def stop() {
     instance.foreach { it =>
       it.process.destroy()
     }
   }

   def running: Boolean = watcher.running

   def errors(): Seq[String] = watcher.errors()

   def port: Option[Int] = instance.map(_.port)

   def getComponentName = getClass.getSimpleName
 }

object CompileServerLauncher {
  def instance = ApplicationManager.getApplication.getComponent(classOf[CompileServerLauncher])
}

private case class ServerInstance(process: Process, port: Int)