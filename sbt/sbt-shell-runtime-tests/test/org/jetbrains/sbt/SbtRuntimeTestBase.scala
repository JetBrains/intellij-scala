package org.jetbrains.sbt

import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase.{IdeaProjectFixtureOptions, TestProjectCopyOptions}
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike

/**
 * Runtime-oriented sbt import test base built on [[SbtExternalSystemImportingTestLike]].
 *
 * This base adds the runtime-test setup flow on top of the sbt external-system import harness:
 *  - imports the project during `setUp`, so concrete tests start from an already imported project
 *  - resolves test projects from relative paths via [[SbtTestDataUtils]]
 *  - always copies the test project to a temporary directory
 *  - opens the IDEA project fixture under the copied test project directory
 *
 * Subclasses supply the remaining runtime-test workflow:
 *  - relative test-data project path through [[getRelativeTestProjectPath]]
 *  - optional sbt project settings through [[getTestSbtProjectSettings]]
 *  - optional setup before import through [[setupBeforeProjectImport]], with `super.setupBeforeProjectImport()` called first
 *  - runtime/process assertions after the project is imported
 */
abstract class SbtRuntimeTestBase extends SbtExternalSystemImportingTestLike {

  protected def importProjectDuringTestSetUp: Boolean = true

  override def setUp(): Unit = {
    super.setUp()

    // TODO: shouldn't we move it to the base test classes?
    if (importProjectDuringTestSetUp) {
      importProject()
    }
  }

  override protected def getTestProjectCopyOptions: TestProjectCopyOptions =
    super.getTestProjectCopyOptions.copy(copyToTemporaryDir = true)

  override protected def getIdeaProjectFixtureOptions: IdeaProjectFixtureOptions =
    super.getIdeaProjectFixtureOptions.copy(useTestProjectAsIdeaProjectRoot = true)

  protected def getRelativeTestProjectPath: String

  final override protected def getTestDataProjectPath: String =
    SbtTestDataUtils.resolveRelativePath(getRelativeTestProjectPath)

  /**
   * Runtime tests exercise project-level services, shell/process state, and run configurations.
   * Keep the opened IDEA project fixture at the copied sbt project directory, instead of the
   * default temporary fixture directory used by the generic external-system test case.
   */
}
