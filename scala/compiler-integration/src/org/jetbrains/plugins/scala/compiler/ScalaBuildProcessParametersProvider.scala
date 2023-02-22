package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.plugins.scala.compiler.data.SbtData
import org.jetbrains.plugins.scala.settings.{ScalaCompileServerSettings, ScalaHighlightingMode}

import scala.jdk.CollectionConverters._

class ScalaBuildProcessParametersProvider(project: Project)
  extends BuildProcessParametersProvider {
  
  override def getVMArguments: java.util.List[String] = {
    customScalaCompilerInterfaceDir().toSeq ++
    parallelCompilationOptions() ++
    addOpens() ++
    java9rtParams() :+
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

  private def addOpens(): Seq[String] = CompileServerLauncher.compileServerJvmAddOpensExtraParams

  private def scalaCompileServerSystemDir(): String =
    s"-Dscala.compile.server.system.dir=${CompileServerLauncher.scalaCompileServerSystemDir}"

  private def java9rtParams(): Seq[String] = {
    val settings = ScalaCompileServerSettings.getInstance()
    if (settings.COMPILE_SERVER_ENABLED) Seq.empty
    else {
      val sdk = CompileServerJdkManager.getBuildProcessRuntimeJdk(project)._1
      toJdk(sdk).map(CompileServerLauncher.prepareJava9rtJar).getOrElse(Seq.empty)
    }
  }

  override def isProcessPreloadingEnabled: Boolean =
    !ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)
}
