package org.jetbrains.sbt.project

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.sbt.actions.SbtDirectoryCompletionContributor
import org.jetbrains.sbt.project.ProjectStructureMatcher.ProjectComparisonOptions
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtSettings

import scala.jdk.CollectionConverters.CollectionHasAsScala


abstract class SbtProjectStructureImportingLike extends SbtExternalSystemImportingTestLike
  with ProjectStructureMatcher
  with ExactMatch {

  import ProjectStructureDsl._
  override protected def getTestProjectPath: String =
    generateTestProjectPath(getTestName(true))

  override def setUp(): Unit = {
    super.setUp()
    SbtProjectResolver.processOutputOfLatestStructureDump = ""
  }

  protected def runTest(expected: project, singleContentRootModules: Boolean = true): Unit = {
    importProject(false)

    assertProjectsEqual(expected, myProject, singleContentRootModules)(ProjectComparisonOptions.Implicit.default)
    assertNoNotificationsShown(myProject)
  }

  /**
   * It is necessary to explicitly set all project settings that are tested/required for test, because what is set in
   * #setUp method in each SbtProjectStructureImportingTest classes is not applied to the project settings of the linked project
   */
  protected def linkSbtProject(path: String, transitiveProjectDependencies: Boolean, prodTestSourcesSeparated: Boolean): Unit = {
    val settings = new SbtProjectSettings
    settings.jdk = getJdkConfiguredForTestCase.getName
    settings.setExternalProjectPath(path)
    settings.setInsertProjectTransitiveDependencies(transitiveProjectDependencies)
    settings.setSeparateProdAndTestSources(prodTestSourcesSeparated)
    SbtSettings.getInstance(myProject).linkProject(settings)
  }

  protected def generateTestProjectPath(projectName: String): String =
    s"${TestUtils.getTestDataPath}/sbt/projects/$projectName"

  protected case class ExpectedDirectoryCompletionVariant(
    projectRelativePath: String,
    rootType: JpsModuleSourceRootType[_]
  )
   object ExpectedDirectoryCompletionVariant {
     implicit val expectedDirectoryCompletionVariantOrdering: Ordering[ExpectedDirectoryCompletionVariant] =
       (x: ExpectedDirectoryCompletionVariant, y: ExpectedDirectoryCompletionVariant) => {
         x.projectRelativePath compare y.projectRelativePath
       }
   }

  protected val DefaultSbtContentRootsScala212: Seq[ExpectedDirectoryCompletionVariant] =
    defaultSbtContentRootsScala2(12)

  protected val DefaultSbtContentRootsScala213: Seq[ExpectedDirectoryCompletionVariant] =
    defaultSbtContentRootsScala2(13)

  private def defaultSbtContentRootsScala2: Integer => Seq[ExpectedDirectoryCompletionVariant] = (minorVersion: Integer) => Seq(
    ("src/main/java", JavaSourceRootType.SOURCE),
    ("src/main/scala", JavaSourceRootType.SOURCE),
    ("src/main/scala-2", JavaSourceRootType.SOURCE),
    (s"src/main/scala-2.$minorVersion", JavaSourceRootType.SOURCE),
    ("src/test/java", JavaSourceRootType.TEST_SOURCE),
    ("src/test/scala", JavaSourceRootType.TEST_SOURCE),
    ("src/test/scala-2", JavaSourceRootType.TEST_SOURCE),
    (s"src/test/scala-2.$minorVersion", JavaSourceRootType.TEST_SOURCE),
    ("src/main/resources", JavaResourceRootType.RESOURCE),
    ("src/test/resources", JavaResourceRootType.TEST_RESOURCE),
  ).map((ExpectedDirectoryCompletionVariant.apply _).tupled)

  protected val DefaultMainSbtContentRootsScala213: Seq[ExpectedDirectoryCompletionVariant] =
    defaultMainSbtContentRootsScala2(13)

  protected val DefaultTestSbtContentRootsScala213: Seq[ExpectedDirectoryCompletionVariant] =
    defaultTestSbtContentRootsScala2(13)

  protected val DefaultMainSbtContentRootsScala212: Seq[ExpectedDirectoryCompletionVariant] =
    defaultMainSbtContentRootsScala2(12)

  protected val DefaultTestSbtContentRootsScala212: Seq[ExpectedDirectoryCompletionVariant] =
    defaultTestSbtContentRootsScala2(12)


  private def defaultMainSbtContentRootsScala2: Integer => Seq[ExpectedDirectoryCompletionVariant] = (minorVersion: Integer) => Seq(
    ("java", JavaSourceRootType.SOURCE),
    ("scala", JavaSourceRootType.SOURCE),
    ("scala-2", JavaSourceRootType.SOURCE),
    (s"scala-2.$minorVersion", JavaSourceRootType.SOURCE),
    ("resources", JavaResourceRootType.RESOURCE),
  ).map((ExpectedDirectoryCompletionVariant.apply _).tupled)

  private def defaultTestSbtContentRootsScala2: Integer => Seq[ExpectedDirectoryCompletionVariant] = (minorVersion: Integer) => Seq(
    ("java", JavaSourceRootType.TEST_SOURCE),
    ("scala", JavaSourceRootType.TEST_SOURCE),
    ("scala-2", JavaSourceRootType.TEST_SOURCE),
    (s"scala-2.$minorVersion", JavaSourceRootType.TEST_SOURCE),
    ("resources", JavaResourceRootType.TEST_RESOURCE),
  ).map((ExpectedDirectoryCompletionVariant.apply _).tupled)

  protected val DefaultSbtContentRootsScala3: Seq[ExpectedDirectoryCompletionVariant] = Seq(
    ("src/main/java", JavaSourceRootType.SOURCE),
    ("src/main/scala", JavaSourceRootType.SOURCE),
    ("src/main/scala-3", JavaSourceRootType.SOURCE),
    ("src/test/java", JavaSourceRootType.TEST_SOURCE),
    ("src/test/scala", JavaSourceRootType.TEST_SOURCE),
    ("src/test/scala-3", JavaSourceRootType.TEST_SOURCE),
    ("src/main/resources", JavaResourceRootType.RESOURCE),
    ("src/test/resources", JavaResourceRootType.TEST_RESOURCE),
  ).map((ExpectedDirectoryCompletionVariant.apply _).tupled)

  protected val DefaultMainSbtContentRootsScala3: Seq[ExpectedDirectoryCompletionVariant] = Seq(
    ("java", JavaSourceRootType.SOURCE),
    ("scala", JavaSourceRootType.SOURCE),
    ("scala-3", JavaSourceRootType.SOURCE),
    ("resources", JavaResourceRootType.RESOURCE),
  ).map((ExpectedDirectoryCompletionVariant.apply _).tupled)

  protected val DefaultTestSbtContentRootsScala3: Seq[ExpectedDirectoryCompletionVariant] = Seq(
    ("java", JavaSourceRootType.TEST_SOURCE),
    ("scala", JavaSourceRootType.TEST_SOURCE),
    ("scala-3", JavaSourceRootType.TEST_SOURCE),
    ("resources", JavaResourceRootType.TEST_RESOURCE),
  ).map((ExpectedDirectoryCompletionVariant.apply _).tupled)

  //NOTE: it doesn't test final ordering on UI, see IDEA-306694
  protected def assertSbtDirectoryCompletionContributorVariants(
    directory: VirtualFile,
    expectedVariants: Seq[ExpectedDirectoryCompletionVariant]
  ): Unit = {
    val psiDirectory = PsiManager.getInstance(myProject).findDirectory(directory)
    val directoryPath = directory.getPath

    val variants = new SbtDirectoryCompletionContributor().getVariants(psiDirectory).asScala.toSeq
    val actualVariants = variants.map(v => ExpectedDirectoryCompletionVariant(
      v.getPath.stripPrefix(directoryPath).stripPrefix("/"),
      v.getRootType
    ))

    assertCollectionEquals(
      "Wrong directory completion contributor variants",
      expectedVariants.sorted,
      actualVariants.sorted
    )
  }

}
