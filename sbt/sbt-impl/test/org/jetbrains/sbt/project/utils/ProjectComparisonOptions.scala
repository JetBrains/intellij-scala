package org.jetbrains.sbt.project.utils

/**
 * @param strictCheckForBuildModules if `false` then if expected project structure doesn't contain `-build` modules it will not be considered as a test failure<br>
 *                                   if `true` then all the modules will be checked
 * @note there is also [[org.jetbrains.sbt.DslUtils.MatchType]]
 */
case class ProjectComparisonOptions(
  strictCheckForBuildModules: Boolean,
  scalaCliStructureHelper: Option[ScalaCliStructureHelper],
  checkLibraryDependenciesOrder: Boolean
)

object ProjectComparisonOptions {
  val Default: ProjectComparisonOptions =
    ProjectComparisonOptions(
      strictCheckForBuildModules = false,
      scalaCliStructureHelper = None,
      checkLibraryDependenciesOrder = false
    )

  def apply(strictCheckForBuildModules: Boolean): ProjectComparisonOptions =
    ProjectComparisonOptions(strictCheckForBuildModules, scalaCliStructureHelper = None, checkLibraryDependenciesOrder = false)

  def apply(scalaCliProjectName: String): ProjectComparisonOptions =
    ProjectComparisonOptions(
      strictCheckForBuildModules = false,
      scalaCliStructureHelper = Some(ScalaCliStructureHelper(scalaCliProjectName)),
      checkLibraryDependenciesOrder = false
    )
}