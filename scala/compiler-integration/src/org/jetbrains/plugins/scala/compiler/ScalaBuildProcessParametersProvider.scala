package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.plugins.scala.compiler.data.SbtData
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings.{ScalaCompileServerSettings, ScalaHighlightingMode}
import org.jetbrains.sbt.project.settings.DisplayModuleName

import java.util.Collections
import scala.jdk.CollectionConverters._

class ScalaBuildProcessParametersProvider(project: Project)
  extends BuildProcessParametersProvider {
  
  override def getVMArguments: java.util.List[String] =
    if (project.hasScala) {
      (
        customScalaCompilerInterfaceDir().toSeq ++
          parallelCompilationOptions() ++
          addOpens() ++
          java9rtParams() :+
          useDisplayModuleNames() :+
          scalaCompileServerSystemDir() :+
          // this is the only way to propagate registry values to the JPS process
          s"-Dscala.compile.server.socket.connect.timeout.milliseconds=${Registry.intValue("scala.compile.server.socket.connect.timeout.milliseconds")}"
      ).asJava
    } else Collections.emptyList()

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

  private def useDisplayModuleNames(): String = {
    val modules = ModuleManager.getInstance(project).getModules
    val areUnique = areDisplayModuleNamesUnique(modules)
    s"-Duse.module.display.name=$areUnique"
  }

  /**
   * Checks whether display module names for all modules are unique.
   * If yes, then display module names will be used in compilation charts.
   * Otherwise, full module names will be used for all modules.
   *
   * The case in which display names may be duplicated is when there is a multi build sbt project,
   * or there are many separate sbt projects imported in IDEA.
   * Then it may happen that in two builds there will be sbt projects with the same names, and
   * these names are used as display names. Therefore, duplication occurs. <br>
   * This problem is not present when creating modules, as then we add the root project prefix, which is unique.
   */
  private def areDisplayModuleNamesUnique(modules: Array[Module]): Boolean = {
    val displayModuleNames = modules.map(DisplayModuleName.getInstance(_).name)
    val containsNull = displayModuleNames.contains(null)
    val isUnique = displayModuleNames.toSet.size == displayModuleNames.length
    !containsNull && isUnique
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
    if (project.hasScala)
      !ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)
    else
      super.isProcessPreloadingEnabled
}
