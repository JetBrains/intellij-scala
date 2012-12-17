package org.jetbrains.plugins.scala
package compiler

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.projectRoots.JavaSdkType
import collection.JavaConverters._
import com.intellij.util.PathUtil
import java.io.File
import com.intellij.openapi.application.ApplicationManager
import extensions._

/**
 * @author Pavel Fatin
 */
class CompileServerLauncher(project: Project) extends ProjectComponent {
   private var instance: Option[Process] = None

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

     val jvmParameters = {

       val xmx = settings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE |> { size =>
         if (size.isEmpty) Nil else List("-Xmx%sm".format(size))
       }

       xmx ++ settings.COMPILE_SERVER_JVM_PARAMETERS.split(" ").toSeq
     }

     val java = {
       val sdk = Option(ProjectRootManager.getInstance(project).getProjectSdk)
               .getOrElse(throw new RuntimeException("No project SDK specified"))

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
           new File(pluginRoot, "nailgun.jar"),
           new File(jpsRoot, "sbt-interface.jar"),
           new File(jpsRoot, "incremental-compiler.jar"),
           new File(jpsRoot, "jline.jar"),
           new File(jpsRoot, "scala-jps-plugin.jar"))
       }

       files.map(_.getCanonicalPath).mkString(File.pathSeparator)
     }

     val commands = java +: "-cp" +: classpath +: jvmParameters :+
             "com.martiansoftware.nailgun.NGServer" :+ settings.COMPILE_SERVER_PORT

     val process = new ProcessBuilder(commands.asJava).redirectErrorStream(true).start()

     instance = Some(process)

     watcher.watch(process)
   }

   // TODO stop server more gracefully
   def stop() {
     instance.foreach { it =>
       it.destroy()
     }
   }

   def running: Boolean = watcher.running

   def getComponentName = getClass.getSimpleName
 }