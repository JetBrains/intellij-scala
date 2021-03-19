package org.jetbrains.plugins.scala.compiler

import java.util
import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.plugins.scala.compiler.data.SbtData
import org.jetbrains.plugins.scala.externalHighlighters.ScalaHighlightingMode
import org.jetbrains.plugins.scala.util.JvmOptions

import scala.jdk.CollectionConverters._

/**
  * @author Nikolay.Tropin
  */
class ScalaBuildProcessParametersProvider(project: Project)
  extends BuildProcessParametersProvider {
  
  override def getVMArguments: util.List[String] = {
    customScalaCompilerInterfaceDir().toSeq ++
    parallelCompilationOptions() ++
    addOpens()
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
      JvmOptions.addOpens("java.base/java.util")
    else
      Seq.empty

  override def isProcessPreloadingEnabled: Boolean =
    !ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)
}
