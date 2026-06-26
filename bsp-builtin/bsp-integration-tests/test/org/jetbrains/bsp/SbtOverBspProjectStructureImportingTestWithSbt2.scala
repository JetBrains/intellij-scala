package org.jetbrains.bsp

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.project.ProjectStructureDsl.{contentRoots, excluded, libraries, libraryDependencies, module, modules, project, resources, sources, testResources, testSources}
import org.jetbrains.sbt.project.RequiresJdk
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests2]))
@RequiresJdk(LanguageLevel.JDK_17)
class SbtOverBspProjectStructureImportingTestWithSbt2 extends SbtOverBspProjectStructureImportingTestBase {

  override def sbtVersionToInject = Some(SbtVersion.Latest.Sbt_2)

  override protected def jdkForBspConnectionFile =
    Some(getJdkConfiguredForTestCase)

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

    val expectedScala_3_3 = BspProjectStructureImportingTestUtils.expectedScalaLibraryWithScalaSdk("3.3.3", useScalaSdkExtraClasspath = false)
    val expectedScala_3_6 = BspProjectStructureImportingTestUtils.expectedScalaLibraryWithScalaSdk("3.6.2", useScalaSdkExtraClasspath = false)

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
          // The excluded folders should also include the output directories for subProject1 & subProject2 (SCL-25499)
          //excluded := Seq("target/out/jvm/scala-3.3.3/root", ".bloop", ".bsp")
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