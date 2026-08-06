package org.jetbrains.sbt.project

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase.IdeaProjectFixtureOptions

/**
 * Runs the standard project-structure importing suite through sbt shell import.
 *
 * Subclasses choose the old or new shell implementation by overriding [[useNewShell]].
 */
abstract class SbtShellProjectStructureImportingTestBase extends SbtProjectStructureImportingSuiteBase {

  override protected def getTestSbtProjectSettings =
    super.getTestSbtProjectSettings.copy(useSbtShellForImport = true)

  override protected def getIdeaProjectFixtureOptions: IdeaProjectFixtureOptions =
    super.getIdeaProjectFixtureOptions.copy(useTestProjectAsIdeaProjectRoot = true)

  override def setUp(): Unit = {
    super.setUp()

    val newShellRegistry = Registry.get("sbt.new.shell")
    newShellRegistry.setValue(useNewShell, getTestRootDisposable)
  }

  protected def useNewShell: Boolean = false
}
