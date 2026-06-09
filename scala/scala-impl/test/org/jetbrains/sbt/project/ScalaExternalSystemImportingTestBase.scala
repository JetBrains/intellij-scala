package org.jetbrains.sbt.project

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import junit.framework.TestCase.assertNotNull
import org.jetbrains.sbt.project.fixture.{IdeaProjectFixtureFactory, TestProjectJdkHolder, TestProjectRootFixture}

import java.nio.file.Path

abstract class ScalaExternalSystemImportingTestBase extends ExternalSystemImportingTestCase {

  import ScalaExternalSystemImportingTestBase._

  protected lazy val testProjectJdk: TestProjectJdkHolder =
    new TestProjectJdkHolder(projectJdkLanguageLevel)

  protected def getJdkConfiguredForTestCase: Sdk = testProjectJdk.configuredJdk

  protected def projectJdkLanguageLevel: LanguageLevel =
    TestProjectJdkHolder.defaultProjectJdkLanguageLevel(this)

  private lazy val testProjectRootFixture: TestProjectRootFixture =
    new TestProjectRootFixture(
      originalTestDataProjectDir = Path.of(getTestDataProjectPath),
      copyOptions = getTestProjectCopyOptions
    )

  private var isExternalSystemTestProjectRootSetUp: Boolean = false

  override protected def getTestsTempDir: String = "" // Use default temp directory

  override def setUp(): Unit = {
    super.setUp()
    setupBeforeProjectImport()
  }

  override def tearDown(): Unit = {
    try {
      // Remove the test JDK before heavy fixture SDK leak checks. Root disposable cleanup runs too late for that.
      testProjectJdk.tearDown()
    } finally {
      super.tearDown()
    }
  }

  /**
   * Runs after the test fixture and project root are created, but before child test setup can import the project.
   *
   * Subclasses overriding this hook must call `super.setupBeforeProjectImport()`.
   */
  protected def setupBeforeProjectImport(): Unit = {
    setupProjectJdk()
  }

  protected def setupProjectJdk(): Unit = {
    testProjectJdk.setUp()
  }

  /**
   * @return path to the test project in the test data directory.<br>
   *         Note, the actual runtime project directory can be changed if [[TestProjectCopyOptions.copyToTemporaryDir]] is set to true.<br>
   *         If it's set to false, then the test data directory will be used as a project root during test (which can lead to uncommited changes in the VCS)
   * @example `.../testdata/projectsForHighlightingTests/downloaded/scala3-example-project`
   */
  protected def getTestDataProjectPath: String

  // TODO: Make copyTestProjectToTemporaryDir true by default for all tests and run the tests on TC, see if anything fails
  protected def getTestProjectCopyOptions: TestProjectCopyOptions =
    TestProjectCopyOptions(
      copyToTemporaryDir = false,
      deleteTempDirectoryOnTestProcessShutDown = true
    )

  protected def getIdeaProjectFixtureOptions: IdeaProjectFixtureOptions =
    IdeaProjectFixtureOptions(
      useTestProjectAsIdeaProjectRoot = false
    )

  protected def getExternalSystemImportRootOptions: ExternalSystemImportRootOptions =
    ExternalSystemImportRootOptions(
      relativePath = None,
      setUpDuringSetUp = true
    )

  /**
   * Holds a path of the actual project that is used in the tests (not just test data).<br>
   *  - it points to [[getTestDataProjectPath]] when [[TestProjectCopyOptions.copyToTemporaryDir]] is `false`
   *  - it points to the project copy in a temporary directory when [[TestProjectCopyOptions.copyToTemporaryDir]] is `true`
   *
   * The actual project copying is done in [[TestProjectRootFixture.copyTestDataProjectToTempDirIfNeeded]].
   */
  protected final lazy val getTestProjectPath: Path = testProjectRootFixture.testProjectPath

  protected final lazy val getExternalSystemImportRoot: Path = {
    val options = getExternalSystemImportRootOptions
    options.relativePath.fold(getTestProjectPath)(getTestProjectPath.resolve)
  }

  override protected def setUpFixtures(): Unit = {
    if (getIdeaProjectFixtureOptions.useTestProjectAsIdeaProjectRoot)
      testProjectRootFixture.copyTestDataProjectToTempDirIfNeeded()

    val fixture = createProjectFixture()
    fixture.setUp()

    setMyTestFixture(fixture)
  }

  final protected def createProjectFixture(): IdeaProjectTestFixture = {
    IdeaProjectFixtureFactory.createProjectFixture(
      testName = getName,
      testProjectPath = getTestProjectPath,
      useTestProjectAsIdeaProjectRoot = getIdeaProjectFixtureOptions.useTestProjectAsIdeaProjectRoot
    )
  }

  /**
   * When false, the IDEA project fixture is opened in the default temporary location selected by the
   * IntelliJ test framework.
   *
   * When true, the IDEA project fixture is opened at [[getTestProjectPath]] itself. Use this for tests
   * whose logic depends on the IDEA project base path being equal to the runtime test project root.
   * Directory-based project format is enabled automatically in this mode.
   */
  final override protected def useDirectoryBasedStorageFormat(): Boolean =
    getIdeaProjectFixtureOptions.useTestProjectAsIdeaProjectRoot

  override protected def setUpInWriteAction(): Unit = {
    if (getExternalSystemImportRootOptions.setUpDuringSetUp)
      setUpExternalSystemTestProjectRoot()
  }

  /**
   * Some tests choose the test-data project dynamically inside the test method.
   * Those tests can delay root setup until the first import.
   */
  private def ensureExternalSystemTestProjectRootIsSetUp(): Unit = {
    if (!isExternalSystemTestProjectRootSetUp)
      setUpExternalSystemTestProjectRoot()
  }

  /**
   * Sets the external-system project root used by import tests.
   *
   * We can't override [[setUpProjectRoot]] because it's a final method.
   */
  protected def setUpExternalSystemTestProjectRoot(): Unit = {
    testProjectRootFixture.copyTestDataProjectToTempDirIfNeeded()
    setProjectRoot(getExternalSystemImportRoot)
  }

  final protected def setProjectRoot(projectRoot: Path): Unit = {
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectRoot)
    assertNotNull(s"Could not find a virtual file for: $projectRoot", virtualFile)
    setMyProjectRoot(virtualFile)
    isExternalSystemTestProjectRootSetUp = true
  }

  override protected def importProject(): Unit = {
    ensureExternalSystemTestProjectRootIsSetUp()

    ExternalSystemImportingTestCaseProxy.importProject(
      getMyProject,
      getExternalSystemId,
      getCurrentExternalProjectSettings,
      getProjectPath,
      createImportSpec(),
      handleImportFailure(_, _)
    )
  }
}

object ScalaExternalSystemImportingTestBase {

  /**
   * Controls the runtime location of the test project.
   *
   * @param copyToTemporaryDir
   * When true, the runtime project root is a temporary copy of [[ScalaExternalSystemImportingTestBase.getTestDataProjectPath]].
   * When false, the original test-data directory is used directly.
   * @param deleteTempDirectoryOnTestProcessShutDown
   * When true, the temporary parent directory is deleted by a JVM shutdown hook.
   */
  final case class TestProjectCopyOptions(
    copyToTemporaryDir: Boolean,
    deleteTempDirectoryOnTestProcessShutDown: Boolean,
  )

  /**
   * Controls where the IDEA project fixture itself is opened.
   *
   * @param useTestProjectAsIdeaProjectRoot
   * When true, the IDEA project is opened at the runtime test project root.
   * When false, the IDEA fixture is opened in the default temporary location selected by the IntelliJ test framework.
   */
  final case class IdeaProjectFixtureOptions(
    useTestProjectAsIdeaProjectRoot: Boolean
  )

  /**
   * Controls which directory is used as the external-system import root.
   *
   * @param relativePath
   * Optional path relative to the runtime test project root. When empty, the runtime test project root itself is used.
   * @param setUpDuringSetUp
   * When true, the external-system project root is set during test setup.
   * When false, root setup is delayed until the first project import.
   */
  final case class ExternalSystemImportRootOptions(
    relativePath: Option[String],
    setUpDuringSetUp: Boolean
  )
}
