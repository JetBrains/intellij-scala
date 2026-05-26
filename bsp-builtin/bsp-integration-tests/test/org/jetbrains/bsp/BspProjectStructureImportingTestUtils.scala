package org.jetbrains.bsp

import org.jetbrains.sbt.project.ProjectStructureDsl.{library, scalaSdkSettings}

object BspProjectStructureImportingTestUtils {

  /**
   * In BSP projects, libraries that are available for the entire module (except for the ones that are customized per scope and Scala SDKs)
   * are duplicated in the `Project Structure` - once with the `Compile` scope and once with the `Test` scope.
   *
   * This utility creates the expected libraries for a module, including duplicated test-scope libraries and
   * the synthetic `"BSP: <moduleName> test dependencies"` library, which is attached to every module.
   *
   * @param libraries libraries without per test-scope duplications
   * @param moduleName name of the module for which the expected libraries are generated
   * @todo to make the tests more exhaustive, not only library names but also scopes could be tested
   */
  def expectedLibraryDependencies(libraries: Seq[library], moduleName: String): Seq[library] = {
    val allLibrariesWithoutScalaSDK = libraries.filter(_.get(scalaSdkSettings).flatten.isEmpty)
    val testLibraries = allLibrariesWithoutScalaSDK :+ new library(s"BSP: $moduleName test dependencies")
    libraries ++ testLibraries
  }
}
