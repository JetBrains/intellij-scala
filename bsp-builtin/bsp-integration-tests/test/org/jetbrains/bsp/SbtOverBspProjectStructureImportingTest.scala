package org.jetbrains.bsp

import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.sbt.project.ProjectStructureDsl.{excluded, libraries, libraryDependencies, module, modules, project, resources, sources, testResources, testSources}
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests2]))
class SbtOverBspProjectStructureImportingTest extends SbtOverBspProjectStructureImportingTestBase {

  def testSimple(): Unit = {
    importProject(false)

    val scalaLibraries = BspProjectStructureImportingTestUtils.expectedScalaLibraryWithScalaSdk("2.13.14", useScalaSdkExtraClasspath = true)

    val expectedProject = new project("simple") {
      libraries := scalaLibraries
      libraries.inexactMatch()

      modules := Seq(
        new module("simple") {
          libraryDependencies := BspProjectStructureImportingTestUtils.expectedLibraryDependencies(scalaLibraries, "simple")
          sources := Seq("src/main/scala", "src/main/java")
          testSources := Seq("src/test/scala", "src/test/java")
          resources := Seq("src/main/resources")
          testResources := Seq("src/test/resources")
          excluded := Seq("target", ".bloop", ".bsp")
        },
        new module("simple-build") {
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