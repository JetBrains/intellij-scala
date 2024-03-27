package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.{LanguageLevelProjectExtension, ProjectRootManager}
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.{IdeaTestUtil, UsefulTestCase}
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.external
import org.jetbrains.plugins.scala.project.external.{JdkByName, SdkReference, SdkUtils}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.sbt.project.data._
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.project.sources.SharedSourcesModuleType
import org.jetbrains.sbt.settings.SbtSettings
import org.junit.Assert._

import java.io.File
import java.net.URI

class SbtProjectDataServiceTest extends ProjectDataServiceTestCase {

  import ExternalSystemDataDsl._

  override def setUp(): Unit = {
    super.setUp()
    setUpJdks()
  }

  override def tearDown(): Unit = {
    tearDownJdks()
    super.tearDown()
  }

  def testEmptyBasePackage(): Unit =
    doTestBasePackages(Seq.empty)

  def testNonEmptyBasePackage(): Unit =
    doTestBasePackages(Seq("com.test.base"))

  def testValidJavaSdk(): Unit =
    doTestSdk(
      Option(JdkByName("1.8")),
      ProjectJdkTable.getInstance().findJdk(IdeaTestUtil.getMockJdkName(LanguageLevel.JDK_1_8.toJavaVersion)),
      LanguageLevel.JDK_1_8,
      expectedProjectLanguageLevelIsDefault = true
    )

  def testInvalidSdk_ShouldFallbackToMostRecentJdk(): Unit =
    doTestSdk(Some(external.JdkByName("20")), mostRecentJdk, mostRecentJdkLanguageLevel, expectedProjectLanguageLevelIsDefault = true)

  def testAbsentSdk_ShouldFallbackToMostRecentJdk(): Unit =
    doTestSdk(None, mostRecentJdk, mostRecentJdkLanguageLevel, expectedProjectLanguageLevelIsDefault = true)

  // javacOptions now don't effect IDEA project settings, they are imported into IDEA modules
  // (including module which represents sbt root project)
  // javacOptions in sbt root projects are tested in org.jetbrains.sbt.project.ProjectImportingTest
  // def testValidJavaSdkWithDifferentLanguageLevel(): Unit = ???
  // def testJavacOptionsInSbtRootProjectShouldNotBeAppliedToIDEAProjectSettings(): Unit = ???

  def testSbtVersion(): Unit = {
    val projectSettings = SbtProjectSettings.default
    projectSettings.setExternalProjectPath(ExternalSystemApiUtil.toCanonicalPath(getProject.getBasePath))
    SbtSettings.getInstance(getProject).linkProject(projectSettings)

    val expectedVersion = "0.13.8"
    importProjectData(generateProject(Seq.empty, None, expectedVersion))
    val actualVersion = SbtSettings.getInstance(getProject).getLinkedProjectSettings(getProject.getBasePath).sbtVersion
    assertEquals(expectedVersion, actualVersion)
  }

  def testIncrementalityTypeForSharedModules(): Unit = {
    val testProject = new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath

      modules += new module {
        val uri: URI = new File(getProject.getBasePath).toURI
        val moduleName = "Module 1"
        override val typeId: String = SharedSourcesModuleType.instance.getId
        projectId := ModuleNode.combinedId(moduleName, Option(uri))
        projectURI := uri
        name := moduleName
        moduleFileDirectoryPath := getProject.getBasePath + "/module1"
        externalConfigPath := getProject.getBasePath + "/module1"
      }

      arbitraryNodes += new SbtProjectNode(SbtProjectData(None, "", getProject.getBasePath, projectTransitiveDependenciesUsed = false, prodTestSourcesSeparated = false))
    }.build.toDataNode

    importProjectData(testProject)
    assertEquals(IncrementalityType.SBT, ScalaCompilerConfiguration.instanceIn(getProject).incrementalityType)
  }

  private def setUpJdks(): Unit = inWriteAction {
    val projectJdkTable = ProjectJdkTable.getInstance()
    projectJdkTable.getAllJdks.foreach(projectJdkTable.removeJdk)
    projectJdkTable.addJdk(IdeaTestUtil.getMockJdk17)
    projectJdkTable.addJdk(IdeaTestUtil.getMockJdk18)
    // TODO: find a way to create mock Android SDK
  }

  private def tearDownJdks(): Unit = inWriteAction {
    val projectJdkTable = ProjectJdkTable.getInstance()
    projectJdkTable.getAllJdks.foreach(projectJdkTable.removeJdk)
  }

  private def generateProject(
    basePackages: Seq[String],
    jdk: Option[SdkReference],
    sbtVersion: String
  ): DataNode[ProjectData] =
    new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath

      modules += new module {
        val uri: URI = new File(getProject.getBasePath).toURI
        val moduleName = "Module 1"
        override val typeId: String = JavaModuleType.getModuleType.getId
        projectId := ModuleNode.combinedId(moduleName, Option(uri))
        projectURI := uri
        name := moduleName
        projectId := ModuleNode.combinedId(moduleName, Option(uri))
        moduleFileDirectoryPath := getProject.getBasePath + "/module1"
        externalConfigPath := getProject.getBasePath + "/module1"
        arbitraryNodes ++= Seq(
          new ModuleExtNode(SbtModuleExtData(None, basePackage = basePackages.headOption)),
          new ScalaSdkNode(SbtScalaSdkData(None))
        )
      }

      arbitraryNodes += new SbtProjectNode(SbtProjectData(jdk, sbtVersion, getProject.getBasePath, projectTransitiveDependenciesUsed = false, prodTestSourcesSeparated = false))
    }.build.toDataNode

  private def doTestBasePackages(basePackages: Seq[String]): Unit = {
    importProjectData(generateProject(basePackages, None, ""))
    UsefulTestCase.assertContainsElements(ScalaProjectSettings.getInstance(getProject).getCustomBasePackages.values(), basePackages:_*)
  }

  private def mostRecentJdk: Sdk =
    ProjectJdkTable.getInstance().findMostRecentSdkOfType(JavaSdk.getInstance())

  private def mostRecentJdkLanguageLevel: LanguageLevel =
    LanguageLevel.parse(mostRecentJdk.getVersionString).ensuring(_ != null)

  private def doTestSdk(
    projectSdk: Option[SdkReference],
    expectedProjectSdk: Sdk,
    expectedProjectLanguageLevel: LanguageLevel,
    expectedProjectLanguageLevelIsDefault: Boolean
  ): Unit = {
    importProjectData(generateProject(Seq.empty, projectSdk, ""))

    assertEquals(expectedProjectSdk, ProjectRootManager.getInstance(getProject).getProjectSdk)
    val languageLevelProjectExtension = LanguageLevelProjectExtension.getInstance(getProject)

    assertEquals(expectedProjectLanguageLevel, languageLevelProjectExtension.getLanguageLevel)
    assertEquals(expectedProjectLanguageLevelIsDefault, languageLevelProjectExtension.isDefault)

    if (SdkUtils.defaultJavaLanguageLevelIn(expectedProjectSdk).fold(false)(_ != expectedProjectLanguageLevel))
      assertFalse(languageLevelProjectExtension.getDefault)
  }
}
