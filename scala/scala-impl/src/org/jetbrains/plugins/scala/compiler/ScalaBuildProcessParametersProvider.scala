package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.plugins.scala.compiler.data.SbtData
import org.jetbrains.plugins.scala.externalHighlighters.ScalaHighlightingMode

import scala.jdk.CollectionConverters._

class ScalaBuildProcessParametersProvider(project: Project)
  extends BuildProcessParametersProvider {
  
  override def getVMArguments: java.util.List[String] = {
    customScalaCompilerInterfaceDir().toSeq ++
    parallelCompilationOptions() ++
    addOpens() :+
    scalaCompileServerSystemDir() :+
    // this is the only way to propagate registry values to the JPS process
    s"-Dscala.compile.server.socket.connect.timeout.milliseconds=${Registry.intValue("scala.compile.server.socket.connect.timeout.milliseconds")}"
  }.asJava

  private def customScalaCompilerInterfaceDir(): Option[String] = {
    val key = SbtData.compilerInterfacesKey
    val custom = Option(System.getProperty(key))
    custom.map(path => s"-D$key=$path")
  }
  
  private def parallelCompilationOptions(): Seq[String] = {
    val settings = ScalaCompileServerSettings.getInstance
    if (settings.COMPILE_SERVER_ENABLED)
      Seq(
        GlobalOptions.COMPILE_PARALLEL_MAX_THREADS_OPTION -> settings.COMPILE_SERVER_PARALLELISM,
        GlobalOptions.COMPILE_PARALLEL_OPTION -> settings.COMPILE_SERVER_PARALLEL_COMPILATION,
      ).map { case (key, value) =>
        s"-D$key=$value"
      }
    else
      Seq.empty
  }

  private def addOpens(): Seq[String] =
    if (CompileServerJdkManager.getBuildProcessJdkVersion(project).isAtLeast(JavaSdkVersion.JDK_1_9))
      createJvmAddOpensParams("java.base/java.util")
    else
      Seq.empty

  private def scalaCompileServerSystemDir(): String =
    s"-Dscala.compile.server.system.dir=${CompileServerLauncher.scalaCompileServerSystemDir}"

  override def isProcessPreloadingEnabled: Boolean =
    !ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)
}
