package org.jetbrains.bsp

import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.project.ProjectStructureDsl.{contentRoots, excluded, libraries, libraryDependencies, module, modules, project, resources, sources, testResources, testSources}
import org.jetbrains.sbt.project.utils.ProjectStructureComparisonContext
import org.jetbrains.sbt.project.{ExactMatch, ProjectStructureMatcher, ProjectStructureTestUtils, RequiresJdk}
import org.junit.experimental.categories.Category

abstract class BspProjectStructureImportingTestBase
  extends SbtOverBspExternalSystemImportingTestCase
    with ProjectStructureMatcher
    with ExactMatch {

  override protected def getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/projects/${getTestName(true)}"

  override protected def reuseExistingConnectionFile = false

  override protected def copyTestProjectToTemporaryDir = true

  protected implicit lazy val defaultCompareContext: ProjectStructureComparisonContext =
    ProjectStructureComparisonContext.Implicit.default(using getMyProject)

  override def tearDown(): Unit = {
    inWriteAction {
      val table = ProjectJdkTable.getInstance
      table.getAllJdks.foreach(table.removeJdk)
    }
    super.tearDown()
  }
}

@Category(Array(classOf[SlowTests2]))
class BspProjectStructureImportingTest extends BspProjectStructureImportingTestBase {

  def testSimple(): Unit =
    runSimpleTest(
      projectName = "simple",
      scalaVersion = "2.13.14",
      excludeTarget = true,
      buildModuleSources = Nil,
    )

  private def runSimpleTest(
    projectName: String,
    scalaVersion: String,
    excludeTarget: Boolean,
    buildModuleSources: Seq[String]
  ): Unit = {
    importProject(false)

    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk(useEnv = true)(
      scalaVersion,
      BSP.ProjectSystemId,
      useScalaSdkExtraClasspath = true
    )

    val _moduleName = projectName.toLowerCase
    val expectedProject = new project(projectName) {
      libraries := scalaLibraries
      libraries.inexactMatch()

      modules := Seq(
        new module(_moduleName) {
          libraryDependencies := BspProjectStructureImportingTestUtils.expectedLibraryDependencies(scalaLibraries, _moduleName)
          sources := Seq("src/main/scala", "src/main/java")
          testSources := Seq("src/test/scala", "src/test/java")
          resources := Seq("src/main/resources")
          testResources := Seq("src/test/resources")
          excluded := Seq(".bloop", ".bsp") ++ { if excludeTarget then Seq("target") else Nil }
        },
        new module(s"$_moduleName-build") {
          sources := buildModuleSources
          testSources := Nil
          resources := Nil
          testResources := Nil
          excluded := Nil
        }
      )
    }

    assertProjectsEqual(expectedProject, getMyProject)
  }
}

@Category(Array(classOf[SlowTests2]))
class BspProjectStructureImportingTestWithLatestSbt1 extends BspProjectStructureImportingTestBase {

  override def sbtVersionToInject = Some(SbtVersion.Latest.Sbt_1)

  def testSimpleSbt1Latest(): Unit = {
    importProject(false)

    val expectedScala_3_3 = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk(useEnv = true)(
      "3.3.3",
      BSP.ProjectSystemId,
      useScalaSdkExtraClasspath = false
    )
    val expectedScala_3_6 = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk(useEnv = true)(
      "3.6.2",
      BSP.ProjectSystemId,
      useScalaSdkExtraClasspath = false
    )

    val expectedProject = new project("simpleSbt1Latest") {
      libraries := expectedScala_3_3 ++ expectedScala_3_6
      libraries.inexactMatch()

      modules := Seq(
        new module("root") {
          contentRoots := Seq(getProjectPath)
          libraryDependencies := BspProjectStructureImportingTestUtils.expectedLibraryDependencies(expectedScala_3_3, "root")
          sources := Seq("src/main/scala", "src/main/java")
          testSources := Seq("src/test/scala", "src/test/java")
          resources := Seq("src/main/resources")
          testResources := Seq("src/test/resources")
          excluded := Seq("target", ".bloop", ".bsp")
        },
        new module("subProject1") {
          contentRoots := Seq(s"$getProjectPath/subProject1")
          libraryDependencies := BspProjectStructureImportingTestUtils.expectedLibraryDependencies(expectedScala_3_3, "subProject1")
          sources := Seq("src/main/scala", "src/main/java")
          testSources := Seq("src/test/scala", "src/test/java")
          resources := Seq("src/main/resources")
          testResources := Seq("src/test/resources")
          excluded := Seq(s"%PROJECT_ROOT%/subProject1/target")
        },
        new module("subProject2") {
          contentRoots := Seq(s"$getProjectPath/subProject2")
          libraryDependencies := BspProjectStructureImportingTestUtils.expectedLibraryDependencies(expectedScala_3_6, "subProject2")
          sources := Seq("src/main/scala", "src/main/java")
          testSources := Seq("src/test/scala", "src/test/java")
          resources := Seq("src/main/resources")
          testResources := Seq("src/test/resources")
          excluded := Seq(s"%PROJECT_ROOT%/subProject2/target")
        },
        new module(s"root-build") {
          sources := Nil
          testSources := Nil
          resources := Nil
          testResources := Nil
          excluded := Nil
        }
      )
    }

    assertProjectsEqual(expectedProject, getMyProject)
  }
}

@Category(Array(classOf[SlowTests2]))
@RequiresJdk(LanguageLevel.JDK_17)
class BspProjectStructureImportingTestWithNewestSbt2 extends BspProjectStructureImportingTestBase {

  override def sbtVersionToInject = Some(SbtVersion.Latest.Sbt_2)

  def testSimpleSbt2Latest(): Unit = {
    importProject(false)

    def moduleContentRoots(moduleName: String, scalaVersion: String): Seq[String] =
      Seq(
        s"$getProjectPath/$moduleName",
        s"$getProjectPath/target/out/jvm/scala-$scalaVersion/${moduleName.toLowerCase}/src_managed/main",
        s"$getProjectPath/target/out/jvm/scala-$scalaVersion/${moduleName.toLowerCase}/resource_managed/main",
        s"$getProjectPath/target/out/jvm/scala-$scalaVersion/${moduleName.toLowerCase}/src_managed/test",
        s"$getProjectPath/target/out/jvm/scala-$scalaVersion/${moduleName.toLowerCase}/resource_managed/test"
      )

    val expectedScala_3_3 = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk(useEnv = true)(
      "3.3.3",
      BSP.ProjectSystemId,
      useScalaSdkExtraClasspath = false
    )
    val expectedScala_3_6 = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk(useEnv = true)(
      "3.6.2",
      BSP.ProjectSystemId,
      useScalaSdkExtraClasspath = false
    )

    val expectedScalaLibraries = expectedScala_3_3 ++ expectedScala_3_6

    val expectedProject = new project("simpleSbt2Latest") {
      libraries := expectedScalaLibraries
      libraries.inexactMatch()

      modules := Seq(
        new module("root") {
          contentRoots := Seq(getProjectPath)
          libraryDependencies := BspProjectStructureImportingTestUtils.expectedLibraryDependencies(expectedScala_3_3, "root")
          sources := Seq("src/main/scala", "src/main/java")
          testSources := Seq("src/test/scala", "src/test/java")
          resources := Seq("src/main/resources")
          testResources := Seq("src/test/resources")
          // Uncomment this when the problem with output paths is fixed in sbt https://github.com/sbt/sbt/issues/9268
          //excluded := Seq(".bloop", ".bsp")
        },
        new module("subProject1") {
          contentRoots := moduleContentRoots("subProject1", "3.3.3")
          libraryDependencies := BspProjectStructureImportingTestUtils.expectedLibraryDependencies(expectedScala_3_3, "subProject1")
          sources := Seq("src/main/scala", "src/main/java")
          testSources := Seq("src/test/scala", "src/test/java")
          resources := Seq("src/main/resources")
          testResources := Seq("src/test/resources")
          excluded := Nil
        },
        new module("subProject2") {
          contentRoots := moduleContentRoots("subProject2", "3.6.2")
          libraryDependencies := BspProjectStructureImportingTestUtils.expectedLibraryDependencies(expectedScala_3_6, "subProject2")
          sources := Seq("src/main/scala", "src/main/java")
          testSources := Seq("src/test/scala", "src/test/java")
          resources := Seq("src/main/resources")
          testResources := Seq("src/test/resources")
          excluded := Nil
        },
        new module(s"root-build") {
          sources := Nil
          testSources := Nil
          resources := Nil
          testResources := Nil
          excluded := Nil
        }
      )
    }

    assertProjectsEqual(expectedProject, getMyProject, singleContentRootModules = false)
  }
}
