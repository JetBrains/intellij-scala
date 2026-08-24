package org.jetbrains.sbt.project

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike.TestSbtProjectSettings
import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase.IdeaProjectFixtureOptions

abstract class SbtProjectStructureImportingTestCase
  extends SbtProjectStructureImportingTestBase
    with ImportingTestCase:

  override protected def getTestSbtProjectSettings: TestSbtProjectSettings =
    super.getTestSbtProjectSettings.copy(useSbtShellForImport = importMode.usesSbtShell)

  override protected def getIdeaProjectFixtureOptions: IdeaProjectFixtureOptions =
    super.getIdeaProjectFixtureOptions.copy(useTestProjectAsIdeaProjectRoot = importMode.usesSbtShell)

  override def setUp(): Unit =
    super.setUp()

    if importMode.usesSbtShell then
      val newShellRegistry = Registry.get("sbt.new.shell")
      val useNewShell = importMode == ImportMode.NewShell
      newShellRegistry.setValue(useNewShell, getTestRootDisposable)
  end setUp

  override protected def getTestDataProjectPath: String =
    val testName = getTestName(true).replaceAll("_sbt_.*$", "")
    generateTestProjectPath(testName)
