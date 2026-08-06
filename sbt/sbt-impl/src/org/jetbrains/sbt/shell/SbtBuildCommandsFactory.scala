package org.jetbrains.sbt.shell

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.TestOnly
import org.jetbrains.sbt.SbtSourceSetUtil.SbtSourceSetModuleExt
import org.jetbrains.sbt.project.data.SbtModuleData
import org.jetbrains.sbt.{SbtUtil, SbtVersion, SbtVersionCapabilities}

/**
 * Constructs a list of sbt commands to build a list of given IntelliJ modules
 */
private[shell] object SbtBuildCommandsFactory {
  private val log = Logger.getInstance(getClass)

  /** Represents the sbt source set scope (main or test) for which a build command should be generated. */
  @TestOnly
  enum SbtScope:
    case Main
    case Test

  @TestOnly
  final case class ModuleWithScopes(
    moduleData: SbtModuleData,
    scopes: Set[SbtScope],
  )

  /**
   * Builds the list of sbt shell commands for the given modules and their scopes.
   *
   * @todo sensible way to find out what scopes to run it for besides compile and test?
   * @todo make tasks should be user-configurable
   */
  def createBuildCommands(
    sbtVersion: SbtVersion,
    taskModules: Seq[Module]
  ): Seq[String] = {
    val scopesPerModule = collectScopesPerModule(taskModules)
    createBuildCommandsInner(sbtVersion, scopesPerModule)
  }

  /**
   * Collect the sbt scopes (main/test) per sbt module.<br>
   * This is done to:
   *  1. Avoid duplicate commands:<br>
   *     Triggering "Build project" for a project with a single sbt module results in 3 ProjectTasks:
   *     1. for the parent module
   *     2. for the main module
   *     3. for the test module
   *
   * For our use case, only 2 "products" commands are needed:
   * 1. one for Compile scope in the given sbt module
   * 2. one for Test scope in the given sbt module
   *
   * The logic in this method ensures duplicates are filtered out.
   *  2. Run the "products" task only in the relevant scope:<br>
   *     When a build is triggered for a main or test module, only the "products" task in the Compile or Test scope (respectively) should be executed.
   */
  private def collectScopesPerModule(modules: Seq[Module]): Seq[ModuleWithScopes] =
    modules.flatMap { module =>
      val moduleData = SbtUtil.getSbtModuleData(module)
      moduleData.map(data => ModuleWithScopes(data, scopesForModule(module)))
    }

  private def deduplicateScopes(moduleScopes: Seq[ModuleWithScopes]): Seq[ModuleWithScopes] =
    moduleScopes.foldLeft(Map.empty[SbtModuleData, Set[SbtScope]]) { case (acc, moduleWithScopes) =>
      val mergedScopes = acc.getOrElse(moduleWithScopes._1, Set.empty) ++ moduleWithScopes._2
      acc.updated(moduleWithScopes._1, mergedScopes)
    }.toSeq.map(ModuleWithScopes.apply)

  private def scopesForModule(module: Module): Set[SbtScope] =
    if !module.isSbtSourceSetModule then
      Set(SbtScope.Main, SbtScope.Test)
    else if module.isMain then
      Set(SbtScope.Main)
    else
      Set(SbtScope.Test)

  /**
   * Exposing this method to tests to simpify the unit tests and decouple from the External System API to extract info about the modules
   */
  @TestOnly
  def createBuildCommandsInner(
    sbtVersion: SbtVersion,
    scopesPerModule: Seq[ModuleWithScopes],
  ): Seq[String] = {
    val scopesPerModuleDeduplicated = deduplicateScopes(scopesPerModule)
    createBuildCommandsInnerImpl(sbtVersion, scopesPerModuleDeduplicated)
  }

  private def createBuildCommandsInnerImpl(
    sbtVersion: SbtVersion,
    scopesPerModule: Seq[ModuleWithScopes],
  ): Seq[String] = {
    // Sort commands Keep command ordering deterministic for reproducibility, primarily in tests.
    val projectIdWithSortedScopes: Seq[(String, Seq[SbtScope])] = {
      scopesPerModule
        .map { case ModuleWithScopes(moduleData, scopes) =>
          val projectScope = SbtUtil.makeSbtProjectId(moduleData)
          val sortedScopes = sortScopes(scopes.toSeq)
          (projectScope, sortedScopes)
        }
        .sortBy(_._1)
    }

    projectIdWithSortedScopes.flatMap { case (projectScope, scopes) =>
      scopes.map(scope => commandForScope(sbtVersion, projectScope, scope))
    }
  }

  private def sortScopes(scopes: Seq[SbtScope]): Seq[SbtScope] =
    scopes.sortBy {
      case SbtScope.Main => 0
      case SbtScope.Test => 1
    }

  private def commandForScope(
    sbtVersion: SbtVersion,
    projectScope: String,
    scope: SbtScope
  ): String =
    scope match {
      case SbtScope.Main =>
        s"$projectScope/products"
      case SbtScope.Test =>
        if SbtVersionCapabilities.isSlashSyntaxSupported(sbtVersion) then
          s"$projectScope/Test/products"
        else
          s"$projectScope/test:products"
    }
}
