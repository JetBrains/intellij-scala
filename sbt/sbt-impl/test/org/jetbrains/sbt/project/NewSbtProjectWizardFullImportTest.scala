package org.jetbrains.sbt.project

import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.plugins.scala.{ScalaVersion, SlowTests}
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.project.ProjectStructureDsl.*
import org.junit.experimental.categories.Category

// TODO:
//  - test .gitignore creation
//  - check added sample code
//  - test with IntelliJ build system as well
/**
 * Verifies SBT new-project wizard behavior end-to-end with external project import enabled.
 *
 * For file-generation-only coverage without import, see [[NewSbtProjectWizardGeneratedFilesTest]].
 */
@Category(Array(classOf[SlowTests]))
class NewSbtProjectWizardFullImportTest extends NewSbtProjectWizardTestBase {
  import NewSbtProjectWizardTestBase.SbtWizardProjectConfig

  def testCreateProjectWithLowerCaseName(): Unit =
    runSimpleCreateSbtProjectTest(
      projectName = "lower_case_project_name",
      scalaVersion = ScalaVersion(ScalaLanguageLevel.Scala_2_13, "14")
    )

  def testCreateProjectWithUpperCaseName(): Unit =
    runSimpleCreateSbtProjectTest(
      projectName = "UpperCaseProjectName",
      scalaVersion = ScalaVersion(ScalaLanguageLevel.Scala_2_13, "14"),
      packagePrefixOpt = Some("org.example.prefix")
    )

  //SCL-12528, SCL-12528
  def testCreateProjectWithDotsSpacesAndDashesInNameName(): Unit =
    runSimpleCreateSbtProjectTest(
      projectName = "project_name_with_dots spaces and-dashes and UPPERCASE",
      scalaVersion = ScalaVersion(ScalaLanguageLevel.Scala_2_13, "14")
    )

  def testCreateScala3ProjectAndUseIndentationBasedSyntax(): Unit =
    runSimpleCreateSbtProjectTest(
      projectName = "scala3-indentation-based-syntax",
      scalaVersion = ScalaVersion(ScalaLanguageLevel.Scala_3_3, "3"),
      useIndentationBasedSyntax = true
    )

  private def runSimpleCreateSbtProjectTest(
    projectName: String,
    scalaVersion: ScalaVersion,
    packagePrefixOpt: Option[String] = None,
    useIndentationBasedSyntax: Boolean = false,
  ): Unit = {
    val sbtVersion = SbtVersion.Latest.Sbt_1
    val config = SbtWizardProjectConfig(
      projectName = projectName,
      scalaVersion = scalaVersion,
      sbtVersion = sbtVersion,
      packagePrefix = packagePrefixOpt,
      useIndentationBasedSyntax = useIndentationBasedSyntax,
    )
    runProjectStructureOnlyTest(config)
  }

  import NewSbtProjectWizardGeneratedFilesTest.TestData.*

  def testCreateSbt_0_13_Project(): Unit =
    runProjectStructureOnlyTest(sbt013ProjectConfig)

  def testCreateSbt_1_0_Project(): Unit =
    runProjectStructureOnlyTest(sbt10ProjectConfig)

  def testCreateSbt_1_Latest_Project(): Unit =
    runProjectStructureOnlyTest(sbt1LatestProjectConfig)

  def testCreateSbt_1_Latest_Project_WithPackagePrefix(): Unit =
    runProjectStructureOnlyTest(sbt1LatestProjectWithPackagePrefixConfig)

  def testCreateSbt_2_Latest_Project(): Unit =
    runProjectStructureOnlyTest(sbt2LatestProjectConfig)

  def testCreateSbt_2_Latest_Project_WithPackagePrefix(): Unit =
    runProjectStructureOnlyTest(sbt2LatestProjectWithPackagePrefixConfig)

  private def runProjectStructureOnlyTest(config: SbtWizardProjectConfig): Unit = {
    val useCoursier = config.sbtVersion >= SbtVersion("1.3.0")
    // Since sbt 1.12, the compiler classpath has been reduced to include only what is necessary for the `scala3-compiler` to be runnable -
    // without the Scaladoc extra classpath. Therefore, for tests running with sbt 1.12+ & Scala 3, no extra classpath should be included in the Scala SDK.
    // See org.jetbrains.sbt.project.ProjectStructureTestUtils.expectedScalaSdkLibraryFromCoursier
    val useScalaSdkExtraClasspath = config.sbtVersion < SbtVersion("1.12") || !config.scalaVersion.languageLevel.isScala3
    val expectedProjectStructure = createExpectedProjectStructure(
      config.projectName,
      config.scalaVersion.minor,
      config.packagePrefix,
      useCoursier = useCoursier,
      useScalaSdkExtraClasspath
    )
    runImportEnabledTest(config) { project =>
      new ProjectStructureAssertionsFixture(project).assertProjectsEqual(expectedProjectStructure, singleContentRootModules = false)
    }
  }

  private def createExpectedProjectStructure(
    projectName: String,
    scalaVersion: String,
    packagePrefixOpt: Option[String],
    useCoursier: Boolean,
    useScalaSdkExtraClasspath: Boolean
  ): project = {
    //noinspection TypeAnnotation
    val expectedIntellijProjectStructure: project = new project(projectName) {
      lazy val scalaLibraries =
        if (useCoursier)
          ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = false)(scalaVersion, useScalaSdkExtraClasspath)
        else
          ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkFromIvy(useEnv = false)(scalaVersion)

      libraries := scalaLibraries
      libraries.exactMatch()

      lazy val mainModule = new module(s"$projectName.main") {
        libraryDependencies := scalaLibraries
        ProjectStructureDsl.sources := Seq("scala")
      }

      lazy val testModule = new module(s"$projectName.test") {
        libraryDependencies := scalaLibraries
        testSources := Seq("scala")
        moduleDependencies += new dependency(mainModule) { isExported := false }
      }

      modules := Seq(
        new module(projectName) {
          excluded := Seq("target")
          moduleDependencies := Seq(
            new dependency(mainModule) { isExported := false },
            new dependency(testModule) { isExported := false }
          )
        },
        mainModule, testModule,
        new module(s"$projectName.$projectName-build") {
          // TODO: why `-build` module contains empty string? in UI the `project` folder is marked as `sources`.
          //  Is it some implicit IntelliJ behaviour?
          ProjectStructureDsl.sources := Seq("")
          excluded := Seq("project/target", "target")
        }
      )

      packagePrefixOpt.foreach { prefix =>
        packagePrefix := prefix
      }
    }
    expectedIntellijProjectStructure
  }
}
